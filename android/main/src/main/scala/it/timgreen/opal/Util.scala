package it.timgreen.opal

import android.text.format.Time
import android.util.Log

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

import it.timgreen.opal.api.CardTransaction

object Util {
  @inline
  def currentTimeInMs = new Date().getTime

  // NOTE(timgreen): 0 AM - 4 AM is count as previous day.
  def getJulianWeekNumber(time: Time): Int = Time.getWeeksSinceEpochFromJulianDay(
    Time.getJulianDay(time.toMillis(false) - TimeUnit.HOURS.toMillis(4) + 1, time.gmtoff),
    Time.MONDAY
  )

  def format(time: Time, template: String): String = {
    val formatter = new SimpleDateFormat(template, Locale.US)
    formatter.setTimeZone(TimeZone.getTimeZone(CardTransaction.timezone))
    formatter.format(new Date(time.toMillis(false)))
  }

  def getBalls(maxJourneyNumber: Int) = {
    val fullCircles = Math.min(8, maxJourneyNumber)
    val emptyCircles = 8 - fullCircles
    val balls = List.fill(fullCircles)("●") ::: List.fill(emptyCircles)("○")
    val (line1, line2) = balls.splitAt(4)
    line1.mkString(" ") + "\n" + line2.mkString(" ")
}

  private def debugTag = "OPAL_DEBUG"
  def debug(msg: => String) {
    if (BuildConfig.DEBUG) {
      Log.d(debugTag, msg)
    }
  }

  def debug(msg: => String, t: => Throwable) {
    if (BuildConfig.DEBUG) {
      Log.d(debugTag, msg, t)
    }
  }
}
