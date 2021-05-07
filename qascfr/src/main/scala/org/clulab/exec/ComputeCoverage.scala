package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.json.{ParseQASCJsonFile, QASCEntry}
import org.clulab.odin.{EventMention, ExtractorEngine, Mention, TextBoundMention}
import org.clulab.utils.{StringUtils, cacheResult, stopWords}

import scala.annotation.tailrec

case class PhraseIntersection(sentA:String, sentB:String, intersection:Set[String]){
  override def toString: String = {
    s"""From: \"$sentA\"
       |To:   \"$sentB\"
       |By:   \"${intersection.toSeq.sortBy(_.length).last}\"
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

  // read rules from general-rules.yml file in resources
  val source = io.Source.fromURL(getClass.getResource("/grammars/master.yml"))
  val rules = source.mkString
  source.close()

  // creates an extractor engine using the rules and the default actions
  val extractor = ExtractorEngine(rules)
  val processor = new MyCoreNLPProcessor()

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

  @tailrec
  def postProcessExtraction(mention:Mention):Set[String] = {
    mention match {
      case m:TextBoundMention =>
        val lemmas = m.words.map (_.toLowerCase)  map StringUtils.porterStem filterNot (w => stopWords.contains(w))
        val baseForm = lemmas.mkString (" ")

        Set(baseForm) ++ lemmas.toSet
      case e:EventMention =>
        postProcessExtraction(e.trigger)
    }
  }

  def hopIntersects(s:String, d:String):Set[String] = {
    def aux(phrase:String) = {
      if(phrase.split(" ").length == 1)
        Set(StringUtils.porterStem(phrase.toLowerCase().replace(".", ""))).filter(_ != "")
      else
        extractions(phrase).flatMap(postProcessExtraction).toSet.filter(_ != "")
    }
    val entitiesSource:Set[String] = aux(s)

    val entitiesDest:Set[String] = aux(d)

    entitiesSource intersect entitiesDest
  }

  // Now compute the coverage for each QASC instance
  val intersectionCoverage =
    trainingEntries map {
      e =>
        e -> findIntersection(e)
    }

  def findIntersection(e: QASCEntry):Option[IntersectionInfo] = {
    val startHops = Seq((e.question, e.fact2.get), (e.question, e.fact1.get))
    val innerHop = (e.fact1.get, e.fact2.get)
    val endHops = Seq((e.fact2.get, e.choices(e.answerKey.get)), (e.fact1.get, e.choices(e.answerKey.get)))

    val innerIntersection = hopIntersects(innerHop._1, innerHop._2)
    val startIntersections =
      startHops map (h => PhraseIntersection(h._1, h._2, hopIntersects(h._1, h._2))) filter (_.intersection.nonEmpty)
    val endIntersections =
      endHops map (h => PhraseIntersection(h._1, h._2, hopIntersects(h._1, h._2))) filter (_.intersection.nonEmpty)

    if(startIntersections.nonEmpty && endIntersections.nonEmpty && innerIntersection.nonEmpty) {
      Some(IntersectionInfo(
        startIntersections.head,
        PhraseIntersection(e.fact1.get, e.fact2.get, innerIntersection),
        endIntersections.head
      ))
    }
    else
      None
  }

  val coveredInstances = intersectionCoverage.collect{ case (e, Some(c)) => (e, c) }
  val covered = coveredInstances.size
  val notCovered = trainingEntries.size - covered

  logger.info(s"Covered: $covered\tNot covered: $notCovered")

  coveredInstances foreach {
    case (e, c) =>
      println(e.id)
      println(s"${e.question} -- ${e.choices(e.answerKey.get)}:")
      println(c)
  }
}
