import org.clulab.processors.clu.CluProcessor
import org.clulab.processors.corenlp.CoreNLPProcessor
import org.clulab.utils.Serializer

object AnnotateQASCCorpus extends App {
  // Path  to the corpus TODO: Patameterize
  val inputPath = "QASC_Corpus.txt"

  // Read the lines lazily
  val src = io.Source.fromFile(inputPath)
  val facts = src.getLines().toList
  src.close()

  // Instantiate a processors instance to generate the doc object
  org.clulab.dynet.Utils.initializeDyNet()
  val processor = new CluProcessor()
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