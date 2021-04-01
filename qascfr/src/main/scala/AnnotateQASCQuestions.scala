import org.clulab.processors.Document
import org.clulab.processors.clu.CluProcessor
import org.clulab.processors.corenlp.CoreNLPProcessor
import org.clulab.utils.Serializer

object AnnotateQASCQuestions extends App {
  // Path  to the corpus TODO: Patameterize
  val inputPath = "train.tsv"

  // Read the lines lazily
  val src = io.Source.fromFile(inputPath)
  val questions = src.getLines().map(l => {
    val toks = l.trim.split("\t")
    toks.head -> toks.last
  }).toMap
  src.close()

  // Instantiate a processors instance to generate the doc object
//  org.clulab.dynet.Utils.initializeDyNet()
//  val processor = new CluProcessor()
  val processor = new MyCoreNLPProcessor()
  // Initialize the processor
  processor.annotate("Test")

  // Annotate the facts as document objects
  val totalQuestions = questions.size
//  val chunks = questions.sliding(10000, 10000).toList
//  val percent = totalQuestions / 100
//  val updatePercentage = 10
  println("Annotating questions")
  val sentences = questions.values
//  val docs =
//    for((chunk, ix) <- chunks.zipWithIndex.par) {
//      val done = (ix + 1) * 10000
//
//      println(s"$done (${(done.toFloat/totalQuestions)*100}%) ...")

      val doc = processor.annotateFromSentences(sentences)
      Serializer.save(doc, s"QASC_questions_train.ser") // TODO: Parameterize output name
//    }
//  println("Saving results")



}