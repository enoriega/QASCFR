package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.exec.NounPhraseExtractor.getClass
import org.clulab.json.ParseQASCJsonFile
import org.clulab.odin.{EventMention, ExtractorEngine, Mention, TextBoundMention}
import org.clulab.utils.{StringUtils, cacheResult, stopWords}

import scala.annotation.tailrec

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
        val lemmas = m.words.map (_.toLowerCase) filterNot (w => stopWords.contains(w)) map StringUtils.porterStem
        val baseForm = lemmas.mkString (" ")

        Set(baseForm) ++ lemmas.toSet
      case e:EventMention =>
        postProcessExtraction(e.trigger)
    }
  }

  def hopIntersects(s:String, d:String):Boolean = {
    def aux(phrase:String) = {
      if(phrase.split(" ").length == 1)
        Set(StringUtils.porterStem(phrase.toLowerCase().replace(".", "")))
      else
        extractions(phrase).flatMap(postProcessExtraction).toSet
    }
    val entitiesSource:Set[String] = aux(s)

    // if
    val entitiesDest:Set[String] = aux(d)

    (entitiesSource intersect entitiesDest).nonEmpty
  }

  // Now compute the coverage for each QASC instance
  val intersectionCoverage =
    trainingEntries map {
      e =>
        val startHops = Seq((e.fact2.get, e.question), (e.fact1.get, e.question))
        val innerHop = (e.fact1.get, e.fact2.get)
        val endHops = Seq((e.fact2.get, e.choices(e.answerKey.get)), (e.fact1.get, e.choices(e.answerKey.get)))

        val innerIntersects = hopIntersects(innerHop._1, innerHop._2)
        val startIntersects = startHops map (h => hopIntersects(h._1, h._2)) exists identity
        val endIntersects = endHops map (h => hopIntersects(h._1, h._2)) exists identity

        innerIntersects && startIntersects && endIntersects
    }

  val covered = intersectionCoverage.count(identity)
  val notCovered = trainingEntries.size - covered

  logger.info(s"Covered: $covered\tNot covered: $notCovered")
}
