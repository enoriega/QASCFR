package org.clulab.exec

import org.clulab.processors.Document
import org.clulab.processors.corenlp.CoreNLPProcessor
import org.clulab.utils.Serializer

class MyCoreNLPProcessor extends CoreNLPProcessor {
  override def annotate(doc: Document): Document = {
    tagPartsOfSpeech(doc)
    lemmatize(doc)
//    recognizeNamedEntities(doc)
    parse(doc)
    chunking(doc)
//    relationExtraction(doc)
//    srl(doc)
//    resolveCoreference(doc)
//    discourse(doc)
    doc.clear()
    doc
  }
}

object AnnotateQASCCorpus extends App {
  // Path  to the corpus TODO: Patameterize
  val inputPath = "QASC_Corpus.txt"

  // Read the lines lazily
  val src = io.Source.fromFile(inputPath)
  val facts = src.getLines().toList
  src.close()

  // Instantiate a processors instance to generate the doc object
//  org.clulab.dynet.Utils.initializeDyNet()
//  val processor = new CluProcessor()
  val processor = new MyCoreNLPProcessor()
  // Initialize the processor
  processor.annotate("Test")

  // Annotate the facts as document objects
  val totalFacts = facts.length
  val chunks = facts.sliding(10000, 10000).toList
  val percent = totalFacts / 100
  val updatePercentage = 10
  println("Annotating documents")
//  val docs =
    for((chunk, ix) <- chunks.zipWithIndex.par) {
      val done = (ix + 1) * 10000

      println(s"$done (${(done.toFloat/totalFacts)*100}%) ...")

      val doc = processor.annotateFromSentences(chunk)
      Serializer.save(doc, s"QASC_annotations_$ix.ser") // TODO: Parameterize output name
    }
//  println("Saving results")



}