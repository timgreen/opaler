package it.timgreen.opal.rxdata

import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.DataStatus
import it.timgreen.opal.api.CardDetails

object RxCards {

  val cards = BehaviorSubject[DataStatus[List[CardDetails]]](DataStatus.dataLoading)

  // TODO(timgreen): load/reload
}
