package it.timgreen.opal.rxdata

import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.api.CardTransaction

object RxTransactions {

  val transactions = BehaviorSubject[List[CardTransaction]]()

  // TODO(timgreen): load/reload
}
