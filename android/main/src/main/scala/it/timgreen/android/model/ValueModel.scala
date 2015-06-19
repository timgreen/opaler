package it.timgreen.android.model

import scala.collection.mutable

class ValueModel[T](defaultValue: T) {
  private var value: T = defaultValue
  private val listeners = mutable.MutableList[T => Unit]()

  def apply(): T = value

  def update(newValue: T) {
    if (value != newValue) {
      value = newValue
      fireChange
    }
  }

  def on(listener: T => Unit) {
    listener(value)
    listeners += listener
  }

  private def fireChange() {
    listeners foreach { listener =>
      listener(value)
    }
  }

  // TODO(timgreen): remove listener
}

object ValueModel {
  def apply[T](defaultValue: T) = new ValueModel[T](defaultValue)
}
