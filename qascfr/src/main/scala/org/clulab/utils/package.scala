package org.clulab

import org.clulab.odin._
import org.clulab.processors.{Document, Sentence}

import java.io.File
import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.matching.Regex

package object utils {

  val stopWords = "i\nme\nmy\nmyself\nwe\nour\nours\nourselves\nyou\nyour\nyours\nyourself\nyourselves\nhe\nhim\nhis\nhimself\nshe\nher\nhers\nherself\nit\nits\nitself\nthey\nthem\ntheir\ntheirs\nthemselves\nwhat\nwhich\nwho\nwhom\nthis\nthat\nthese\nthose\nam\nis\nare\nwas\nwere\nbe\nbeen\nbeing\nhave\nhas\nhad\nhaving\ndo\ndoes\ndid\ndoing\na\nan\nthe\nand\nbut\nif\nor\nbecause\nuse\nas\nuntil\nwhile\nof\nat\nby\nfor\nwith\nabout\nagainst\nbetween\ninto\nthrough\nduring\nbefore\nafter\nabove\nbelow\nto\nfrom\nup\ndown\nin\nout\non\noff\nover\nunder\nagain\nfurther\nthen\nonce\nhere\nthere\nwhen\nwhere\nwhy\nhow\nall\nany\nboth\neach\nfew\nmore\nmost\nother\nsome\nsuch\nno\nnor\nnot\nonly\nown\nsame\nso\nthan\ntoo\nvery\ns\nt\ncan\nwill\njust\ndon\nshould\nnow\n's\n?\n*\n?.\n??".split("\n").toSet.map(StringUtils.porterStem) //++ io.Source.fromFile("black_list.txt").getLines().toSet

  val numRegex = "^\\$?\\d+([\\.\\-]*\\d*)*".r

  def displayMentions(mentions: Seq[Mention], doc: Document): Unit = {
    val mentionsBySentence = mentions groupBy (_.sentence) mapValues (_.sortBy(_.start)) withDefaultValue Nil
    for ((s, i) <- doc.sentences.zipWithIndex) {
      println(s"sentence #$i")
      println(s.getSentenceText)
      println("Tokens: " + (s.words, s.tags.get, s.chunks.get).zipped.mkString(", "))
      printSyntacticDependencies(s)
      println

      val sortedMentions = mentionsBySentence(i).sortBy(_.label)
      val (events, entities) = sortedMentions.partition(_ matches "Event")
      val (tbs, rels) = entities.partition(_.isInstanceOf[TextBoundMention])
      val sortedEntities = tbs ++ rels.sortBy(_.label)
      println("entities:")
      sortedEntities foreach displayMention

      println
      println("events:")
      events foreach displayMention
      println("=" * 50)
    }
  }

  def printSyntacticDependencies(s:Sentence): Unit = {
    if(s.dependencies.isDefined) {
      println(s.dependencies.get.toString)
    }
  }

  def displayMention(mention: Mention) {
    val boundary = s"\t${"-" * 30}"
    println(s"${mention.labels} => ${mention.text}")
    println(boundary)
    println(s"\tRule => ${mention.foundBy}")
    val mentionType = mention.getClass.toString.split("""\.""").last
    println(s"\tType => $mentionType")
    println(boundary)
    mention match {
      case tb: TextBoundMention =>
        println(s"\t${tb.labels.mkString(", ")} => ${tb.text}")
      case em: EventMention =>
        println(s"\ttrigger => ${em.trigger.text}")
        displayArguments(em)
      case rel: RelationMention =>
        displayArguments(rel)
      case _ => ()
    }
    println(s"$boundary\n")
  }


  def displayArguments(b: Mention): Unit = {
    b.arguments foreach {
      case (argName, ms) =>
        ms foreach { v =>
          println(s"\t$argName ${v.labels.mkString("(", ", ", ")")} => ${v.text}")
        }
    }
  }

  def processText(lemmas:Seq[String], tags:Seq[String], checkTags:Boolean = true):Seq[String] = {
    (lemmas zip tags) map {
      case (l, t) => (l.toLowerCase, t.toLowerCase)
    } filterNot {
      case (l, _) => (stopWords contains l)
    } filter {
      case (l, t) => {
        if(l.length > 1){
          if(checkTags)
            t.startsWith("n") || t.startsWith("j") // Keep only nouns or adjectives
          else
            true
        }
        else
          false
      }

    } filterNot {
      case (l, _) => numRegex.pattern.matcher(l).matches
    } map (_._1.replace(":", "").replace("?", "").replace(",", "").replace("\"", ""))
  }

//  def processText(lemmas:String): Seq[String] = processText(lemmas.split(" "))

//  def processText(mention:TextBoundMention): Option[Seq[String]] = {
//    mention.lemmas match {
//      case Some(lemmas) => Some(processText(lemmas))
//      case None => None
//    }
//  }
//
//  def computeAllIntersections(frozenSets:Iterable[Set[String]]):List[Set[String]] = {
//    if(frozenSets.isEmpty)
//      List.empty[Set[String]]
//    else{
//      val head = frozenSets.head
//      val tail = frozenSets.tail
//      val tailIntersections = computeAllIntersections(tail)
//      var newIntersections = head::tailIntersections
//      newIntersections ++= (for(s <- tailIntersections) yield {head intersect s})
//      newIntersections.distinct
//    }
//
//  }

  def computeAllIntersections(frozenSets:Iterable[Set[String]]):List[Set[String]] = {
    val universalSet = frozenSets.reduce((a, b) => a union b)
    val intersections = mutable.Set(universalSet)
    for(s <- frozenSets) {
      val moreIntersections =
        for(t <- intersections) yield (t intersect s)
      intersections ++= (moreIntersections)
    }
    intersections.toList
  }

  type Closable = { def close(): Unit }

  def using[A <: Closable, B](resource: A)(f: A => B): B = {
    try {
      f(resource)
    } finally {
      resource.close()
    }
  }

  def cacheResult[B](path:String, overwrite:Boolean = false)(f: () => B): B ={
    val file = new File(path)
    val result =
      if(overwrite || !file.exists()) {
        val r = f()
        Serializer.save(r, file)
        r
      }
      else
        Serializer.load[B](file)

    result
  }

}