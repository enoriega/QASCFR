package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.utils.using

import java.io.{BufferedOutputStream, File, FileWriter, FilenameFilter}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.language.postfixOps

object MakeNetworkXFiles extends App with LazyLogging {
  val edgesPath = "data/edges"

  def buildEdgeList(edgesDir:String) = {
//    val index = new mutable.HashMap[String, mutable.HashSet[String]] {
//      override def default(key: String): mutable.HashSet[String] = new mutable.HashSet[String]}
//    val invertedIndex = new mutable.HashMap[String, ListBuffer[String]]{
//      override def default(key: String): ListBuffer[String] = new ListBuffer[String]()
//    }

    val files = new File(edgesDir).listFiles(new FilenameFilter {
      override def accept(dir: File, name: String): Boolean = name.toLowerCase.startsWith("train")
    })

    val individualMaps =
      for(file <- files) yield {
        logger.info(s"Reading ${file.getName}")
        using(io.Source.fromFile(file)){
          src =>
            for(line <- src.getLines().toList) yield {
              val cols = line.split("\t")
              val (phrase, terms) = (cols.head, cols.tail.toSet)
              phrase -> terms
            }
        } toMap
      }

    logger.info("Computing indices")
    val index = individualMaps flatMap (_.toSeq) groupBy (_._1) mapValues { _.flatMap(_._2).toSet }
    val invertedIndex = index.toSeq flatMap { case (phrase, terms) => terms map (t => t -> phrase)} groupBy (_._1) mapValues { _.map(_._2).toSet}
    logger.info("Generating edges")

    val total = index.size
    logger.info(s"$total")
    val stepSize = total / 100

    using(new FileWriter("nx_edges.txt")){
      w =>
        val seen = mutable.HashSet[(String, String)]()
        //val edges =
          index.zipWithIndex foreach  {
            case ((phrase, terms), ix) =>
                logger.info(s"${ix}")
              terms foreach  {
                term =>
                // See the edges with connections
                val endPoints = invertedIndex(term)
                endPoints foreach  {
                  endPoint =>
                    if(endPoint != phrase) {
                      val edge = (phrase, endPoint)
                      if (!seen.contains(edge)) {
                        val reversed = (endPoint, phrase)
                        seen += edge
                        seen += reversed
                        w.write(s"$phrase\t$endPoint\n")
                        //                      Some(edge)
                      }
                    }
//                    else
//                      None
              }
            }
          }
//      edges foreach {
//        case (a, b, t) =>
//          w.write(s"$a\t$b\t$t\n")
//      }
    }
    logger.info("Done")

  }

  buildEdgeList(edgesPath)
}
