package it.timgreen.opal

import android.support.v4.widget.SwipeRefreshLayout

import it.timgreen.android.rx.RxFragment
import it.timgreen.opal.AnalyticsSupport._

trait SwipeRefreshSupport extends RxFragment {
  import rxdata.RxSync.isSyncing
  import rxdata.RxSync.syncTrigger

  var swipeRefreshLayout: List[SwipeRefreshLayout]

  def initSwipeOptions() {
    setAppearance
    swipeRefreshLayout foreach {
      _.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
        def onRefresh {
          Util.debug(s"swipe refresh")
          trackEvent("UI", "pullToRefresh", Some(this.getClass.getSimpleName))(getActivity)
          rxdata.RxSync.markIsSyncing(true)
          syncTrigger.onNext(0)
        }
      })
    }
  }

  override def onResume() {
    super.onResume
    isSyncing.bindToLifecycle subscribe { syncing =>
      if (syncing) {
        onRefreshStart
      } else {
        onRefreshEnd
      }
    }
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
