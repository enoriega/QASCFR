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

//  // First, load the relations from the serialized file
//  logger.info("Loading the relations")
//  val relations = Serializer.load[Seq[Seq[Relation]]]("extractions.ser")
//  logger.info("Finished loading the relations")

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
  do {
    rels = readRelations(ois)
    rels match {
      case Some(r) =>
        relations += r
      case None => ()
    }
  }while(rels.isDefined)


//  println(i)

//  val relations = ListBuffer[Seq[Relation]]()
//
//  try{
//    while(true){
//      val rs = ois.readObject().asInstanceOf[Seq[Relation]]
//      relations += rs
//    }
//  }
//  catch {
//    case _:EOFException => () // Means we are done reading the file
//  }

  ois.close()
  logger.info("Finished loading the relations")


  // Now, process the relations to get the bag of lemmas for each participant, which will be used to generate the nodes
  logger.info("Computing bags of lemmas")
  val bagsOfLemmas:Map[String, Set[String]] = relations.flatten.flatMap(r => Iterable(r.agent, r.obj)).map(m => m.words.mkString(" ") -> processText(m.lemmas).toSet).toMap
  logger.info("Finished computing bags of lemmas")
  logger.info(s"Number of nodes: ${bagsOfLemmas.size}")

  // Compute all the intersections, which will become nodes on the graph
  logger.info("Computing all intersections")

  buildEdges(bagsOfLemmas)

  private def buildEdges(bags:Map[String, Set[String]]) = {
    // To optimize, we are going to test intersection against the words instead of against the lemmas
    val vocabulary = new mutable.HashSet[String]()

    // Build an index from words to lemmas
    val wordsToLemmas = new mutable.HashMap[String, List[Set[String]]]().withDefaultValue(Nil)
    val wordsToEntities = new mutable.HashMap[String, List[String]]().withDefaultValue(Nil)
    val entityIndex = new mutable.HashMap[String, Int]()

    // Streams to save the output
    val entityOutputStream = new PrintWriter("entity_codes.tsv")
    val lemmasOutputStream = new PrintWriter("lemmas.tsv")
    val incidencesOutputStream = new PrintWriter("graph.tsv")

    // Populate the data structures
    for{((entity, bag), ix) <- bags.zipWithIndex}
    {
      entityOutputStream.println(s"$entity\t$ix")
      lemmasOutputStream.println(s"$ix\t${bag.mkString("\t")}")
      entityIndex += entity -> ix
      for(word <- bag) {
        vocabulary += word
        wordsToLemmas(word) = bag :: wordsToLemmas(word)
        wordsToEntities(word) = entity :: wordsToEntities(word)
      }
    }

    // Build and save an incidence list
    for{(entity, bag) <- bags}  {
      val connections =
        bag flatMap wordsToEntities withFilter (entity != _) map entityIndex

      incidencesOutputStream.println(s"${entityIndex(entity)}\t${connections.mkString("\t")}")
    }

    entityOutputStream.close()
    lemmasOutputStream.close()
    incidencesOutputStream.close()
  }

  logger.info("Finished computing all intersections")
}
