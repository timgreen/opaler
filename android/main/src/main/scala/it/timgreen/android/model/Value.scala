package it.timgreen.android.model

abstract class Value[T](initValue: T) extends Listenable[Value.Listener[T]] {
  protected var value: T = initValue

  def apply(): T = value

  protected override def invoke(listener: Value.Listener[T]) {
    listener(value)
  }

  protected def update(newValue: T) {
    if (value != newValue) {
      value = newValue
      invokeAll
    }
  }

  def &[O](o: Value[O]): Value[(T, O)] = Value.&(this, o)
  def map[U](op: T => U): Value[U] = new MappedValue(this, op)
}

object Value {
  type Listener[T] = T => Unit

  def &[A, B](a: Value[A], b: Value[B]) = new CombinedValue(a, b)
}

private[model] class CombinedValue[A, B](a: Value[A], b: Value[B]) extends Value[(A, B)](a() -> b()) {
  a.on(invokeOnRegister = false, tag = this)(onParentChange)
  b.on(invokeOnRegister = false, tag = this)(onParentChange)

  private def onParentChange[T](t: T) {
    update(a() -> b())
  }
}

class SingleValue[T](defaultValue: T) extends Value[T](defaultValue) {
  override def update(newValue: T) = super.update(newValue)
}

object SingleValue {
  type Listener[T] = T => Unit
  def apply[T](defaultValue: T) = new SingleValue[T](defaultValue)
}

private [model] class MappedValue[T, U](orignal: Value[T], op: T => U) extends Value[U](op(orignal())) {
  orignal.on(invokeOnRegister = false, tag = this)(onOrignalChange)

  private def onOrignalChange(t: T) {
    update(op(t))
  }
}
