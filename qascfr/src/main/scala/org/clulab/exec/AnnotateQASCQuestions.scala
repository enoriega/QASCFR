package org.clulab.exec

import org.clulab.utils.Serializer

object AnnotateQASCQuestions extends App {
  // Path  to the corpus TODO: Patameterize
  val inputPath = "train.tsv"

  // Read the lines lazily
  val src = io.Source.fromFile(inputPath)
  val questions = src.getLines().map(l => {
    val toks = l.trim.split("\t")
    toks.head -> toks(1)
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
  val (ids, sentences) = questions.unzip
//  val docs =
//    for((chunk, ix) <- chunks.zipWithIndex.par) {
//      val done = (ix + 1) * 10000
//=-=
//      println(s"$done (${(done.toFloat/totalQuestions)*100}%) ...")

      val doc = processor.annotateFromSentences(sentences)
      Serializer.save((ids, doc), s"QASC_questions_train.ser") // TODO: Parameterize output name
//    }
//  println("Saving results")



}