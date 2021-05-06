package org.clulab.json

import org.clulab.utils.using

import scala.io.Source

object ParseQASCJsonFile {

  def readFrom(path:String):Iterable[QASCEntry] = {
    using(Source.fromFile(path)){
      src =>
        src.getLines().toList.map(QASCEntry.fromJson)
    }
  }

//  val entries =
//    readFrom("/home/enrique/Downloads/QASC_Dataset/train.jsonl")
//
//  entries foreach println
}
