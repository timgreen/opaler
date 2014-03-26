package it.timgreen.opal.sync

import android.content.Context
import android.text.format.Time

import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.Util
import it.timgreen.opal.account.AccountUtil

case class SyncStatus(
  timeInSeconds: Long,
  resultType: String
)

object SyncStatus {
  val SYNC_STATUS_PREFS = "sync_status"
  val LAST_SUCCESSFUL_SYNC_TIME_KEY = "last_successful_sync_time"
  val LAST_SYNC_TIME_KEY = "last_sync_time"
  val LAST_SYNC_RESULT_TYPE_KEY = "last_sync_result_type"
  val ID_NEED_RESYNC_KEY_PREFIX = "id_need_resync_"

  object ResultType {
    val success = "success"
    val loginFailed = "loginFailed"
    val serverError = "serverError"
    val serverApiChanged = "serverApiChanged"
    val ioError = "ioError"
  }

  def markSyncResult(resultType: String)(implicit context: Context) {
    trackEvent("Sync", resultType)
    val time = new Time(Time.getCurrentTimezone)
    time.setToNow
    AccountUtil.getAccount foreach { account =>
      AccountUtil.getAccountManager.setUserData(account, LAST_SYNC_RESULT_TYPE_KEY, resultType)
      if (resultType == ResultType.success) {
        AccountUtil.getAccountManager.setUserData(account, LAST_SUCCESSFUL_SYNC_TIME_KEY, time.toMillis(false).toString)
      }
      AccountUtil.getAccountManager.setUserData(account, LAST_SYNC_TIME_KEY, time.toMillis(false).toString)
    }
  }

  def getSyncResult(implicit context: Context): Option[SyncStatus] = {
    AccountUtil.getAccount flatMap { account =>
      val resultType = AccountUtil.getAccountManager.getUserData(account, LAST_SYNC_RESULT_TYPE_KEY)
      if (resultType != null) {
        AccountUtil.getAccountManager.setUserData(account, LAST_SYNC_RESULT_TYPE_KEY, null)
        val lastSyncTime = Option(AccountUtil.getAccountManager.getUserData(account, LAST_SYNC_TIME_KEY)).getOrElse("0").toLong
        Some(SyncStatus(
          lastSyncTime,
          resultType
        ))
      } else {
        None
      }
    }
  }

  def hasSyncedBefore(implicit context: Context) = {
    AccountUtil.getAccount map { account =>
      AccountUtil.getAccountManager.getUserData(account, LAST_SUCCESSFUL_SYNC_TIME_KEY) != null
    } getOrElse false
  }

  def needSync(implicit context: Context) = {
    val nowTime = new Time(Time.getCurrentTimezone)
    nowTime.setToNow
    val lastSyncTime = AccountUtil.getAccount.flatMap( account =>
      Option(AccountUtil.getAccountManager.getUserData(account, LAST_SYNC_TIME_KEY))
    ).getOrElse("0").toLong
    val delta = nowTime.toMillis(false) - lastSyncTime
    Util.debug(s"nowTime ${nowTime.toMillis(false)} lastSyncTime $lastSyncTime delta $delta")

    delta > 3600 * 1000 * 4  // 4hrs
  }

  def getLastSuccessfulSyncTime(implicit context: Context): String = {
    AccountUtil.getAccount.flatMap { account =>
      Option(AccountUtil.getAccountManager.getUserData(account, LAST_SUCCESSFUL_SYNC_TIME_KEY))
    } match {
      case None => ""
      case Some(t) =>
        val time = new Time(Time.getCurrentTimezone)
        time.set(t.toLong)
        time.format("%b %d %H:%M:%S")
    }
  }

  def clearSyncStatus(implicit context: Context) {
    AccountUtil.getAccount foreach { account =>
      AccountUtil.getAccountManager.setUserData(account, LAST_SYNC_RESULT_TYPE_KEY, null)
      AccountUtil.getAccountManager.setUserData(account, LAST_SUCCESSFUL_SYNC_TIME_KEY, null)
      AccountUtil.getAccountManager.setUserData(account, LAST_SYNC_TIME_KEY, null)
    }
  }

  def setIdNeedResync(cardIndex: Int, idNeedResync: Int)(implicit context: Context) {
    AccountUtil.getAccount foreach { account =>
      AccountUtil.getAccountManager.setUserData(account, ID_NEED_RESYNC_KEY_PREFIX + cardIndex, idNeedResync.toString)
    }
  }

  def getIdNeedResync(cardIndex: Int)(implicit context: Context): Int = {
    AccountUtil.getAccount flatMap { account =>
      Option(AccountUtil.getAccountManager.getUserData(account, ID_NEED_RESYNC_KEY_PREFIX + cardIndex)) map { _.toInt }
    } getOrElse 0
  }
}
