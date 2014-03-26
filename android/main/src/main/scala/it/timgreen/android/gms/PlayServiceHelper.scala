package it.timgreen.android.gms

import android.app.Activity
import android.content.pm.PackageManager

import com.google.android.gms.R
import com.google.android.gms.common.GooglePlayServicesUtil

object PlayServiceHelper {

  def check(activity: Activity) {
    val r = GooglePlayServicesUtil.isGooglePlayServicesAvailable(activity)
    if (r != 0) {
      GooglePlayServicesUtil.getErrorDialog(r, activity, 0).show
    }
  }

  def version(activity: Activity): Int = {
    try {
      activity.getPackageManager().getPackageInfo("com.google.android.gms", 0).versionCode
    } catch {
      case t: PackageManager.NameNotFoundException =>
        -1
    }
  }

  def versionOk(activity: Activity): Boolean = {
    version(activity) >= activity.getResources.getInteger(R.integer.google_play_services_version)
  }
}
