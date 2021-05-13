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

  def writeToDisk(chunkNodes: Map[String, Set[String]], file: File): Unit = using(new FileWriter(file)){
    os =>
      for((phrase, nodes) <- chunkNodes)
        os.write(s"$phrase\t" + nodes.mkString("\t") + "\n")
  }

  def makeDatasetFiles(path:String): Unit =  {

    val prefix = new File(path).getName
    val entries = ParseQASCJsonFile.readFrom(path)

    val uniquePhrases = entries.flatMap(_.phrases).toSeq

    val (processor, extractor) = buildExtractorEngine()

    val extractions = {
      cacheResult(new File(extractionsPath, "${prefix}_extractions.ser").getPath, overwrite = false) {
        () =>
          (uniquePhrases.par map {
            sent =>
              val doc = processor.annotate(sent)
              val mentions = extractor.extractFrom(doc)
              sent -> mentions
          }).toMap.seq
      }
    }

    val phrasesNodes =
      makeNodesMap(uniquePhrases, extractions)

    writeToDisk(phrasesNodes, new File(edgesPath,  s"${prefix}_nodes.tsv"))
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

            val chunkNodes: Map[String, Set[String]] = makeNodesMap(chunk, chunkExtractions)

            writeToDisk(chunkNodes, new File(edgesPath, s"edges_$ix.tsv"))
            logger.info(s"Finished chunk $ix")

          }
        val results = Future.sequence(futures)
        Await.result(results, 10 days)
        ()
    }
  }

  private def makeNodesMap(phrases: Seq[String], extractions: Map[String, Seq[Mention]]): Map[String, Set[String]] = {
    (phrases map (p => p -> nodesFromPhrase(p, extractions))).toMap
  }

//  makeDatasetFiles(datasetPath)
  makeCorpusFiles()
}
