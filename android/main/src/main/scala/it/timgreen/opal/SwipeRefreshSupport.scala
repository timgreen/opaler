package it.timgreen.opal

import android.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout

import rx.lang.scala.Subscription

import it.timgreen.opal.AnalyticsSupport._

trait SwipeRefreshSupport extends Fragment {
  import it.timgreen.opal.Bus.isSyncingDistinct
  import it.timgreen.opal.Bus.isSyncing
  import it.timgreen.opal.Bus.syncTrigger

  var swipeRefreshLayout: List[SwipeRefreshLayout]
  var isSyncingSubscription: Subscription = _

  def initSwipeOptions() {
    setAppearance
    swipeRefreshLayout foreach {
      _.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
        def onRefresh {
          Util.debug(s"swipe refresh")
          trackEvent("UI", "pullToRefresh", Some(this.getClass.getSimpleName))(getActivity)
          isSyncing.onNext(true)
          syncTrigger.onNext(0)
        }
      })
    }
  }

  override def onResume() {
    super.onResume
    isSyncingSubscription = isSyncingDistinct subscribe { syncing =>
      if (syncing) {
        onRefreshStart
      } else {
        onRefreshEnd
      }
    }
  }

  override def onPause() {
    isSyncingSubscription.unsubscribe
    super.onPause
  }

  private def setAppearance() {
    swipeRefreshLayout foreach {
      _.setColorSchemeResources(
        android.R.color.holo_blue_bright,
        android.R.color.holo_green_light,
        android.R.color.holo_orange_light,
        android.R.color.holo_red_light
      )
    }
  }

  private def onRefreshStart() {
    swipeRefreshLayout foreach { srl =>
      srl.setRefreshing(true)
      srl.setEnabled(false)
    }
  }

  private def onRefreshEnd() {
    swipeRefreshLayout foreach { srl =>
      srl.setRefreshing(false)
      srl.setEnabled(true)
    }
  }
}
