package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.exec.NounPhraseExtractor.getClass
import org.clulab.json.ParseQASCJsonFile
import org.clulab.odin.ExtractorEngine
import org.clulab.utils.cacheResult

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
    cacheResult("training_extractions.ser") {
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

  // Now compute the coverage for each QASC instance
  val intersectionCoverage =
    trainingEntries map {
      e =>
//        val hops = Seq((e.question, e.fact1.get), (e.fact1.get, e.fact2.get), (e.fact2.get, e.choices(e.answerKey.get)))
        val hops = Seq((e.fact1.get, e.fact2.get))

        val intersections =
          hops map {
            case (s, d) =>
              val entitiesSource = extractions(s).map(_.text)
              val entitiesDest = extractions(d).map(_.text)

              (entitiesSource intersect entitiesDest).nonEmpty
          }

        intersections.forall(identity)
    }

  val covered = intersectionCoverage.count(identity)
  val notCovered = trainingEntries.size - covered

  logger.info(s"Covered: $covered\tNot covered: $notCovered")
}
