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

  // Annotate the facts as document objects
  val totalFacts = facts.length
  val percent = totalFacts / 100
  val updatePercentage = 10
  println("Annotating documents")
  val docs =
    for(fact <- facts.par) yield {
//      val done = ix + 1
//      if((done)%(percent*updatePercentage) == 0){
//        println(s"$done (${(done.toFloat/totalFacts)*100}%) ...")
//      }
      processor.annotate(fact)
    }
  println("Saving results")
  Serializer.save(docs.seq.toArray, "QASC_annotations.ser") // TODO: Parameterize output name


}