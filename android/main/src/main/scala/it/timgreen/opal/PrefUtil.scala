package it.timgreen.opal

import android.content.Context
import android.preference.PreferenceManager

object PrefUtil {

  @inline
  def prefs(implicit context: Context) =
    PreferenceManager.getDefaultSharedPreferences(context)

  def syncOnStart(implicit context: Context) =
    prefs.getBoolean("sync_on_start", true)

  def autoSyncOnWifiOnly(implicit context: Context) =
    prefs.getBoolean("auto_sync_on_wifi_only", true)

  def use24hourFormat(implicit context: Context) =
    prefs.getBoolean("use_24_hour_format", true)

  def isAdDisabled(implicit context: Context) =
    prefs.getBoolean("disable_ads", false)

  // Flags
  def enableFakeData(implicit context: Context) = {
    BuildConfig.ENABLE_FAKE_DATA && prefs.getBoolean("show_fake_data", false)
  }
}
