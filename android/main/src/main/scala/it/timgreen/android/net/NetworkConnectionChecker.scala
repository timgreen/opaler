package it.timgreen.android.net

import android.content.Context
import android.net.ConnectivityManager

object NetworkConnectionChecker {

  def hasConnection(context: Context): Boolean = hasConnectionWithType(context)
  def hasWifiConnection(context: Context): Boolean =
    hasConnectionWithType(context, ConnectivityManager.TYPE_WIFI)

  def hasConnectionWithType(context: Context, netTypes: Int*): Boolean = {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager]
    cm.getAllNetworkInfo.exists { ni =>
      if (ni.isConnected) {
        if (netTypes.isEmpty) {
          true
        } else {
          netTypes.contains(ni.getType)
        }
      } else {
        false
      }
    }
  }
}
