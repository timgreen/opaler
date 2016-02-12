package it.timgreen.opal.rxdata

import android.content.Context

import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.DataStatus
import it.timgreen.opal.api.CardDetails
import it.timgreen.opal.provider.CardsCache

object RxCards {
  val currentCardIndex = BehaviorSubject[Int](0)

  private val cardsSubject = BehaviorSubject[DataStatus[List[CardDetails]]](DataStatus.dataLoading)
  val cards = cardsSubject.distinctUntilChanged

  val currentCardDetails: Observable[DataStatus[CardDetails]] =
    currentCardIndex.combineLatestWith(cards) { (cardIndex, cardsData) =>
      cardsData.flatMap { cards =>
        cards.lift(cardIndex) match {
          case Some(card) => DataStatus(card)
          case None => DataStatus.noData
        }
      }
    }

  // TODO(timgreen): listen to sync finished / refresh -> reload

  // TODO(timgreen): better method name
  def reload(implicit context: Context) {
    // TODO(timgreen): read from provider instead
    // TODO(timgreen): set scheduler
    cardsSubject.onNext(DataStatus(CardsCache.getCards))
  }
}
