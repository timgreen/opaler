package it.timgreen.android.model

import scala.collection.mutable

class ValueModel[T](defaultValue: T) extends Listenable[ValueModel.Listener[T]] {
  private var value: T = defaultValue

  def apply(): T = value

  def update(newValue: T) {
    if (value != newValue) {
      value = newValue
      invokeAll
    }
  }

  protected def invoke(listener: ValueModel.Listener[T]) {
    listener(value)
  }
}

object ValueModel {
  type Listener[T] = T => Unit
  def apply[T](defaultValue: T) = new ValueModel[T](defaultValue)
}
