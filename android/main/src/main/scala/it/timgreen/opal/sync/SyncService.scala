package it.timgreen.opal.sync

import android.app.Service
import android.content.Intent
import android.os.IBinder

class SyncService extends Service {

  private var syncAdapter: SyncAdapter = null

  override def onCreate {
    super.onCreate
    SyncService.synchronized {
      if (syncAdapter == null) {
        syncAdapter = new SyncAdapter(getApplicationContext, true, true)
      }
    }
  }

  override def onBind(intent: Intent): IBinder = {
    syncAdapter.getSyncAdapterBinder
  }
}

object SyncService
