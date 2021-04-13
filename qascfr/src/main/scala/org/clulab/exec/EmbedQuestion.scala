package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.utils.processText

/** Reads an extractions file and gives endpoints for each of the questions in the dataset */
object EmbedQuestion extends App with LazyLogging{
  //  First read the words2Entities file
  logger.info("Reading words2Entities")
  val src = io.Source.fromFile("/home/enrique/github/QASCFR/qascfr/data/words2Entities.tsv")
  val words2Entities:Map[String, Set[Int]] =
    src.getLines().toList.par.map{
      line =>
        val tokens = line.split("\t")
        val key = tokens.head
        val values = tokens.tail.map(_.toInt).toSet
        key -> values
    }.seq.toMap
  src.close()
  logger.info("Finished reading words2Entities")
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
  val questionsSrc = io.Source.fromFile("extractions_train.tsv").getLines().toList
  logger.info(s"${questionsSrc.length} questions")
  val questionEndpoints:Map[String, (Set[Int], Set[Int])] =
    questionsSrc.par.map{
      line =>
        val tokens = line.split("\t").reverse
        val key = tokens.head
        val values = //tokens.tail.map {
//          token =>
            processText(key.toLowerCase.split(" "), key.toLowerCase.split(" "), checkTags = false).toSet
//        }
//
        val intersectedQuestion =
          for{
//            bag <- values
            (word, entities) <- words2Entities
            if values contains word
          } yield entities

        val answer =
          answers.get(key.dropRight(1).trim) match {
            case Some(ans) => ans.toLowerCase.split(" ")
            case None => Array.empty[String]
          }

        val answerTokens =
          processText(answer, answer, checkTags = false).toSet

        val intersectAnswer =
          for{
            (word, entities) <- words2Entities
            if answerTokens contains word
          } yield  entities

        key -> (intersectedQuestion.flatten.toSet, intersectAnswer.flatten.toSet)
    }.seq.toMap

  logger.info("Finished reading questions")

  val matched = questionEndpoints.filter {
    case (_, (q, a)) =>
      q.nonEmpty && a.nonEmpty
  }

  matched foreach {
    case (question, (starts, ends)) =>
      println(s"$question\t${starts.mkString(",")}\t${ends.mkString(",")}")
  }
}
