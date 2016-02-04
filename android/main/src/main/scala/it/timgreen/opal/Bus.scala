package it.timgreen.opal

import rx.lang.scala.subjects.BehaviorSubject
import rx.lang.scala.subjects.PublishSubject

import it.timgreen.android.model.SingleValue
import it.timgreen.android.model.Trigger

object Bus {
  // val selectedCardIndex = BehaviorSubject(0)
  // val hasCards = BehaviorSubject(false)
  val currentCardIndex = SingleValue[Option[Int]](None)
  val isSyncing = BehaviorSubject(false)
  val isSyncingDistinct = isSyncing.distinctUntilChanged
  val syncTrigger = PublishSubject[Int]()

  val fragmentRefreshTrigger = PublishSubject[Int]()
}
