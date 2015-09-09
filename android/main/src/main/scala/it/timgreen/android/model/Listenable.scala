package it.timgreen.android.model

import scala.collection.mutable

trait Listenable[Listener] {
  private var listeners = mutable.MutableList[(Listener, Any)]()

  protected def invoke(listener: Listener)

  protected def invokeAll() {
    listeners foreach { case (listener, _) =>
      invoke(listener)
    }
  }

  def on(listener: Listener) {
    on()(listener)
  }

  def on(invokeOnRegister: Boolean = true, tag: Any = null)(listener: Listener) {
    if (listeners.isEmpty) {
      preAddFirstListener
    }

    if (invokeOnRegister) {
      invoke(listener)
    }
    val newPair = (listener, tag)
    listeners += newPair
  }

  def removeByTag(removeTag: Any) {
    listeners = listeners.filterNot { case (_, tag) => tag == removeTag }

    if (listeners.isEmpty) {
      postRemoveLastListener
    }
  }

  protected def preAddFirstListener() {}
  protected def postRemoveLastListener() {}
}
