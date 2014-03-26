package it.timgreen.android.gms

import android.content.Context

import com.google.android.gms.analytics.GoogleAnalytics
import com.google.android.gms.analytics.HitBuilders
import com.google.android.gms.analytics.Tracker

import java.util.Date
import scala.collection.mutable

trait AnalyticsSupportBase {

  private var tracker: Tracker = null
  protected def getTracker(res: Int)(implicit context: Context): Tracker = this.synchronized {
    if (tracker == null) {
      val analytics = GoogleAnalytics.getInstance(context)
      if (isDebug) {
        analytics.setDryRun(true)
      }
      tracker = analytics.newTracker(res)
      tracker.enableAdvertisingIdCollection(true)
      getUserId foreach { uid => tracker.set("&uid", uid) }
    }
    tracker
  }

  protected val isDebug = false
  protected def getTracker(implicit context: Context): Tracker
  protected def getUserId(implicit context: Context): Option[String] = None

  private val customDimensions = mutable.Map[Int, String]()
  def setCustomDimensions(customDimensions: (Int, String)*) {
    this.customDimensions ++ customDimensions
  }

  private val customMetrics = mutable.Map[Int, Float]()
  def setCustomMetrics(customMetrics: (Int, Float)*) {
    this.customMetrics ++ customMetrics
  }

  private[AnalyticsSupportBase] def setCustoms(builder: HitBuilders.HitBuilder[_]) {
    customDimensions foreach { case (i, v) =>
      builder.setCustomDimension(i, v)
    }
    customMetrics foreach { case (i, v) =>
      builder.setCustomMetric(i, v)
    }
  }

  def trackView(screen: String)
               (implicit context: Context) {
    val t = getTracker
    t.setScreenName(screen)
    val b = new HitBuilders.AppViewBuilder()
    setCustoms(b)
    t.send(b.build)
  }

  def trackEvent(category: String, action: String, label: Option[String] = None, value: Option[Long] = None)
                (implicit context: Context) {
    val b = new HitBuilders.EventBuilder()
      .setCategory(category)
      .setAction(action)
    setCustoms(b)
    label foreach b.setLabel
    value foreach b.setValue
    getTracker.send(b.build)
  }

  def trackTiming(category: String, interval: Long, variable: Option[String] = None, label: Option[String] = None)
                 (implicit context: Context) {
    val b = new HitBuilders.TimingBuilder()
      .setCategory(category)
      .setValue(interval)
    setCustoms(b)
    variable foreach b.setVariable
    label foreach b.setLabel
    getTracker.send(b.build)
  }

  def trackBlockTiming[R](category: String, variable: Option[String] = None, label: Option[String] = None)
                         (op: => R)
                         (implicit context: Context): R = {
    val startTime = new Date().getTime
    val r = op
    val endTime = new Date().getTime
    trackTiming(category, endTime - startTime, variable, label)
    r
  }

  def reportError(t: Throwable)(implicit context: Context) {
    val description = t.toString
    val b = new HitBuilders.ExceptionBuilder()
      .setDescription("* " + description)
    setCustoms(b)
    getTracker.send(b.build)
  }
}
