package it.timgreen.opal

import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject
import rx.lang.scala.subjects.PublishSubject

import it.timgreen.android.model.SingleValue
import it.timgreen.opal.api.CardDetails

object Bus {
  val currentCardIndex = BehaviorSubject[Option[Int]](None)
  val isSyncing = BehaviorSubject(false)
  val isSyncingDistinct = isSyncing.distinctUntilChanged
  val syncTrigger = PublishSubject[Int]()

  val fragmentRefreshTrigger = PublishSubject[Int]()

  //
  val currentCardDetails: Observable[Option[CardDetails]] = currentCardIndex.combineLatestWith(rxdata.RxCards.cards) { (cardIndex, cards) =>
    cardIndex flatMap { i => cards.lift(i) }
  }
}
