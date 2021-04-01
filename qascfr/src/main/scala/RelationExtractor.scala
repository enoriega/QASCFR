

import org.clulab.odin._
import org.clulab.processors.corenlp.CoreNLPProcessor
import org.clulab.utils.Serializer
import org.clulab.processors.Document
import utils._

import java.io.File

case class Relation(predicate:String, agent:String, obj:String, text:String)



object ReltionExtractor extends App {

  def getListOfFiles(dir: String):List[File] = {
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
  val allRelations =
    (for((path, ix) <- paths.zipWithIndex.par) yield {
      val doc = Serializer.load[Document](path.getAbsolutePath)

      println(s"Extracting from ${ix+1} out of ${paths.size}...")
      // extract mentions from annotated document
      val mentions = extractor.extractFrom(doc).sortBy(m => (m.sentence, m.getClass.getSimpleName))

      // display the mentions
      //  displayMentions(mentions, doc)
      val data =
      mentions collect {
        case m:EventMention =>
          val agent = m.arguments("agent").head.text
          val obj = m.arguments("object").head.text
          Relation(m.trigger.text, agent, obj, m.text)
      }

      data
    }).seq

  println(allRelations.size)
  Serializer.save(allRelations, "extractions.ser")
}