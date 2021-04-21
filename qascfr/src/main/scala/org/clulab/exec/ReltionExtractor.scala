package org.clulab.exec

import org.clulab.{MentionData, Relation}
import org.clulab.odin.{EventMention, ExtractorEngine, TextBoundMention}
import org.clulab.processors.Document
import org.clulab.utils.Serializer

import java.io.{File, FileOutputStream, ObjectOutputStream}
import scala.collection.mutable

object ReltionExtractor extends App {

  def getListOfFiles(dir: String): List[File] = {
    val d = new File(dir)
    if (d.exists && d.isDirectory) {
      d.listFiles.filter(_.isFile).toList
    } else {
      List[File]()
    }
  }

  // read rules from general-rules.yml file in resources
  val source = io.Source.fromURL(getClass.getResource("/grammars/master.yml"))
  val rules = source.mkString
  source.close()

  // creates an extractor engine using the rules and the default actions
  val extractor = ExtractorEngine(rules)

  val paths = getListOfFiles("/media/wdblue/github/QASCFR/qascfr/data/")

  val oos = new ObjectOutputStream(new FileOutputStream("extractions_serial.ser"))
  var numRelations = 0
  var completed = 0

  def serialize(relations:Iterable[Relation]): Unit = synchronized {
    oos.writeObject(relations)
    numRelations += relations.size
    completed += 1
    println(s"Extracted $completed from completed out of ${paths.size}...")
  }

  for ((path, ix) <- paths.zipWithIndex.par)  {
    val doc = Serializer.load[Document](path.getAbsolutePath)

//    // extract mentions from annotated document
//    val mentions = extractor.extractFrom(doc).sortBy(m => (m.sentence, m.getClass.getSimpleName))
//
//    val data =
//    mentions collect {
//      case m: EventMention =>
//        val agent = m.arguments("agent").head.asInstanceOf[TextBoundMention]
//        val obj = m.arguments("object").head.asInstanceOf[TextBoundMention]
//        Relation(m.trigger, agent, obj, m.sentenceObj.getSentenceText)
//    }

    // extract mentions from annotated document
    val mentionsBySent = extractor.extractFrom(doc).groupBy(m => m.sentence)

    val dummy = MentionData(Seq.empty[String], Seq.empty[String], Seq.empty[String])

    val data =
      mentionsBySent flatMap {
        case (_, mentions) =>
          val sentObj = mentions.head.sentenceObj
          val seen = new mutable.HashSet[(String, String)]()

          for {
            a <- mentions
            b <- mentions
            if a != b
            if !seen.contains((a.text, b.text)) && !seen((a.text, b.text))
          } yield {
            seen += Tuple2(a.text, b.text)
            Relation(dummy, a.asInstanceOf[TextBoundMention], b.asInstanceOf[TextBoundMention], sentObj.getSentenceText)
          }
      }

    serialize(data)
  }

  oos.close()
  print(numRelations)
//  println(allRelations.size)
//  Serializer.save(allRelations, "extractions.ser")
}
