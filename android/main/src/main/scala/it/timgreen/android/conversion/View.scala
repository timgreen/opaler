package it.timgreen.android.conversion

import android.view.KeyEvent
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.{ View => AView }
import android.widget.{ TextView => ATextView }

import scala.language.implicitConversions

object View {

  implicit def toViewOnClickListener1[A](f: AView => A) =
    new AView.OnClickListener() { def onClick(v: AView) = f(v) }

  implicit def toViewOnClickListener2[A](f: () => A) =
    new AView.OnClickListener() { def onClick(v: AView) = f() }

  implicit def toViewTreeObserverOnGlobalLayoutListener(f: () => Unit) =
    new OnGlobalLayoutListener() { def onGlobalLayout = f() }
}

object TextView {
  implicit def toTextViewOnEditorActionListener(f: (ATextView, Int, KeyEvent) => Boolean) =
    new ATextView.OnEditorActionListener() {
      def onEditorAction(v: ATextView, actionId: Int, event: KeyEvent) = f(v, actionId, event)
    }

  implicit def toTextViewOnEditorActionListener2(f: () => Boolean) =
    new ATextView.OnEditorActionListener() {
      def onEditorAction(v: ATextView, actionId: Int, event: KeyEvent) = f()
    }
}

