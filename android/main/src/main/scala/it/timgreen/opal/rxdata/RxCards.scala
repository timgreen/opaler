package it.timgreen.opal.rxdata

import android.content.Context

import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.DataStatus
import it.timgreen.opal.api.CardDetails
import it.timgreen.opal.provider.CardsCache

object RxCards {
  private val cardsSubject = BehaviorSubject[DataStatus[List[CardDetails]]](DataStatus.dataLoading)
  val cards = cardsSubject.distinctUntilChanged

  // TODO(timgreen): listen to sync finished / refresh -> reload

  // TODO(timgreen): better method name
  def reload(implicit context: Context) {
    // TODO(timgreen): read from provider instead
    // TODO(timgreen): set scheduler
    cardsSubject.onNext(DataStatus(CardsCache.getCards))
  }
}
