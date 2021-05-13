package org.clulab.utils

import org.clulab.odin.{EventMention, Mention, TextBoundMention}

import scala.annotation.tailrec

object EntityProcessing {
  @tailrec
  def postProcessExtraction(mention:Mention):Set[String] = {
    mention match {
      case m:TextBoundMention =>
        val lemmas = m.words.map (_.toLowerCase)  map StringUtils.porterStem filterNot (w => stopWords.contains(w))
        val baseForm = lemmas.mkString (" ")

        (Set(baseForm) ++ lemmas.toSet).filter(_ != "")
      case e:EventMention =>
        postProcessExtraction(e.trigger)
    }
  }

  def postProcessSingleWord(word:String):Set[String] = {
    Set(StringUtils.porterStem(word.toLowerCase().replace(".", ""))).filter(_ != "")
  }

  def nodesFromPhrase(phrase:String, extractions:Map[String, Seq[Mention]]):Set[String] = {
    if(phrase.split(" ").length == 1)
      postProcessSingleWord(phrase)
    else
      extractions(phrase).flatMap(postProcessExtraction).toSet
  }
}
