package it.timgreen.opal

import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject
import rx.lang.scala.subjects.PublishSubject

import it.timgreen.android.model.SingleValue
import it.timgreen.opal.api.CardDetails

object Bus {
  val currentCardIndex = BehaviorSubject[Int](0)

  val fragmentRefreshTrigger = PublishSubject[Int]()

  // TODO(timgreen): move into RxCards
  val currentCardDetails: Observable[DataStatus[CardDetails]] =
    currentCardIndex.combineLatestWith(rxdata.RxCards.cards) { (cardIndex, cardsData) =>
      cardsData.flatMap { cards =>
        cards.lift(cardIndex) match {
          case Some(card) => DataStatus(card)
          case None => NoData
        }
      }
    }
}
