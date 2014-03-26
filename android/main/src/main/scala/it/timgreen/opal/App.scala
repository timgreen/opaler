package it.timgreen.opal

import android.app.Application

class App extends Application {

  workaroundForGtmCrash

  /**
   * See https://productforums.google.com/forum/#!topic/tag-manager/87Kb_TZv79w for details.
   * TODO(timgreen): remove this.
   */
  def workaroundForGtmCrash() {
    Util.debug("workaround for GTM crash")
    val defaultUncaughtExceptionHandler = Thread.getDefaultUncaughtExceptionHandler
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      override def uncaughtException(thread: Thread, ex: Throwable) {
        val classpath = Option(ex).flatMap(_.getStackTrace.headOption).map(_.toString)
        if (classpath.exists(_.contains("tagmanager"))) {
          // avoid app crashing caused by tagmanager but report it via ga
          AnalyticsSupport.reportError(ex)(App.this)
        } else {
          // normal handling with ga reporting
          defaultUncaughtExceptionHandler.uncaughtException(thread, ex)
        }
      }
    })
  }
}
