package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.utils.{cacheResult, using}

import java.io.{BufferedOutputStream, File, FileWriter, FilenameFilter}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps
import scala.util.Random
import collection.Map

object MakeNetworkXFiles extends App with LazyLogging {
  val edgesPath = "data/edges"

  def buildEdgeList(edgesDir:String) = {

    val files = new File(edgesDir).listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.toLowerCase.endsWith(".tsv") //name.toLowerCase.startsWith("train")
    })


    val (index:Map[String, List[String]],
    invertedIndex: Map[String, List[String]],
    questionFlagMap: Map[String, Boolean]) = mergeIndividualFiles(files)

    logger.info("Computing indices")
//    val index = individualMaps flatMap (_.toSeq) groupBy (_._1) mapValues { _.flatMap(_._2).toSet }
//    val invertedIndex = index.toSeq flatMap { case (phrase, terms) => terms map (t => t -> phrase)} groupBy (_._1) mapValues { _.map(_._2).toSet}
    logger.info("Generating edges")

    val total = index.size
    logger.info(s"$total")
    val stepSize = total / 100

    using(new FileWriter("nx_nodes.txt")){
      w =>
        val seen = mutable.HashSet[String]()
        questionFlagMap foreach {
          case (phrase, isQuestion) =>
            if(!seen.contains(phrase)) {
              seen += phrase
              w.write(s"$phrase\t$isQuestion\n")
            }
        }
    }

    using(new FileWriter("nx_edges.txt")){
      w =>
        val seen = mutable.HashSet[(String, String)]()
        //val edges =
          index.zipWithIndex foreach  {
            case ((phrase, terms), ix) =>
                logger.info(s"${ix}")
              terms.distinct foreach  {
                term =>
                // See the edges with connections
                val endPoints = invertedIndex(term)
                  // Sub sample the end points to keep things feasible
                  val sampledEndPoints = endPoints.take(1)
                sampledEndPoints foreach  {
                  endPoint =>
                    if(endPoint != phrase) {
                      val edge = (phrase, endPoint)
                      if (!seen.contains(edge)) {
                        val reversed = (endPoint, phrase)
                        seen += edge
                        seen += reversed
                        w.write(s"$phrase\t$endPoint\n")
                      }
                    }

              }
            }
          }

    }
    logger.info("Done")

  }

   def mergeIndividualFiles(files: Array[File]): (Map[String, List[String]], Map[String, List[String]], Map[String, Boolean]) = {
    val index = new mutable.OpenHashMap[String, List[String]](initialSize = files.length*10000).withDefaultValue(Nil)
    val invertedIndex = new mutable.OpenHashMap[String, List[String]](initialSize = files.length*100000).withDefaultValue(Nil)
    val questionFlagMap = new mutable.OpenHashMap[String, Boolean](initialSize = files.length*10000).withDefaultValue(false)

    val total = files.length
    (for ((file, ix) <- files.zipWithIndex) {
      logger.info(s"Reading ${ix+1} out of $total")
      using(io.Source.fromFile(file)) {
        src =>
          for (line <- src.getLines().toList) yield {
            val cols = line.split("\t")
            val (phrase, isQuestion, terms) = (cols.head, cols.tail.head.toBoolean, cols.tail.tail.toList)
            index(phrase) :::= terms
            for (term <- terms) {
              invertedIndex(term) ::= phrase
            }
            questionFlagMap(phrase) = isQuestion
          }
      }
    })
    (index, invertedIndex, questionFlagMap)
  }

  buildEdgeList(edgesPath)
}
