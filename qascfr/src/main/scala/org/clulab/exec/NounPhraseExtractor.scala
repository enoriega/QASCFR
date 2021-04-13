package org.clulab.exec


import org.clulab.odin.ExtractorEngine
import org.clulab.processors.Document
import org.clulab.utils.Serializer

import java.io.PrintWriter

object NounPhraseExtractor extends App {


  // read rules from general-rules.yml file in resources
  val source = io.Source.fromURL(getClass.getResource("/grammars/master.yml"))
  val rules = source.mkString
  source.close()

  // creates an extractor engine using the rules and the default actions
  val extractor = ExtractorEngine(rules)

  val path = "/media/wdblue/github/QASCFR/qascfr/QASC_questions_dev.ser"


  val doc = Serializer.load[Document](path)

  // extract mentions from annotated document
  val mentions = extractor.extractFrom(doc).filter {
    case m if m.matches("NounPhrase") => true
    case _ => false
  }.sortBy(m => m.sentence).groupBy(_.sentence)


  println(doc.sentences.size)
  println(mentions.values.flatten.size)
  val lines =
    mentions.map{
      case (_, ms)=>
        ms.map(m => m.lemmas.get.mkString(" ") + "|" + m.tags.get.mkString(" ")).mkString("\t")  + s"\t${ms.head.sentenceObj.getSentenceText}"
    }

  val pw = new PrintWriter("extractions_dev.tsv")
  lines foreach pw.println
  pw.close()

  Serializer.save(mentions, "extractions_dev.ser")
}
