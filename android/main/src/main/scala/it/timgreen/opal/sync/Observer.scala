package it.timgreen.opal.sync

import android.accounts.Account
import android.content.ContentResolver
import android.content.SyncStatusObserver

import scala.collection.JavaConversions._

class Observer(op: () => Unit) extends SyncStatusObserver {
  private var syncHandle: AnyRef = null

  override def onStatusChanged(which: Int) {
    op()
  }

  def addListener() {
    val mask = ContentResolver.SYNC_OBSERVER_TYPE_PENDING |
               ContentResolver.SYNC_OBSERVER_TYPE_ACTIVE
    syncHandle = ContentResolver.addStatusChangeListener(mask, this)
  }

  def removeListener() {
    if (syncHandle != null) {
      ContentResolver.removeStatusChangeListener(syncHandle)
      syncHandle = null
    }
  }
}

object Observer {
  def isSyncActive(account: Account, authority: String): Boolean = {
    ContentResolver.getCurrentSyncs exists { syncInfo =>
      (syncInfo.account == account) && (syncInfo.authority == authority)
    }
  }
}
