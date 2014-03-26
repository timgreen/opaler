package it.timgreen.opal

import android.app.Activity
import android.content.Intent
import android.os.Bundle

import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.provider.CardsCache
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.TransactionTable
import it.timgreen.opal.sync.SyncStatus

object AccountHelper {

  trait Checker extends Operator { self: Activity =>

    private def ensureLogin(implicit activity: Activity) {
      if (!hasLogin) {
        clearAppData
        startLoginActivity
        finish
      }
    }

    @inline
    protected def startLoginActivity() {
      val intent = new Intent(this, classOf[LoginActivity])
      intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      startActivity(intent)
    }

    @inline
    def hasLogin(implicit activity: Activity) = AccountUtil.getAccount.isDefined

    override protected def onResume() {
      ensureLogin(this)
      self.onResume
    }

    override protected def onRestart() {
      ensureLogin(this)
      self.onRestart
    }

    override def onCreate(savedInstanceState: Bundle) {
      self.onCreate(savedInstanceState)
      ensureLogin(this)
    }
  }

  trait Operator {

    protected def clearAppData(implicit activity: Activity) {
      activity.getContentResolver.delete(
        OpalProvider.Uris.activities(0),
        "",
        Array()
      )
      activity.deleteDatabase(TransactionTable.databaseName)
      CardsCache.getPrefs.edit.clear.commit
      SyncStatus.clearSyncStatus
      PrefUtil.prefs.edit.clear.commit
    }
  }
}
