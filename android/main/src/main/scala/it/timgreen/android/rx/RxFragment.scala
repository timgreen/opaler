package it.timgreen.android.rx

import android.app.Activity
import android.app.Fragment
import android.os.Bundle
import android.support.annotation.CallSuper
import android.view.View

import com.trello.rxlifecycle.FragmentEvent
import com.trello.rxlifecycle.RxLifecycle

import rx.lang.scala.ImplicitFunctionConversions._
import rx.lang.scala.JavaConversions._
import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject

import scala.language.implicitConversions

trait RxFragment { self: Fragment =>

  val lifecycleSubject = BehaviorSubject[FragmentEvent]()

  @CallSuper
  override def onAttach(activity: Activity) {
    self.onAttach(activity)
    lifecycleSubject.onNext(FragmentEvent.ATTACH)
  }

  @CallSuper
  override def onCreate(savedInstanceState: Bundle) {
    self.onCreate(savedInstanceState)
    lifecycleSubject.onNext(FragmentEvent.CREATE)
  }

  @CallSuper
  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    self.onViewCreated(view, savedInstanceState)
    lifecycleSubject.onNext(FragmentEvent.CREATE_VIEW)
  }

  @CallSuper
  override def onStart() {
    self.onStart
    lifecycleSubject.onNext(FragmentEvent.START)
  }

  @CallSuper
  override def onResume() {
    self.onResume
    lifecycleSubject.onNext(FragmentEvent.RESUME)
  }

  @CallSuper
  override def onPause() {
    lifecycleSubject.onNext(FragmentEvent.PAUSE)
    self.onPause
  }

  @CallSuper
  override def onStop() {
    lifecycleSubject.onNext(FragmentEvent.STOP)
    self.onStop
  }

  @CallSuper
  override def onDestroyView() {
    lifecycleSubject.onNext(FragmentEvent.DESTROY_VIEW)
    self.onDestroyView
  }

  @CallSuper
  override def onDestroy() {
    lifecycleSubject.onNext(FragmentEvent.DESTROY)
    self.onDestroy
  }

  @CallSuper
  override def onDetach() {
    lifecycleSubject.onNext(FragmentEvent.DETACH)
    self.onDetach
  }

  implicit protected def toRichObservable[T](observable: Observable[T]) =
    new RxFragment.RichObservable(observable, lifecycleSubject)
}

object RxFragment {
  private[rx] class RichObservable[T](
    observable: rx.Observable[T], lifecycleSubject: BehaviorSubject[FragmentEvent]) {
    def bindToLifecycle: Observable[T] = {
      val op: rx.Observable.Transformer[T, T] =
        RxLifecycle.bindFragment(lifecycleSubject.asJavaObservable.asInstanceOf[rx.Observable[FragmentEvent]])
      toScalaObservable(observable.compose(op))
    }
  }
}
