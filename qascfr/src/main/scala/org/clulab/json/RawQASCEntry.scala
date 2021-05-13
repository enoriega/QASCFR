package org.clulab.json
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.language.implicitConversions

case class QASCChoice(text:String, label:String)
case class QASCQuestion(stem:String, choices:List[QASCChoice])
case class RawQASCEntry(id:String,
                        question:QASCQuestion,
                        answerKey:Option[String],
                        fact1:Option[String],
                        fact2:Option[String],
                        combinedfact:Option[String],
                        formatted_question:String)

case class QASCEntry(id:String,
                     question:String,
                     choices:Map[String, String],
                     answerKey:Option[String],
                     fact1:Option[String],
                     fact2:Option[String],
                     combinedFact:Option[String],
                     formattedQuestion:String) {

  val answer: Option[String] = answerKey match {
    case Some(k) => Some(choices(k))
    case None => None
  }

  val phrases:Seq[String] = {
    val a = answer match {
      case Some(s) => List(s)
      case None => Nil
    }

    val facts =
      List(fact1, fact2) collect { case Some(s) => s }

    question :: (a ++ facts)
  }
}

object QASCEntry{
  implicit lazy val formats = DefaultFormats

  implicit def fromRawEntry(r:RawQASCEntry):QASCEntry =
    QASCEntry(r.id,
      r.question.stem,
      r.question.choices.map{
        case QASCChoice(text, label) => label -> text
      }.toMap,
      r.answerKey,
      r.fact1,
      r.fact2,
      r.combinedfact,
      r.formatted_question
    )

  def fromJson(jsonLine:String):QASCEntry = {
    val json = parse(jsonLine)
    json.extract[RawQASCEntry]
  }
}