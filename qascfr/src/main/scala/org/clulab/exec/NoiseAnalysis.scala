package org.clulab.exec

import org.clulab.json.{ParseQASCJsonFile, QASCEntry}
import org.clulab.utils.using
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.language.implicitConversions

case class NoiseEntry(question:String, answer:String, paths:List[List[String]])
case class IndividualStats(numHops: Int, numCorrectHops:Int, fact1Correct:Boolean, fact2Correct:Boolean) {
  val numIncorrectHops:Int = numHops - numCorrectHops
  val isPartiallyCorrect:Boolean = (fact1Correct && !fact2Correct) || (!fact1Correct && fact2Correct)
  val isCorrectPath:Boolean = fact1Correct && fact2Correct
}
case class EntryStats(stats:Seq[IndividualStats]) {
  val containsCorrectPath:Boolean = stats exists (_.isCorrectPath)
  val numPartiallyCorrect:Int = stats count (_.isPartiallyCorrect)
  val numTotallyIncorrect:Int = stats.size - numPartiallyCorrect - (if(containsCorrectPath) 1 else 0)
  val numFact1Correct:Int = stats count (s => s.isPartiallyCorrect && s.fact1Correct)
  val numFact2Correct:Int = stats count (s => s.isPartiallyCorrect && s.fact2Correct)
  val lengths:Seq[Int] = stats map (_.numHops)
  val numHopsFrequency:Map[Int, Int] = lengths groupBy identity mapValues (_.size)

  assert((numFact1Correct + numFact2Correct) == numPartiallyCorrect)
  assert((numPartiallyCorrect + numTotallyIncorrect + (if(containsCorrectPath) 1 else 0)) == stats.size)
}

object NoiseEntry {
  implicit lazy val formats = DefaultFormats
  def fromJson(path:String) = using(io.Source.fromFile(path)){
    src =>
      val json = parse(src.mkString)
      val data = json.extract[Map[String, List[List[String]]]]
      for{(key, paths) <- data} yield {
        val tokens = key.split("\t")
        val (q, a) = (tokens.head, tokens.last)
        NoiseEntry(q, a, paths)
      }
  }
}

object NoiseAnalysis extends App {
  val datasetPath = "/Users/enrique/Downloads/QASC_Dataset/train.jsonl"
  // Read the noise
  val data = NoiseEntry.fromJson("qascfr_noise.json")
  // Load the GT paths
  val gt = ParseQASCJsonFile.readFrom(datasetPath)
  // Match the analyzed data with the
  def findGtEntry(noiseEntry: NoiseEntry, dataset:Iterable[QASCEntry]):QASCEntry = {
    val (q, a) = (noiseEntry.question, noiseEntry.answer)
    dataset.filter(e => e.question == q && e.answer.get == a).head
  }
  val relatedEntries = data map (x => x -> findGtEntry(x, gt)) toMap

  // Function to compute statistics
  def crunchExamples(noiseEntry: NoiseEntry, gt:QASCEntry):EntryStats = {

    val (fact1, fact2) = (gt.fact1.get, gt.fact2.get)
    val correctFacts = Set(fact1, fact2)

    def crunchExample(path:List[String]):IndividualStats = {
      val len = path.length
      val intermediateNodes = path.drop(1).dropRight(1).toSet
      val numCorrect = intermediateNodes.count(n => correctFacts.contains(n))
      val containsFact1 = intermediateNodes contains fact1
      val containsFact2 = intermediateNodes contains fact2
      IndividualStats(len - 1, numCorrect, containsFact1, containsFact2)
    }

    val stats = noiseEntry.paths map crunchExample

    EntryStats(stats)
  }

  val allStats =
    relatedEntries map { case (k, v) => crunchExamples(k, v) }
  var x = 0
}
