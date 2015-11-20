package it.timgreen.opal.sync

import android.accounts.Account
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle

import scala.annotation.tailrec

import it.timgreen.android.net.Http
import it.timgreen.android.net.NetworkConnectionChecker
import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.BuildConfig
import it.timgreen.opal.Usage
import it.timgreen.opal.Util
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.api.ApiChangedException
import it.timgreen.opal.api.CardDetails
import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.api.FareApplied
import it.timgreen.opal.api.LoginFailedException
import it.timgreen.opal.api.Model
import it.timgreen.opal.api.OpalAccount
import it.timgreen.opal.api.OpalApi
import it.timgreen.opal.api.TransactionDetails
import it.timgreen.opal.gtm.Gtm
import it.timgreen.opal.provider.CardsCache
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.TransactionTable
import it.timgreen.opal.widget.OpalWidgetUpdateService

class SyncAdapter(context: Context, autoInitialize: Boolean, allowParallelSyncs: Boolean)
  extends AbstractThreadedSyncAdapter(context, autoInitialize, allowParallelSyncs) {

  override def onPerformSync(account: Account, extras: Bundle, authority: String,
                             provider: ContentProviderClient, syncResult: SyncResult) {
    val isManualSync = extras.getBoolean(SyncAdapter.isManualSyncKey)
    if (shouldSkipSync(isManualSync)) {
      Util.debug("SyncAdapter sikpped")
      trackEvent("Sync", "syncSkip")(context)
    } else {
      trackBlockTiming("Sync") {
        val username = account.name
        val password = AccountUtil.getPassword(account)(context)
        val opalAccount = OpalAccount(username, password)

        Util.debug("SyncAdapter(" + isManualSync + ") start: " + username)
        sync(account, opalAccount, syncResult)
        if (!isManualSync) {
          Gtm.refresh(context)
        }
        Util.debug("SyncAdapter(" + isManualSync + ") end:   " + username)
      } (context)
    }
    syncResult.delayUntil = 60 * 60  // no auto sync in one hour
  }

  private def shouldSkipSync(isManualSync: Boolean): Boolean = {
    val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(context)
    !isManualSync &&
      prefs.getBoolean("auto_sync_on_wifi_only", true) &&
      !NetworkConnectionChecker.hasWifiConnection(context)
  }

  private def sync(implicit account: Account, opalAccount: OpalAccount, syncResult: SyncResult) {
    try {
      OpalApi.getCardDetailsList.left map handleCardDetails match {
        case Left(_) =>
          SyncStatus.markSyncResult(SyncStatus.ResultType.success)(context)
        case Right(t) =>
          handleError(t)
      }
    } catch {
      case t: Throwable =>
        handleError(t)
    }
  }

  private def handleError(t: Throwable)(implicit account: Account, syncResult: SyncResult) {
    Util.debug("SyncAdapter error", t)
    t match {
      case e: ApiChangedException =>
        syncResult.stats.numParseExceptions += 1
        SyncStatus.markSyncResult(SyncStatus.ResultType.serverApiChanged)(context)
        reportError(t)(context)
      case LoginFailedException(failed) =>
        syncResult.stats.numAuthExceptions += 1
        SyncStatus.markSyncResult(SyncStatus.ResultType.loginFailed)(context)
        AccountUtil.disableAutoSync(account)
      case Http.UnexpectedResponseCode(code, _) =>
        if ((code >= 500) && (code < 600)) {
          syncResult.stats.numIoExceptions += 1
          SyncStatus.markSyncResult(SyncStatus.ResultType.serverError)(context)
        } else {
          syncResult.stats.numParseExceptions += 1
          SyncStatus.markSyncResult(SyncStatus.ResultType.serverApiChanged)(context)
        }
      case _: java.io.IOException =>
        syncResult.stats.numIoExceptions += 1
        SyncStatus.markSyncResult(SyncStatus.ResultType.ioError)(context)
      case _ =>
        syncResult.stats.numParseExceptions += 1
        SyncStatus.markSyncResult(SyncStatus.ResultType.serverApiChanged)(context)
        reportError(t)(context)
    }
  }

  private def handleCardDetails(cardDetailsList: List[CardDetails])
                               (implicit opalAccount: OpalAccount, syncResult: SyncResult) {
    val cv = new ContentValues
    cv.put(CardsCache.cardCacheKey, CardDetails.toJsonArray(cardDetailsList).toString)
    context.getContentResolver.update(OpalProvider.Uris.cards, cv, null, null)
    (Usage.numOfCards() = cardDetailsList.size)(context)

    Util.debug("SyncAdapter, cards: " + cardDetailsList)
    updateWidget

    cardDetailsList.par foreach { cardDetails =>
      syncCardTransaction(cardDetails)
    }
    updateWidget
  }

  @inline
  private def updateWidget() {
    val intent = new Intent(context, classOf[OpalWidgetUpdateService])
    intent.putExtra(OpalWidgetUpdateService.updateWidgetOnlyKey, true)
    context.startService(intent)
  }

  private def syncCardTransaction(cardDetails: CardDetails)
                                 (implicit opalAccount: OpalAccount, syncResult: SyncResult) {
    val c = context.getContentResolver.query(OpalProvider.Uris.activitiesMaxId(cardDetails.index), null, null, null, null)
    c.moveToFirst
    val maxTransactionNumber = c.getInt(0)
    val syncStopPoint = maxTransactionNumber

    val overrideRecordNum = Gtm.getOverrideRecordNum(context)
    @tailrec
    def fetchUpdates(pageIndex: Int = 1, list: List[CardTransaction] = Nil): List[CardTransaction] = {
      Util.debug(s"Sync transaction card ${cardDetails.index} page $pageIndex")
      val updatedTime = Util.currentTimeInMs
      val r = OpalApi.getCardTransactions(cardDetails.index, pageIndex, updatedTime)
      r match {
        case Left((false, l)) => list ::: l
        case Left((true, l)) =>
          if (l.last.transactionNumber <= syncStopPoint - overrideRecordNum) {
            list ::: l
          } else {
            fetchUpdates(pageIndex + 1, list ::: l)
          }
        case Right(t) =>
          Util.debug(s"Sync error - fetch transaction card ${cardDetails.index} page $pageIndex", t)
          throw t
      }
    }
    val updates = fetchUpdates()
    if (updates.nonEmpty) {
      context.getContentResolver.bulkInsert(
        OpalProvider.Uris.activities(cardDetails.index),
        updates.map(TransactionTable.toValues).toArray
      )
      syncResult.stats.numInserts += updates.size
      checkUpdates(updates)

      val newMaxTransactionNumber = updates.head.transactionNumber
      if (Usage.numOfTransaction()(context) < newMaxTransactionNumber) {
        (Usage.numOfTransaction() = newMaxTransactionNumber)(context)
      }
    }
  }

  private def checkUpdates(updates: List[CardTransaction]) {
    val unknownFareApplieds = updates.filter(_.fareApplied.isInstanceOf[FareApplied.Unknown]).map(_.fareApplied.toString).toSet
    unknownFareApplieds foreach { unknownFareApplied =>
      trackEvent("SelfCheckError", "unknownFareApplied", Some(BuildConfig.VERSION_CODE + ": " + unknownFareApplied))(context)
      Util.debug(s"SelfCheckError unknownFareApplied: $unknownFareApplied")
    }

    val unknownModels = updates.filter(_.model.isInstanceOf[Model.Unknown]).map(_.model.toString).filter(_ != "").toSet
    unknownModels foreach { unknownModel =>
      trackEvent("SelfCheckError", "unknownModel", Some(BuildConfig.VERSION_CODE + ": " + unknownModel))(context)
      val unknownModelsDetails = updates.filter(_.model.isInstanceOf[Model.Unknown]).map(_.details.toString).headOption
      Util.debug(s"SelfCheckError unknownModel: |$unknownModel| $unknownModelsDetails")
    }

    val unknownDetailses = updates.filter(_.details.isInstanceOf[TransactionDetails.Unknown]).map(_.details.toString).filter(_ != "").toSet
    unknownDetailses foreach { unknownDetails =>
      trackEvent("SelfCheckError", "unknownDetails", Some(BuildConfig.VERSION_CODE + ": " + unknownDetails))(context)
      Util.debug(s"SelfCheckError unknownDetails: $unknownDetails")
    }
  }
}

object SyncAdapter {
  val isManualSyncKey = "is_manual_sync"
}
