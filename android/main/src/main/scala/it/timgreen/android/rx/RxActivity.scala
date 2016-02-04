package it.timgreen.android.rx

import android.app.Activity
import android.os.Bundle
import android.support.annotation.CallSuper

import com.trello.rxlifecycle.ActivityEvent
import com.trello.rxlifecycle.RxLifecycle

import rx.lang.scala.ImplicitFunctionConversions._
import rx.lang.scala.JavaConversions._
import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject

import scala.language.implicitConversions

trait RxActivity { self: Activity =>

  implicit val lifecycleSubject = BehaviorSubject[ActivityEvent]()

  @CallSuper
  override protected def onCreate(savedInstanceState: Bundle) {
    self.onCreate(savedInstanceState)
    lifecycleSubject.onNext(ActivityEvent.CREATE)
  }

  @CallSuper
  override protected def onStart() {
    self.onStart
    lifecycleSubject.onNext(ActivityEvent.START)
  }

  @CallSuper
  override protected def onResume() {
    self.onResume
    lifecycleSubject.onNext(ActivityEvent.RESUME)
  }

  @CallSuper
  override protected def onPause() {
    lifecycleSubject.onNext(ActivityEvent.PAUSE)
    self.onPause
  }

  @CallSuper
  override protected def onStop() {
    lifecycleSubject.onNext(ActivityEvent.STOP)
    self.onStop
  }

  @CallSuper
  override protected def onDestroy() {
    lifecycleSubject.onNext(ActivityEvent.DESTROY)
    self.onDestroy
  }

  implicit protected def toRichObservable[T](observable: Observable[T]) =
    new RxActivity.RichObservable(observable, lifecycleSubject)
}

object RxActivity {
  private[rx] class RichObservable[T](
    observable: rx.Observable[T], lifecycleSubject: BehaviorSubject[ActivityEvent]) {
    def bindToLifecycle: Observable[T] = {
      val op: rx.Observable.Transformer[T, T] =
        RxLifecycle.bindActivity(lifecycleSubject.asJavaObservable.asInstanceOf[rx.Observable[ActivityEvent]])
      toScalaObservable(observable.compose(op))
    }
  }
}
