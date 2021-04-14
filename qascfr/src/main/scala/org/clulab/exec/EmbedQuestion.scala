package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.{Document, Field, TextField}
import org.apache.lucene.index.{DirectoryReader, IndexWriter, IndexWriterConfig}
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.RAMDirectory
import org.clulab.utils.processText

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

  val entities = entityIndex.keys

  using(new PrintWriter(new FileOutputStream("question_endpoints.tsv"))){
    pw =>
      questions.zipWithIndex.foreach{
        case ((key, line), ix) =>
          val tokens = line.split("\t").reverse
          val question = tokens.head
          val wordsInQuestion =
            processText(question.toLowerCase.split(" "), question.toLowerCase.split(" "), checkTags = false).mkString(" ")


          val answer =
            processText(answers(key).toLowerCase().split(" "), answers(key).toLowerCase().split(" "), checkTags = false).mkString(" ")


          val candidateEntryPoints = queryLucene(wordsInQuestion, 10, searcher)
          val candidateEndPoints = {
            if(answer.nonEmpty)
              queryLucene(answer, hitsPerPage = 10, searcher)
            else
              Array.empty[String]
          }

          val extactAnswerMatch = candidateEndPoints filter (_ == answer)

          val endpoints =
            if(extactAnswerMatch.length > 0)
              Array(answer)
            else
              candidateEndPoints

          pw.println(s"$key\t$question\t${answers(key)}\t${candidateEntryPoints.mkString("|")}\t${endpoints.mkString("|")}")
          if(ix % 100 == 0)
            logger.info(s"$ix out of ${questionTexts.length} done")
      }
      logger.info("Finished reading questions")
  }

}
