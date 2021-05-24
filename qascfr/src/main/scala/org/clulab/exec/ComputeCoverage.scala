package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.json.{ParseQASCJsonFile, QASCEntry}
import org.clulab.odin.{EventMention, ExtractorEngine, Mention, TextBoundMention}
import org.clulab.utils.EntityProcessing.nodesFromPhrase
import org.clulab.utils.{StringUtils, buildExtractorEngine, cacheResult, stopWords}

import scala.annotation.tailrec
import scala.util.Random

case class PhraseIntersection(sentA:String, sentB:String, intersection:Set[String]){
  override def toString: String = {
    s"""From: \"$sentA\"
       |To:   \"$sentB\"
       |By:   \"${intersection.toSeq.sortBy(_.length).reverse}\"
       |""".stripMargin
  }
}
case class IntersectionInfo(start:PhraseIntersection, intermediate:PhraseIntersection, end:PhraseIntersection) {
  lazy val isComplete:Boolean =
    start.intersection.nonEmpty && intermediate.intersection.nonEmpty && end.intersection.nonEmpty

  override def toString: String = {
    "Start:\n" + start.toString + "\nIntermediate:\n" + intermediate.toString  + "\nEnd:\n" + end.toString + "\n"
  }
}

object ComputeCoverage extends App with LazyLogging{

  // First read all the QASCEntries
  val trainingEntries = ParseQASCJsonFile.readFrom("/home/enrique/Downloads/QASC_Dataset/train.jsonl")

  // Now extract the entities for each phrase
  val uniqueSentences = trainingEntries.flatMap{
    entry =>
      val q =
        entry.question
      val a =
        entry.choices(entry.answerKey.get)
      val f1 = entry.fact1.get
      val f2 = entry.fact2.get

      Seq(q, a, f1, f2)
  }.toSet

  logger.info(s"Annotating ${uniqueSentences.size} different question, answers and facts")

  val (processor, extractor) = buildExtractorEngine()



  val extractions = {
    cacheResult("training_extractions.ser", overwrite = false) {
      () =>
        (uniqueSentences.par map {
          sent =>
            val doc = processor.annotate(sent)
            val mentions = extractor.extractFrom(doc)
            sent -> mentions
        }).toMap.seq
    }
  }

  logger.info("Done extracting")
  val numExtractions = extractions.values.flatten.size
  logger.info(s"Extracted $numExtractions entities")



  def hopIntersects(s:String, d:String):Set[String] = {

    val entitiesSource:Set[String] = nodesFromPhrase(s, extractions)

    val entitiesDest:Set[String] = nodesFromPhrase(d, extractions)

    entitiesSource intersect entitiesDest
  }

  // Now compute the coverage for each QASC instance
  val intersectionCoverage =
    trainingEntries map {
      e =>
        e -> findIntersection(e)
    }

  def findIntersection(e: QASCEntry):IntersectionInfo = {
    val startHops = Seq((e.question, e.fact2.get), (e.question, e.fact1.get))
    val innerHop = (e.fact1.get, e.fact2.get)
    val endHops = Seq((e.fact2.get, e.choices(e.answerKey.get)), (e.fact1.get, e.choices(e.answerKey.get)))

    val innerIntersection = hopIntersects(innerHop._1, innerHop._2)
    val startIntersections =
      startHops map (h => PhraseIntersection(h._1, h._2, hopIntersects(h._1, h._2)))  sortBy (_.intersection.isEmpty)
    val endIntersections =
      endHops map (h => PhraseIntersection(h._1, h._2, hopIntersects(h._1, h._2))) sortBy (_.intersection.isEmpty)

    IntersectionInfo(
        startIntersections.head,
        PhraseIntersection(e.fact1.get, e.fact2.get, innerIntersection),
        endIntersections.head
      )
  }

  val (coveredInstances, unCoveredInstances) = intersectionCoverage.partition{ case (_, c) => c.isComplete }

  val covered = coveredInstances.size
  val notCovered = trainingEntries.size - covered

  logger.info(s"Covered: $covered\tNot covered: $notCovered")

  Random.shuffle(coveredInstances.take(100)) foreach {
    case (e, c) =>
//      println(e.id)
//      println(s"${e.question} -- ${e.choices(e.answerKey.get)}:")
//      println(c)
      println(s"""("${e.question}", "${e.answer.get}"),""")
  }
}
