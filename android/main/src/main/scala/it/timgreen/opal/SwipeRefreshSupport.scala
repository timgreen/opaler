package it.timgreen.opal

import android.app.Fragment
import android.support.v4.widget.SwipeRefreshLayout

import it.timgreen.opal.AnalyticsSupport._

trait SwipeRefreshSupport { self: Fragment =>

  var swipeRefreshLayout: List[SwipeRefreshLayout]
  var isRefreshing = false

  def initSwipeOptions() {
    setAppearance
    swipeRefreshLayout foreach {
      _.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
        def onRefresh {
          Util.debug(s"swipe refresh")
          trackEvent("UI", "pullToRefresh", Some(self.getClass.getSimpleName))(getActivity)
          getActivity.asInstanceOf[MainActivity].startSync
        }
      })
    }
  }

  def setAppearance() {
    swipeRefreshLayout foreach {
      _.setColorSchemeResources(
        android.R.color.holo_blue_bright,
        android.R.color.holo_green_light,
        android.R.color.holo_orange_light,
        android.R.color.holo_red_light
      )
    }
  }

  def syncRefreshStatus() {
    if (isRefreshing) {
      onRefreshStart
    } else {
      onRefreshEnd
    }
  }

  def onRefreshStart() {
    isRefreshing = true
    swipeRefreshLayout foreach { srl =>
      srl.setRefreshing(true)
      srl.setEnabled(false)
    }
  }

  def onRefreshEnd() {
    isRefreshing = false
    swipeRefreshLayout foreach { srl =>
      srl.setRefreshing(false)
      srl.setEnabled(true)
    }
  }
}
