package it.timgreen.opal

import scala.collection.Iterator

object DataStatus {

  import scala.language.implicitConversions

  def apply[A](x: A): DataStatus[A] = if (x == null) NoData else DataLoaded(x)

  def noData[A]: DataStatus[A] = NoData
  def dataLoading[A]: DataStatus[A] = DataLoading
}

sealed abstract class DataStatus[+A] extends Product {
  self =>

  def isEmpty: Boolean
  def isDefined: Boolean = !isEmpty
  def get: A

  @inline final def getOrElse[B >: A](default: => B): B =
    if (isEmpty) default else this.get

  @inline final def orNull[A1 >: A](implicit ev: Null <:< A1): A1 = this getOrElse ev(null)

  @inline final def map[B](f: A => B): DataStatus[B] = this match {
    case NoData => NoData
    case DataLoading => DataLoading
    case DataLoaded(data) => DataLoaded(f(data))
  }

  @inline final def fold[B](ifEmpty: => B)(f: A => B): B =
    if (isEmpty) ifEmpty else f(this.get)

  @inline final def flatMap[B](f: A => DataStatus[B]): DataStatus[B] = this match {
    case NoData => NoData
    case DataLoading => DataLoading
    case DataLoaded(data) => f(data)
  }

  def flatten[B](implicit ev: A <:< DataStatus[B]): DataStatus[B] = this match {
    case NoData => NoData
    case DataLoading => DataLoading
    case DataLoaded(data) => ev(data)
  }

  @inline final def filter(p: A => Boolean): DataStatus[A] =
    if (isEmpty || p(this.get)) this else NoData

  @inline final def filterNot(p: A => Boolean): DataStatus[A] =
    if (isEmpty || !p(this.get)) this else NoData

  final def nonEmpty = isDefined

  @inline final def withFilter(p: A => Boolean): WithFilter = new WithFilter(p)

  class WithFilter(p: A => Boolean) {
    def map[B](f: A => B): DataStatus[B] = self filter p map f
    def flatMap[B](f: A => DataStatus[B]): DataStatus[B] = self filter p flatMap f
    def foreach[U](f: A => U): Unit = self filter p foreach f
    def withFilter(q: A => Boolean): WithFilter = new WithFilter(x => p(x) && q(x))
  }

  final def contains[A1 >: A](elem: A1): Boolean =
    !isEmpty && this.get == elem

  @inline final def exists(p: A => Boolean): Boolean =
    !isEmpty && p(this.get)

  @inline final def forall(p: A => Boolean): Boolean = isEmpty || p(this.get)

  @inline final def foreach[U](f: A => U) {
    if (!isEmpty) f(this.get)
  }

  @inline final def collect[B](pf: PartialFunction[A, B]): DataStatus[B] = {
    if (!isEmpty) {
      pf.lift(this.get).map(x => DataStatus(x)) getOrElse NoData
    } else {
      NoData
    }
  }

  @inline final def orElse[B >: A](alternative: => DataStatus[B]): DataStatus[B] =
    if (isEmpty) alternative else this

  def iterator: Iterator[A] =
    if (isEmpty) Iterator.empty else Iterator.single(this.get)

  def toList: List[A] =
    if (isEmpty) List() else new ::(this.get, Nil)

  @inline final def toRight[X](left: => X) =
    if (isEmpty) Left(left) else Right(this.get)

  @inline final def toLeft[X](right: => X) =
    if (isEmpty) Right(right) else Left(this.get)
}

case object DataLoading extends DataStatus[Nothing] {
  override def isEmpty = true
  override def get = throw new NoSuchElementException("DataLoading.get")
}

case object NoData extends DataStatus[Nothing] {
  override def isEmpty = true
  override def get = throw new NoSuchElementException("NoData.get")
}

final case class DataLoaded[+A](data: A) extends DataStatus[A] {
  override def isEmpty = false
  override def get = data
}

