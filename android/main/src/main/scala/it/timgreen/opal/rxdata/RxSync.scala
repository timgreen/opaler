package it.timgreen.opal.rxdata

import android.app.Activity
import android.content.Context

import rx.lang.scala.subjects.BehaviorSubject
import rx.lang.scala.subjects.PublishSubject

import it.timgreen.opal.Util
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.sync.{ Observer => SyncObserver }

object RxSync {
  private val isSyncingSubject = BehaviorSubject(false)
  val isSyncing = isSyncingSubject
    .subscribeOn(BackgroundThread.scheduler)
    .observeOn(BackgroundThread.scheduler)
    .distinctUntilChanged
  val syncTrigger = PublishSubject[Int]()
  val dataReloadTrigger = PublishSubject[Context]()

  def createSyncObserver(implicit activity: Activity) = {
    val syncSyncStatusOp = { () =>
      // TODO(timgreen): move out of ui thread
      activity.runOnUiThread(new Runnable {
        override def run() {
          val syncInProgress = AccountUtil.getAccount map { account =>
            SyncObserver.isSyncActive(account, OpalProvider.AUTHORITY)
          } getOrElse false
          isSyncingSubject.onNext(syncInProgress)
          Util.debug(s"sync status op: $syncInProgress")
        }
      })
    }
    // TODO(timgreen): move out of main thread
    syncSyncStatusOp()
    new SyncObserver(syncSyncStatusOp)
  }

  def markIsSyncing(s: Boolean) {
    isSyncingSubject.onNext(s)
  }
}
