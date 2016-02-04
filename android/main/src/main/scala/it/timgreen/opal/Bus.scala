package it.timgreen.opal

import rx.lang.scala.subjects.BehaviorSubject
import rx.lang.scala.subjects.PublishSubject

import it.timgreen.android.model.SingleValue

object Bus {
  val currentCardIndex = BehaviorSubject[Option[Int]](None)
  val isSyncing = BehaviorSubject(false)
  val isSyncingDistinct = isSyncing.distinctUntilChanged
  val syncTrigger = PublishSubject[Int]()

  val fragmentRefreshTrigger = PublishSubject[Int]()
}
