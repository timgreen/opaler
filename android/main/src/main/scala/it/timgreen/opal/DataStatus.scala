package it.timgreen.opal

sealed trait DataStatus[+A] {
  @inline final def map[B](op: A => B): DataStatus[B] = this match {
    case NoData => DataStatus.noData
    case DataLoading => DataStatus.dataLoading
    case DataLoaded(data) => DataLoaded(op(data))
  }

  @inline final def foreach[U](op: A => U) {
    if (!isEmpty) {
      op(this.get)
    }
  }

  def isEmpty: Boolean = true
  def get: A = throw new NoSuchElementException("NoData/DataLoading.get")

}

case object DataLoading extends DataStatus[Nothing];
case object NoData extends DataStatus[Nothing];
case class DataLoaded[+A](
  data: A
) extends DataStatus[A] {
  override def isEmpty = false
  override def get = data
}

object DataStatus {
  def noData[A]: DataStatus[A] = NoData
  def dataLoading[A]: DataStatus[A] = DataLoading
}
