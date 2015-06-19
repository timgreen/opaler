package it.timgreen.android.model

import scala.collection.mutable

class Trigger {
  private val listeners = mutable.MutableList[() => Unit]()

  def on(listener: () => Unit) {
    listener()
    listeners += listener
  }

  def fire() {
    listeners foreach { listener =>
      listener()
    }
  }
}

object Trigger {
  def apply() = new Trigger
}
