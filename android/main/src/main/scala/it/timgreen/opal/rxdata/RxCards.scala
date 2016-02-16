package it.timgreen.opal.rxdata

import android.content.Context

import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.DataStatus
import it.timgreen.opal.api.Card
import it.timgreen.opal.provider.CardsCache

object RxCards {
  val currentCardIndex = BehaviorSubject[Int]()

  private val cardsSubject = BehaviorSubject[DataStatus[List[Card]]](DataStatus.dataLoading)
  val cards = cardsSubject
    .subscribeOn(BackgroundThread.scheduler)
    .observeOn(BackgroundThread.scheduler)
    .distinctUntilChanged

  val currentCard: Observable[DataStatus[Card]] = currentCardIndex
    .subscribeOn(BackgroundThread.scheduler)
    .observeOn(BackgroundThread.scheduler)
    .combineLatestWith(cards) { (cardIndex, cardsData) =>
      cardsData.flatMap { cards =>
        cards.lift(cardIndex) match {
          case Some(card) => DataStatus(card)
          case None => DataStatus.noData
        }
      }
    }

  // TODO(timgreen): better method name
  def loadData(implicit context: Context) {
    // TODO(timgreen): read from provider instead
    // TODO(timgreen): set scheduler
    cardsSubject.onNext(DataStatus(CardsCache.getCards))
  }

  RxSync.dataReloadTrigger
    .subscribeOn(BackgroundThread.scheduler)
    .observeOn(BackgroundThread.scheduler)
    .subscribe { c => loadData(c) }
}
