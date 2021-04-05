package org.clulab.exec

import org.clulab.Relation

import java.io.{BufferedInputStream, EOFException, FileInputStream, ObjectInputStream}

object Deleteme extends App {

  val ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream("extractions_serial.ser")))
  var num = 0

  var iters = 0
  try{
    while(true){
      var o = ois.readObject().asInstanceOf[Seq[Relation]]
      num += o.size
//      o = ois.readObject().asInstanceOf[Seq[Relation]]
      iters += 1
    }
  }
  catch {
    case _:EOFException => ()
  }

  ois.close()
  println(num)
  println(iters)
}
