package it.timgreen.opal

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

import it.timgreen.opal.AnalyticsSupport._

object Ads {
  @inline
  private def lastBottomBannerAdClickKey = "last_bottom_banner_ad_click"

  def hasClickedBottomBannerAdBefore(implicit context: Context) =
    PrefUtil.prefs.contains(lastBottomBannerAdClickKey)

  def markAdClick(unit: String)(implicit context: Context) {
    trackEvent("Ad", "click", Some(unit))
    PrefUtil.prefs.edit.putLong(lastBottomBannerAdClickKey, Util.currentTimeInMs).apply
  }

  trait BottomBanner { activity: Activity =>
    @inline
    private def AD_UNIT_ID = "ca-app-pub-5460273749262325/6568884891"

    private var adView: Option[AdView] = None

    @inline
    private[BottomBanner] def setupAd() {
      val adView = new AdView(activity)
      this.adView = Some(adView)
      adView.setAdSize(AdSize.SMART_BANNER)
      adView.setAdUnitId(AD_UNIT_ID)
      adView.setVisibility(View.GONE)
      adView.setAdListener(new AdListener() {
        override def onAdLoaded() {
          adView.setVisibility(View.VISIBLE)
        }
        override def onAdClosed() {}
        override def onAdFailedToLoad(errorCode: Int) {}
        override def onAdLeftApplication() {}
        override def onAdOpened() {
          Util.debug(s"click banner ad")
          markAdClick("banner")(activity)
        }
      })

      val layout = activity.findViewById(R.id.main).asInstanceOf[LinearLayout]
      layout.addView(adView)

      val adRequest = new AdRequest.Builder()
        .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
        .build

      adView.loadAd(adRequest)
    }

    private[BottomBanner] def removeAd() {
      val layout = activity.findViewById(R.id.main).asInstanceOf[LinearLayout]
      adView foreach { v =>
        layout.removeView(v)
        v.destroy
        adView = None
      }
    }

    override def onResume() {
      activity.onResume
      if (PrefUtil.isAdDisabled(this)) {
        adView foreach { _ =>
          removeAd
        }
      } else {
        adView match {
          case Some(v) => v.resume
          case None => setupAd
        }
      }
    }

    override def onPause() {
      activity.onPause
      adView foreach { _.pause }
    }

    override def onDestroy() {
      adView foreach { _.destroy }
      activity.onDestroy
    }
  }
}
