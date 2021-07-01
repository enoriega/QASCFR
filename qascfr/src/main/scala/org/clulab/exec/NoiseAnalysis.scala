package org.clulab.exec

import org.clulab.json.{ParseQASCJsonFile, QASCEntry}
import org.clulab.utils.using
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.collection.immutable
import scala.language.implicitConversions
import scala.util.Random

case class NoiseEntry(question:String, answer:String, paths:List[List[String]])
case class IndividualStats(numHops: Int, numCorrectHops:Int, fact1Correct:Boolean, fact2Correct:Boolean) {
  val numIncorrectHops:Int = numHops - numCorrectHops
  val isPartiallyCorrect:Boolean = (fact1Correct && !fact2Correct) || (!fact1Correct && fact2Correct)
  val isCorrectPath:Boolean = fact1Correct && fact2Correct
}
case class EntryStats(stats:Seq[IndividualStats]) {
  val containsCorrectPath:Boolean = stats exists (_.isCorrectPath)
  def numPartiallyCorrect(normalized:Boolean = false):Double = (stats count (_.isPartiallyCorrect)) / (if(normalized) stats.size.toDouble else 1)
  def numTotallyIncorrect(normalized:Boolean = false):Double = (stats.size - numPartiallyCorrect() - (if(containsCorrectPath) 1 else 0)) / (if(normalized) stats.size.toDouble else 1)
  def numFact1Correct(normalized:Boolean = false):Double = (stats count (s => s.isPartiallyCorrect && s.fact1Correct))  / (if(normalized) stats.size.toDouble else 1)
  def numFact2Correct(normalized:Boolean = false):Double = (stats count (s => s.isPartiallyCorrect && s.fact2Correct))  / (if(normalized) stats.size.toDouble else 1)
  val lengths:Seq[Int] = stats map (_.numHops)
  val size:Int = stats.size
  def numHopsFrequency(normalized:Boolean = false):Map[Int, Double] = lengths groupBy identity mapValues (_.size  / (if(normalized) stats.size.toDouble else 1))

  assert((numFact1Correct() + numFact2Correct()) == numPartiallyCorrect())
  assert((numPartiallyCorrect() + numTotallyIncorrect() + (if(containsCorrectPath) 1 else 0)) == stats.size)
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

  // Compute all statistics
  val allStats =
    relatedEntries map { case (k, v) => crunchExamples(k, v) }

  // function to print the aggregated statistics of a quantity
  def printStats(stats: Iterable[EntryStats]):Unit = {
    def meanStd(nums:Iterable[Double], title:String):(Double, Double, Double) = {
      val mean = nums.sum / nums.size
      val std = Math.sqrt(nums.map(n => Math.pow(n - mean, 2)).sum / (nums.size - 1))
      val sem = std / Math.sqrt(nums.size)
      println(f"$title: Mean: $mean%.5f SE: $sem%.5f Std: $std%.5f")
      (mean, std, sem)
    }

    // Summary of the statistics
    println(s"Number of data points: ${stats.size}")
    meanStd(stats.map(_.size.toDouble), "Number of paths")
    meanStd(stats.map(_.numPartiallyCorrect(true)), "Percentage of paths partially correct")
    meanStd(stats.map(_.numHopsFrequency(true).getOrElse(2, 0.0)), "Paths of two hops (Incorrect)")

    // Sample alternate paths
    Random.shuffle(relatedEntries.toSeq).take(10) foreach {
      case (noiseEntry, _) =>
        val path = Random.shuffle(noiseEntry.paths).head
        println(path.mkString("  -  "))
    }
  }



  printStats(allStats)
}
