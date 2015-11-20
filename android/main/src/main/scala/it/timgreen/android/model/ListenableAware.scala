package it.timgreen.android.model

import scala.collection.mutable
import scala.language.implicitConversions

import android.app.Activity

trait ListenableAwareActivity { self: Activity =>

  override protected def onResume() {
    self.onResume
    registerListeners
  }

  override protected def onPause() {
    unregisterListeners
    self.onPause
  }

  private var pairs = mutable.MutableList[Pair[_]]()

  private[model] def addPair[T](listenable: Listenable[T], listener: T) {
    pairs += Pair(listenable, listener)
  }

  private def registerListeners() {
    pairs foreach { pair =>
      pair.register
    }
  }

  private def unregisterListeners() {
    pairs foreach { pair =>
      pair.unregister
    }
  }

  implicit def toRichListenable[T](listenable: Listenable[T]) = new RichListenable(listenable, this)
}

private[model] case class Pair[Listener](
  listenable: Listenable[Listener],
  listener: Listener) {
  def register() { listenable.on(tag = this)(listener) }
  def unregister() { listenable.removeByTag(this) }
}

private[model] class RichListenable[T](
  listenable: Listenable[T], listenableAware: ListenableAwareActivity) {
  def duringResumePause(listener: T) {
    listenableAware.addPair(listenable, listener)
  }
}
