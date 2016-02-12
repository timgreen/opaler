package it.timgreen.opal.rxdata

import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.DataStatus
import it.timgreen.opal.api.CardTransaction

object RxTransactions {

  val transactions = BehaviorSubject[DataStatus[List[CardTransaction]]](DataStatus.dataLoading)

  // TODO(timgreen): better method name
  def reload(implicit context: Context) {
    // TODO(timgreen): set scheduler
  }
}
