package it.timgreen.opal

import android.content.Context

import com.google.android.gms.analytics.Tracker

import it.timgreen.android.gms.AnalyticsSupportBase
import it.timgreen.android.security.Hash
import it.timgreen.opal.account.AccountUtil

object AnalyticsSupport extends AnalyticsSupportBase {
  override protected def getTracker(implicit context: Context): Tracker =
    getTracker(R.xml.tracker)
  override protected val isDebug = BuildConfig.DEBUG
  override protected def getUserId(implicit context: Context) = {
    AccountUtil.getAccount.map(_.name) map Hash.sha256
  }

  @inline def dimensionAd                   = 1
  @inline def dimensionDbVersion            = 2
  @inline def dimensionPlayServiceVersion   = 3
  @inline def dimensionPlayServiceVersionOK = 4
  @inline def dimensionAdClicked            = 5

  @inline def metricNumberOfWidgets         = 1
  @inline def metricNumOfTransaction        = 2
  @inline def metricNumOfCards              = 3
}
