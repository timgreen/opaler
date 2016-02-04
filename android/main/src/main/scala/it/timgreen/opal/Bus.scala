package it.timgreen.opal

import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.android.model.SingleValue
import it.timgreen.android.model.Trigger

object Bus {
  val currentCardIndex = SingleValue[Option[Int]](None)
  val isSyncing = BehaviorSubject(false)
  val isSyncingDistinct = isSyncing.distinctUntilChanged

  val syncTrigger = Trigger()
  val fragmentRefreshTrigger = Trigger()
}
