package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.json.ParseQASCJsonFile
import org.clulab.odin.Mention
import org.clulab.processors.Document
import org.clulab.utils.EntityProcessing.nodesFromPhrase
import org.clulab.utils.{Serializer, buildExtractorEngine, cacheResult, using}

import java.io.{BufferedOutputStream, File, FileOutputStream, FileWriter}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, Future}
import scala.language.postfixOps
import com.redis._

/**
 * Reads a serialized
 */
object MakeNeo4JFiles extends App with LazyLogging{

  val corpusPath = "/home/enrique/Downloads/QASC_Corpus/QASC_Corpus.txt"
  val datasetPath = "/home/enrique/Downloads/QASC_Dataset/train.jsonl"
  val annotationsPath = "data/annotations"
  val extractionsPath = "data/extractions"
  val edgesPath = "data/edges"

  // Read the corpus and split in chunks, to then index the serialzied document files

  def writeToDisk(chunkNodes: Map[String, (Set[String], Boolean)], file: File): Unit = using(new FileWriter(file)){
    os =>
      for((phrase, (nodes, isQuestion)) <- chunkNodes)
        os.write(s"$phrase\t" + isQuestion + "\t" + nodes.mkString("\t") + "\n")
  }

  def makeDatasetFiles(path:String, corpusPath:Option[String]): Unit =  {

    val prefix = new File(path).getName
    val entries = ParseQASCJsonFile.readFrom(path)

    val uniquePhrases = entries.flatMap(_.phrases.zipWithIndex map { case(p, ix) => (p, ix == 0)}).toSeq

    val (processor, extractor) = buildExtractorEngine()

    // first annotate the sentences in the dataset

    val extractions = {
      cacheResult(new File(extractionsPath, s"${prefix}_extractions.ser").getPath, overwrite = true) {
        () =>
          (uniquePhrases.par map {
            case (sent, _) =>
              val doc = processor.annotate(sent)
              val mentions = extractor.extractFrom(doc)
              sent -> mentions
          }).toMap.seq
      }
    }

    val phrasesNodes =
      makeNodesMap(uniquePhrases, extractions)

    // now sample some sentences in the corpus, to introduce noise
    val corpusPhraseNodes:Map[String, (Set[String], Boolean)] =
    corpusPath match {
      case Some(p) =>
        using(io.Source.fromFile(p)) {
          src =>
            // We are going to add k noise sentences
            val k = 5e4 // 1 million sentences of noise
            val numChunks = k / 10e3
            // The sentences are annotated in chunks of 10k sentences. We will read them lazily
            val chunks = src.getLines().sliding(10000, 10000)

            val (_, extractor) = buildExtractorEngine()

            val futures =
              for ((chunk, ix) <- chunks.zipWithIndex.take(numChunks.toInt)) yield Future {
                logger.info(s"Processing chunk $ix")
                val doc = Serializer.load[Document](new File(annotationsPath, s"QASC_annotations_$ix.ser"))
                val mentionsBySentence = cacheResult(new File(extractionsPath, s"mentions_$ix.ser").getPath, false) {
                  () =>
                    extractor.extractFrom(doc).groupBy(_.sentence)
                }

                val chunkExtractions: Map[String, Seq[Mention]] = chunk.zipWithIndex map {
                  case (phrase, pIx) =>
                    val mentions = mentionsBySentence.get(pIx)
                    phrase -> (mentions match {
                      case Some(m) => m;
                      case None => Seq.empty
                    })
                } toMap

                val chunkNodes: Map[String, (Set[String], Boolean)] = makeNodesMap(chunk map ((_, false)), chunkExtractions)

                //writeToDisk(chunkNodes, new File(edgesPath, s"edges_$ix.tsv"))
                logger.info(s"Finished chunk $ix")
                chunkNodes
              }
            val results = Future.sequence(futures)
            Await.result(results, 10 days).flatten.toMap
        }
      case None => Map.empty
    }




    writeToDisk(phrasesNodes ++ corpusPhraseNodes, new File(edgesPath,  s"${prefix}_nodes.tsv"))
  }

  def populateRedis(datasetPath:String):Unit = {
    using(new RedisClient("localhost", 6379)) {
      redis =>

        def sendToRedis(phrase: String, ms: Seq[Mention]) = {
          ms.head.text
          val key = s"ph#$phrase"
          redis.sadd(key, ms.head.text.toLowerCase, ms.tail.map(_.text.toLowerCase): _*)
        }

        val prefix = new File(datasetPath).getName
        val entries = ParseQASCJsonFile.readFrom(datasetPath)

        val uniquePhrases = entries.flatMap(_.phrases.zipWithIndex map { case(p, ix) => (p, ix == 0)}).toSeq

        val (processor, extractor) = buildExtractorEngine()

        val extractions = {
          cacheResult(new File(extractionsPath, s"${prefix}_extractions.ser").getPath, overwrite = true) {
            () =>
              (uniquePhrases.par map {
                case (sent, _) =>
                  val doc = processor.annotate(sent)
                  val mentions = extractor.extractFrom(doc)
                  sent -> mentions
              }).toMap.seq
          }
        }

        logger.info("Processing dataset")
        extractions foreach {
          case (question, mentions) =>
            if(mentions.nonEmpty)
              sendToRedis(question, mentions)
        }

        using(io.Source.fromFile(corpusPath)) {
          src =>
            // The sentences are annotated in chunks of 10k sentences. We will read them lazily
            val chunks = src.getLines().sliding(10000, 10000)

            for ((chunk, ix) <- chunks.zipWithIndex) {
              logger.info(s"Processing chunk $ix")
              val doc = Serializer.load[Document](new File(annotationsPath, s"QASC_annotations_$ix.ser"))
              val mentionsBySentence = cacheResult(new File(extractionsPath, s"mentions_$ix.ser").getPath, false) {
                () =>
                  extractor.extractFrom(doc).groupBy(_.sentence)
              }

              chunk.zipWithIndex foreach  {
                case (phrase, pIx) =>
                  val mentions = mentionsBySentence.get(pIx)
                  mentions match {
                    case Some(ms) =>
                      sendToRedis(phrase, ms)
                    case None => ()
                  }
              }

              logger.info(s"Finished chunk $ix")

            }

        }
    }



  }
  def makeCorpusFiles(): Unit = {
    using(io.Source.fromFile(corpusPath)) {
      src =>
        // The sentences are annotated in chunks of 10k sentences. We will read them lazily
        val chunks = src.getLines().sliding(10000, 10000)

        val (_, extractor) = buildExtractorEngine()

        val futures =
          for ((chunk, ix) <- chunks.zipWithIndex) yield Future {
            logger.info(s"Processing chunk $ix")
            val doc = Serializer.load[Document](new File(annotationsPath, s"QASC_annotations_$ix.ser"))
            val mentionsBySentence = cacheResult(new File(extractionsPath, s"mentions_$ix.ser").getPath, false) {
              () =>
                extractor.extractFrom(doc).groupBy(_.sentence)
            }

            val chunkExtractions: Map[String, Seq[Mention]] = chunk.zipWithIndex map {
              case (phrase, pIx) =>
                val mentions = mentionsBySentence.get(pIx)
                phrase -> (mentions match {
                  case Some(m) => m;
                  case None => Seq.empty
                })
            } toMap

            val chunkNodes: Map[String, (Set[String], Boolean)] = makeNodesMap(chunk map ((_, false)), chunkExtractions)

            writeToDisk(chunkNodes, new File(edgesPath, s"edges_$ix.tsv"))
            logger.info(s"Finished chunk $ix")

          }
        val results = Future.sequence(futures)
        Await.result(results, 10 days)
        ()
    }
  }

  private def makeNodesMap(phrases: Seq[(String, Boolean)], extractions: Map[String, Seq[Mention]]): Map[String, (Set[String], Boolean)] = {
    (phrases map { case(p, isQuestion) => p -> (nodesFromPhrase(p, extractions), isQuestion)}).toMap
  }

//  makeDatasetFiles(datasetPath, Some(corpusPath))
//  makeCorpusFiles()
  populateRedis(datasetPath)
}
