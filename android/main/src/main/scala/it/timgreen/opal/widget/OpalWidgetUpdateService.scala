package it.timgreen.opal.widget

import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.widget.RemoteViews

import scala.annotation.tailrec

import it.timgreen.opal.MainActivity
import it.timgreen.opal.Overview
import it.timgreen.opal.R
import it.timgreen.opal.Usage
import it.timgreen.opal.Util
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.api.CardDetails
import it.timgreen.opal.provider.CardsCache
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.WidgetSettings
import it.timgreen.opal.sync.SyncAdapter

object OpalWidgetUpdateService {
  val updateWidgetOnlyKey = "updateWidgetOnly"
  val maxLabelTextWidthInDp = 65
}

class OpalWidgetUpdateService extends Service {
  override def onBind(intent: Intent): IBinder = null

  override def onStartCommand(intent: Intent, flags: Int, startId: Int): Int = {
    if (intent == null || !intent.getBooleanExtra(OpalWidgetUpdateService.updateWidgetOnlyKey, false)) {
      sync
    }
    updateWidgets

    stopSelf(startId)
    Service.START_STICKY
  }

  private def updateWidgets() {
    val appWidgetManager = AppWidgetManager.getInstance(this)
    val appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(this, classOf[OpalWidgetProvider]))
    Util.debug("Update widgets " + appWidgetIds.mkString(","))

    val cards = if (appWidgetIds.nonEmpty) {
      val cursor = getContentResolver.query(OpalProvider.Uris.cards, null, null, null, null)
      CardsCache.convert(cursor)
    } else {
      Nil
    }

    appWidgetIds foreach { appWidgetId =>
      val cursor = getContentResolver.query(OpalProvider.Uris.widget(appWidgetId), null, null, null, null)
      cursor.moveToFirst
      val cardIndex = cursor.getInt(cursor.getColumnIndex(WidgetSettings.cardIndexKey))

      val remoteViews = new RemoteViews(getPackageName,  R.layout.widget)
      remoteViews.setTextViewText(R.id.cardName, cards.lift(cardIndex).map(_.cardNickName) getOrElse "")

      val balance = getBalance(cardIndex, cards)
      val textSize = calcBalanceTextSize(balance)
      remoteViews.setTextViewText(R.id.label, balance)
      remoteViews.setTextViewTextSize(R.id.label, android.util.TypedValue.COMPLEX_UNIT_SP, textSize)

      val maxJourneyNumber = Overview.getOverviewData(this, cardIndex).maxJourneyNumber
      val balls = Util.getBalls(maxJourneyNumber).replaceAll("\\s", "")
      List(
        R.id.ball0,
        R.id.ball1,
        R.id.ball2,
        R.id.ball3,
        R.id.ball4,
        R.id.ball5,
        R.id.ball6,
        R.id.ball7
      ).zipWithIndex foreach { case (r, i) =>
        val image = balls(i) match {
          case '●' => R.drawable.dot_solid
          case '○' => R.drawable.dot
        }
        remoteViews.setImageViewResource(r, image)
      }

      val intent = new Intent(this, classOf[MainActivity])
      intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
      intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
      intent.putExtra(MainActivity.initCardIndex, cardIndex)
      // NOTE(timgreen): set some dummy value here, so updated extra will not be ignored.
      // see: http://stackoverflow.com/questions/3168484/pendingintent-works-correctly-for-the-first-notification-but-incorrectly-for-the
      intent.setAction(Util.currentTimeInMs.toString)
      Util.debug(s"Widget ${AppWidgetManager.EXTRA_APPWIDGET_ID} $appWidgetId -> ${MainActivity.initCardIndex} $cardIndex")
      val pendingIntent = PendingIntent.getActivity(this, appWidgetId, intent, 0)
      remoteViews.setOnClickPendingIntent(R.id.widgetContainer, pendingIntent)

      appWidgetManager.updateAppWidget(appWidgetId, remoteViews)
    }

    getContentResolver.delete(OpalProvider.Uris.widgets, null, appWidgetIds.map(_.toString).toArray)
    (Usage.numOfWidgets() = appWidgetIds.size)(this)
  }

  private def getBalance(cardIndex: Int, cards: List[CardDetails]) = {
    val (balance, balanceSmall) =
      cards.lift(cardIndex) map { cardDetails =>
        val balance = cardDetails.cardBalance + cardDetails.svPending
        (
          (balance / 100).toString,
          f".${(balance % 100)}%02d"
        )
      } getOrElse ("0" -> ".00")

    s"$$$balance$balanceSmall"
  }

  private def calcBalanceTextSize(balance: String): Float = {
    val width = OpalWidgetUpdateService.maxLabelTextWidthInDp
    val maxTextSize = 20f
    if (Util.getTextWidth(balance, maxTextSize) <= width) {
      maxTextSize
    } else {
      @tailrec
      def findBestTextSize(min: Float, max: Float): Float = {
        if (max - min < 0.01) {
          min
        } else {
          val mid = (min + max) / 2
          val (newMin, newMax) = if (Util.getTextWidth(balance, mid) <= width) {
            (mid, max)
          } else {
            (min, mid)
          }
          findBestTextSize(newMin, newMax)
        }
      }

      findBestTextSize(0, maxTextSize)
    }
  }

  private def sync() {
    Util.debug("do widget update triggered sync")
    AccountUtil.getAccount(this) foreach { account =>
      ContentResolver.cancelSync(account, OpalProvider.AUTHORITY)
      val extras = new Bundle()
      extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
      extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
      extras.putBoolean(SyncAdapter.isManualSyncKey, false)
      ContentResolver.requestSync(account, OpalProvider.AUTHORITY, extras)
    }
  }
}
