package org.clulab.exec

import com.typesafe.scalalogging.LazyLogging
import org.clulab.Relation
import org.clulab.odin.TextBoundMention

import java.io.{BufferedInputStream, BufferedOutputStream, EOFException, FileInputStream, FileOutputStream, FileWriter, ObjectInputStream, PrintWriter}
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{Success, Try}
//import org.clulab.Relation
import org.clulab.utils.{Serializer, computeAllIntersections, processText}

//case class Relation(predicate:String, agent:String, obj:String, text:String)

/**
 * Out of the relations extracted with Odin, build a (directed) graph
 */
object BuildGroundTruthGraph extends App with LazyLogging {

  // First, load the relations from the serialized file
  logger.info("Loading the relations")
  val ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream("extractions_serial.ser")))

  private def readRelations(ois:ObjectInputStream) = {
    var i = 0

    val x = Try(ois.readUnshared().asInstanceOf[Seq[Relation]])

    x match {
      case Success(rels) => Some(rels)
      case _ => None
    }
  }

  val relations = mutable.ListBuffer[Seq[Relation]]()

  var rels:Option[Seq[Relation]] = None
  var i = 0
  do {
    rels = readRelations(ois)
    i += 1
    rels match {
      case Some(r) =>
        relations += r
      case None => ()
    }
  }while(rels.isDefined)


  ois.close()
  logger.info("Finished loading the relations")


  // Now, process the relations to get the bag of lemmas for each participant, which will be used to generate the nodes

  // Compute all the intersections, which will become nodes on the graph
  logger.info("Computing all intersections")

  //buildIntersectionEdges(bagsOfLemmas, relations.flatten)
  buildIntersectionEdges(relations.flatten)



  def buildIntersectionEdges(rels:Iterable[Relation]) = {
    // To optimize, we are going to test intersection against the words instead of against the lemmas
    val entityIndex = new mutable.HashMap[String, Int]()
    val edges = new mutable.HashSet[(String, String, String, String)]()
    var runningIndex = 0
    // Extract all the entities from the relations
    for(r <- rels){
      val (agent, obj)  = (r.agent, r.obj)

      val agentTxt = processText(agent.lemmas, agent.tags, checkTags = false).mkString(" ")
      val objTxt = processText(obj.lemmas, obj.tags, checkTags = false).mkString(" ")

      if(!entityIndex.contains(agentTxt)){
        entityIndex += agentTxt -> runningIndex
        runningIndex += 1
      }

      if(!entityIndex.contains(objTxt)){
        entityIndex += objTxt -> runningIndex
        runningIndex += 1
      }

      edges += Tuple4(agentTxt, objTxt, r.predicate.words.mkString(" "), r.text)
    }



    // Streams to save the output
    val entityOutputStream = new PrintWriter("entity_codes.tsv")
    val extractedRelationsOutputStream = new PrintWriter("extractedEdges.tsv")

    // Populate the data structures
    for{(entity, ix) <- entityIndex}
    {
      entityOutputStream.println(s"$entity\t$ix")
    }

    // Save the extracted relations
    for((agent, obj, pred, sent) <- edges){
      extractedRelationsOutputStream.println(s"${entityIndex(agent)}\t${entityIndex(obj)}\t$pred\t$sent")
    }

    entityOutputStream.close()
    extractedRelationsOutputStream.close()
  }

  logger.info("Finished computing all intersections")
}
