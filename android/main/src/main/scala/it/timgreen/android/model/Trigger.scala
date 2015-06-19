package it.timgreen.android.model

import scala.collection.mutable

class Trigger extends Listenable[Trigger.Listener] {
  private val listeners = mutable.MutableList[() => Unit]()

  def fire() {
    invokeAll
  }

  protected def invoke(listener: Trigger.Listener) {
    listener()
  }
}

object Trigger {
  type Listener = () => Unit
  def apply() = new Trigger
}
