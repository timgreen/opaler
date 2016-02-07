package it.timgreen.opal.rxdata

import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.api.CardDetails

object RxCards {

  val cards = BehaviorSubject[List[CardDetails]]()

  // TODO(timgreen): load/reload
}
