package it.timgreen.opal.rxdata

import android.os.Handler
import android.os.HandlerThread
import android.os.Process

import rx.android.schedulers.HandlerScheduler
import rx.lang.scala.JavaConversions._
import rx.lang.scala.Scheduler

object BackgroundThread {
  lazy val handler: Handler = createHandler
  lazy val scheduler: Scheduler = HandlerScheduler.from(handler)

  private def createHandler: Handler = {
    val backgroundThread = new BackgroundThread
    backgroundThread.start
    new Handler(backgroundThread.getLooper)
  }
}

class BackgroundThread
  extends HandlerThread("Opaler-BackgroundThread", Process.THREAD_PRIORITY_BACKGROUND) {
}
