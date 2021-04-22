package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.{Document, Field, TextField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.RAMDirectory
import org.clulab.utils.{processText, stopWords}

import java.io.{FileOutputStream, PrintWriter}


/** Reads an extractions file and gives endpoints for each of the questions in the dataset */
object EmbedQuestion extends App with LazyLogging{

  type Closable = { def close(): Unit }

  def using[A <: Closable, B](resource: A)(f: A => B): B = {
    try {
      f(resource)
    } finally {
      resource.close()
    }
  }


  val analyzer = new StandardAnalyzer
  val index = new RAMDirectory

  val config = new IndexWriterConfig(analyzer)
  val w = new IndexWriter(index, config)


  def addDoc(w: IndexWriter, entity: String): Unit = {
    val doc = new Document()
    doc.add(new TextField("entity", entity, Field.Store.YES))
    w.addDocument(doc)
  }

  def queryLucene(entity:String, hitsPerPage:Int, searcher: IndexSearcher) = {
    val query = parser.parse(entity)
    val docs = searcher.search(query, hitsPerPage)
    val hits = docs.scoreDocs
    hits map {
      h =>
        searcher.doc(h.doc).get("entity")
    }
  }



  logger.info(s"Reading the entity index")

  val entityIndex:Map[String, Int] = {
    using(io.Source.fromFile("/home/enrique/github/QASCFR/qascfr/entity_codes.tsv")){
      src => src.getLines().toList.par.map{
        line =>
          val tokens = line.split("\t")
          val entity = tokens.head
          val ix = tokens.last.toInt
          addDoc(w, entity)
          entity -> ix
      }.seq.toMap
    }
  }

  w.close()

  val reader = DirectoryReader.open(index)
  val searcher = new IndexSearcher(reader)
  val parser = new QueryParser("entity", analyzer)

  logger.info("Finished reading the entity index")

  val dataLines =
    using(io.Source.fromFile("train.tsv")){
      src =>
        src.getLines().toList
    }

  val phrasesLines =
    using(io.Source.fromFile("extractions_train.tsv")){
      src =>
        src.getLines().toList
    }

  val (questions, answers) =
    dataLines.map{
      line =>
        val tokens = line.split("\t")
        val key = tokens(0)
        val question = tokens(1).dropRight(1)
        val answer = tokens(2)
        (key -> question, key -> answer)
    }.unzip match {
      case (q, a) => (q.toMap, a.toMap)
    }
  logger.info("Reading the answer keys")

  logger.info("Finished reading the answer keys")

  // Read the extractions file and find starting points
  logger.info("Reading questions")
  val questionTexts = questions.values.toList
  logger.info(s"${questionTexts.length} questions")

  logger.info("Reading phrases")
  val phrases =
    phrasesLines.map{
      line =>
        val tokens = line.split("\t")
        val key = tokens(0)
        val phrases = tokens.tail.dropRight(1)
        key -> phrases.map{
          phrase =>
            phrase.split("\\|").head.replace("_", "").replace("(", "").replace(")", "").trim.split(" ").filterNot(w => stopWords.contains(w)).mkString(" ")
        }.filter(_ != "")
    }.toMap

  val entities = entityIndex.keys

  using(new PrintWriter(new FileOutputStream("question_endpoints.tsv"))){
    pw =>
      var exactStarts = 0
      var exactEnds = 0
      var exactPairs = 0
      var eitherEnd = 0

      questions.zipWithIndex.foreach{
        case ((key, line), ix) =>
          val tokens = line.split("\t").reverse
          val question = tokens.head
          val wordsInQuestion =
            processText(question.toLowerCase.split(" "), question.toLowerCase.split(" "), checkTags = false).mkString(" ")

          val phrasesInQuestions = phrases.getOrElse(key, Array.empty[String])


          val answer =
            processText(answers(key).toLowerCase().split(" "), answers(key).toLowerCase().split(" "), checkTags = false).mkString(" ")

          var (exactStart, exactEnd) = (false, false)

          val entryPoints = {
            if(phrasesInQuestions.isEmpty)
              queryLucene(wordsInQuestion, 10, searcher)
            else{
              phrasesInQuestions flatMap {
                phrase =>
                  val candidateStartPoints = {
                    if(answer.nonEmpty)
                      queryLucene(phrase, hitsPerPage = 10, searcher)
                    else
                      Array.empty[String]
                  }

                  val extactMatch = candidateStartPoints filter (_ == phrase)

                  if(extactMatch.nonEmpty) {
                    exactStart = true
                    extactMatch
                  }
                  else
                    candidateStartPoints.take(1)
              }
            }

          }

          //          val entryPoints = candidateEntryPoints filter(_ ==

          val candidateEndPoints = {
            if(answer.nonEmpty)
              queryLucene(answer, hitsPerPage = 10, searcher)
            else
              Array.empty[String]
          }

          val extactAnswerMatch = candidateEndPoints filter (_ == answer)

          val endpoints =
            if(extactAnswerMatch.length > 0) {
              exactEnd = true
              Array(answer)
            }
            else
              candidateEndPoints

          val kind = {
            if(exactStart && exactEnd){
              exactStarts += 1
              exactEnds += 1
              exactPairs += 1
              eitherEnd += 1
              "exact pair"
            }
            else if(exactStart) {
              exactStarts += 1
              eitherEnd += 1
              "exact start"
            }
            else if(exactEnd) {
              exactEnds += 1
              eitherEnd += 1
              "exact end"
            }
            else
              "inexact"
          }
          pw.println(s"$key\t$question\t${answers(key)}\t${entryPoints.mkString("|")}\t${endpoints.mkString("|")}\t$kind")
          if(ix % 100 == 0)
            logger.info(s"$ix out of ${questionTexts.length} done")
      }
      logger.info("Finished reading questions")
      logger.info(s"Exact starts: $exactStarts\tExact ends: $exactEnds\tExact pairs: $exactPairs\tEither end: $eitherEnd")
  }

}
