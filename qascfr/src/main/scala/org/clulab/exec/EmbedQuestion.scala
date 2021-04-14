package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.utils.processText

import java.io.{FileOutputStream, PrintWriter}
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.StringField
import org.apache.lucene.document.TextField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.queryparser.classic.ParseException
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TopDocs
import org.apache.lucene.store.Directory
import org.apache.lucene.store.RAMDirectory


/** Reads an extractions file and gives endpoints for each of the questions in the dataset */
object EmbedQuestion extends App with LazyLogging{
  //  First read the words2Entities file
//  logger.info("Reading words2Entities")
//  val src = io.Source.fromFile("/home/enrique/github/QASCFR/qascfr/data/words2Entities.tsv")
//  val words2Entities:Map[String, Set[Int]] =
//    src.getLines().toList.par.map{
//      line =>
//        val tokens = line.split("\t")
//        val key = tokens.head
//        val values = tokens.tail.map(_.toInt).toSet
//        key -> values
//    }.seq.toMap
//  src.close()
//  logger.info("Finished reading words2Entities")



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
  val srcEntities = io.Source.fromFile("/home/enrique/github/QASCFR/qascfr/entity_codes.tsv")
  val entityIndex:Map[String, Int] = {
    srcEntities.getLines().toList.par.map{
      line =>
        val tokens = line.split("\t")
        val entity = tokens.head
        val ix = tokens.last.toInt
        addDoc(w, entity)
        entity -> ix
    }.seq.toMap
  }

  w.close()
  val reader = DirectoryReader.open(index)
  val searcher = new IndexSearcher(reader)
  val parser = new QueryParser("entity", analyzer)

  logger.info("Finished reading the entity index")
  val srcAnswers = io.Source.fromFile("train.tsv")
  val answers:Map[String, String] =
    srcAnswers.getLines().map{
      line =>
        val tokens = line.split("\t")
        val key = tokens(1).dropRight(1)
        val value = tokens(2)
        key -> value
    }.toMap
  srcAnswers.close()
  logger.info("Reading the answer keys")

  logger.info("Finished reading the answer keys")

  // Read the extractions file and find starting points
  logger.info("Reading questions")
  val questionsSrc = answers.keys.toList
  logger.info(s"${questionsSrc.length} questions")

  val entities = entityIndex.keys
//  val questionEndpoints:Map[String, (Set[String], Set[String])] =
//    questionsSrc.par.map{
//      line =>
//        val tokens = line.split("\t").reverse
//        val question = tokens.head
//        val wordsInQuestion =
//            processText(question.toLowerCase.split(" "), question.toLowerCase.split(" "), checkTags = false).toSet
//
//        val intersectedQuestion =
//          for{
//            entity <- entities
//            if wordsInQuestion exists (w => entity contains w)
//          } yield entity
//
//        val answer =
//          answers.get(question.dropRight(1).trim) match {
//            case Some(ans) => ans.toLowerCase.split(" ")
//            case None => Array.empty[String]
//          }
//
//        val answerTokens =
//          processText(answer, answer, checkTags = false).toSet
//
//        val intersectAnswer =
//          for{
//            entity <- entities
//            if answerTokens exists (w => entity contains w)
//          } yield  entity
//
//        question -> (intersectedQuestion.toSet, intersectAnswer.toSet)
//    }.seq.toMap


  val pw = new PrintWriter(new FileOutputStream("question_endpoints.tsv"))
  questionsSrc.zipWithIndex.foreach{
    case (line, ix) =>
      val tokens = line.split("\t").reverse
      val question = tokens.head
      val wordsInQuestion =
        processText(question.toLowerCase.split(" "), question.toLowerCase.split(" "), checkTags = false).mkString(" ")


      val answer =
        processText(answers(question).toLowerCase().split(" "), answers(question).toLowerCase().split(" "), checkTags = false).mkString(" ")


      val candidateEntryPoints = queryLucene(wordsInQuestion, 10, searcher)
      val candidateEndPoints = {
        if(answer.nonEmpty)
          queryLucene(answer, hitsPerPage = 10, searcher)
        else
          Array.empty[String]
      }
      pw.println(s"$question\t${answers(question)}\t${candidateEntryPoints.mkString("|")}\t${candidateEndPoints.mkString("|")}")
      if(ix % 100 == 0)
        logger.info(s"$ix out of ${questionsSrc.length} done")
  }
  logger.info("Finished reading questions")
  pw.close()

}
