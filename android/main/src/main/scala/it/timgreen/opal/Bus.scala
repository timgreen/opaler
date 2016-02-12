package it.timgreen.opal

import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject
import rx.lang.scala.subjects.PublishSubject

import it.timgreen.android.model.SingleValue
import it.timgreen.opal.api.CardDetails

object Bus {

  val fragmentRefreshTrigger = PublishSubject[Int]()

  // TODO(timgreen): move into RxCards
}
