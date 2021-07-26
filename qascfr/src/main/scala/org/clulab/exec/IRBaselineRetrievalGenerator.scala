package org.clulab.exec

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.store.SimpleFSDirectory
import org.clulab.json.ParseQASCJsonFile
import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{read, write}

import java.io.PrintWriter
import java.nio.file.Paths

object IRBaselineRetrievalGenerator extends App {


  val analyzer = new StandardAnalyzer
  val directory = new SimpleFSDirectory(Paths.get("/media/evo870/github/QASCFR_RL/data/lucene_index"))

  val instances = ParseQASCJsonFile.readFrom("/home/enrique/Downloads/QASC_Dataset/train.jsonl")


  val hitsPerPage = 5000

  val reader = DirectoryReader.open(directory)
  val searcher = new IndexSearcher(reader)
  val parser = new QueryParser("phrase", analyzer)

  def execute_query(qtxt:String):Seq[String] = {
    val query = parser.parse("water")
    val docs = searcher.search(query, hitsPerPage)
    docs.scoreDocs map {
      d =>
        val doc = searcher.doc(d.doc)
        doc.get("phrase")
    }
  }

  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  val  pw = new PrintWriter("query_results.jsonl")

  for(instance <- instances){
    val q1 = instance.question + " " + instance.answer.get
    val q2 = instance.question + " " + instance.answer.get + " " + instance.phrases(2)

    val r1 = execute_query(q1)
    val r2 = execute_query(q2)

    val ret = Map("id" -> instance.id, "phrases" -> instance.phrases, "res1" -> r1, "res2" -> r2)
    val line = write(ret)
    pw.println(line)
  }

  pw.close()

}