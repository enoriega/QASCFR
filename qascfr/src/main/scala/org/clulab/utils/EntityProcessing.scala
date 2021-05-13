package org.clulab.utils

import org.clulab.odin.{EventMention, Mention, TextBoundMention}

import scala.annotation.tailrec

object EntityProcessing {
  @tailrec
  def postProcessExtraction(mention:Mention):Set[String] = {
    mention match {
      case m:TextBoundMention =>
        val stems = m.words map (w => StringUtils.porterStem(w).toLowerCase) filterNot (s => s.isEmpty || stopWords.contains(s))
        // Filter out any stem that corresponds to noun and verbs
        val terms = (stems zip m.tags.get) collect {
          case (stem, tag)
            if stem.nonEmpty && (tag.startsWith("N") || tag.startsWith("V")) =>
              stem
        }



        val filteredBaseForm = {
          if(stems.nonEmpty) {
            val baseForm = stems.mkString(" ")
            Set(baseForm) diff stopWords
          }
          else
            Set.empty
        }

        filteredBaseForm ++ terms.toSet
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
