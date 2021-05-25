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

    val (index: Map[String, List[String]],
    invertedIndex: Map[String, List[String]],
    questionFlagMap: Map[String, Boolean]) =
      cacheResult("NetworkX_indices.ser", overwrite= false){
        () => buildIndices(edgesDir)
      }

    logger.info("Computing indices")

    val codes = new mutable.OpenHashMap[String, Int](initialSize = index.size + invertedIndex.size)
    for((t, ix) <- (index.keysIterator ++ invertedIndex.keysIterator).zipWithIndex){
      codes += t -> ix
    }

    logger.info("Generating edges")

    val total = index.size
    logger.info(s"$total")
    val stepSize = total / 100

    using(new FileWriter("nx_codes.txt")){
      w =>
        for((t, ix) <- codes){
          w.write(s"$ix\t$t\n")
        }
    }

    using(new FileWriter("nx_nodes.txt")){
      w =>
        val seen = mutable.HashSet[String]()
        questionFlagMap foreach {
          case (phrase, isQuestion) =>
            if(!seen.contains(phrase)) {
              seen += phrase
              w.write(s"${codes(phrase)}\t$isQuestion\n")
            }
        }
    }

    using(new FileWriter("nx_edges.txt")){
      w =>
//        val seen = mutable.HashSet[(String, String)]()
        //val edges =
          var ix = 0
          index foreach  {
            case (phrase, terms) =>
              logger.info(s"${ix}")
              ix += 1
              w.flush()
              terms.distinct foreach  {
                term =>
                  try {
                    // See the edges with connections
                    val endPoints = invertedIndex(term).take(10)
                    // Sub sample the end points to keep things feasible
                    val sampledEndPoints = endPoints
                    sampledEndPoints foreach {
                      endPoint =>
                        if (endPoint != phrase) {
//                          val edge = (phrase, endPoint)
//                          if (!seen.contains(edge)) {
//                            val reversed = (endPoint, phrase)
//                            seen += edge
//                            seen += reversed
                            w.write(s"${codes(phrase)}\t${
                              codes {
                                endPoint
                              }
                            }\n")
//                          }
                        }
                    }
                  }
                  catch {
                    case ex:Exception =>
                      logger.info(ex.getMessage)
                  }

              }
            }
          }



    logger.info("Done")

  }

  private def buildIndices(edgesDir: String): (Map[String, List[String]], Map[String, List[String]], Map[String, Boolean]) = {
    val files = new File(edgesDir).listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.toLowerCase.endsWith(".tsv") //name.toLowerCase.startsWith("train")
    })


    val (index: Map[String, List[String]],
    invertedIndex: Map[String, List[String]],
    questionFlagMap: Map[String, Boolean]) = mergeIndividualFiles(files)
    (index, invertedIndex, questionFlagMap)
  }

  def mergeIndividualFiles(files: Array[File]): (Map[String, List[String]], Map[String, List[String]], Map[String, Boolean]) = {
    val index = new mutable.HashMap[String, List[String]]().withDefaultValue(Nil)
    val invertedIndex = new mutable.HashMap[String, List[String]]().withDefaultValue(Nil)
    val questionFlagMap = new mutable.HashMap[String, Boolean]().withDefaultValue(false)

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
