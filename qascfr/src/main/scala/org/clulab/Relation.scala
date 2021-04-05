package org.clulab

import org.clulab.odin.TextBoundMention

case class MentionData(words:Seq[String], lemmas:Seq[String], tags:Seq[String])

object MentionData {
  implicit def fromTextBoundMention(m:TextBoundMention): MentionData = MentionData(m.words, m.lemmas.get, m.tags.get)
}

case class Relation(predicate:MentionData, agent:MentionData, obj:MentionData, text:String)
