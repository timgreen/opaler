package it.timgreen.opal

import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import rx.android.schedulers.AndroidSchedulers
import rx.lang.scala.JavaConversions._
import rx.lang.scala.Observable

import it.timgreen.android.rx.RxFragment
import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.rxdata.BackgroundThread
import it.timgreen.opal.rxdata.OverviewData
import it.timgreen.opal.sync.SyncStatus

class OverviewFragment extends RxFragment with SwipeRefreshSupport with SnapshotAware {

  import rxdata.RxCards.currentCard

  var rootView: View = _
  var swipeRefreshLayout: List[SwipeRefreshLayout] = Nil

  object ui {
    lazy val balance: TextView = get(R.id.balance)
    lazy val balanceSmall: TextView = get(R.id.balance_small)
    lazy val lastSuccessfulSync: TextView = get(R.id.last_successful_sync)
    lazy val balanceIcon: TextView = get(R.id.balance_icon)
    lazy val todaySpending: TextView = get(R.id.today_spending)
    lazy val thisWeekSpending: TextView = get(R.id.this_week_spending)
    lazy val lastWeekSpending: TextView = get(R.id.last_week_spending)
    def get[T](id: Int): T = {
      rootView.findViewById(id).asInstanceOf[T]
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
                            savedInstanceState: Bundle): View = {
    this.rootView = inflater.inflate(R.layout.fragment_overview, container, false)
    this.rootView
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    swipeRefreshLayout = ui.get[SwipeRefreshLayout](R.id.swipe_container) :: Nil
    initSwipeOptions

    val rxBalance: Observable[DataStatus[(String, String)]] = currentCard
      .subscribeOn(BackgroundThread.scheduler)
      .observeOn(BackgroundThread.scheduler)
      .map { cardData =>
        cardData map { card =>
          (card.cardBalance / 100).toString ->
          f".${(card.cardBalance % 100)}%02d"
        }
      }
    rxBalance.bindToLifecycle
      .subscribeOn(BackgroundThread.scheduler)
      .observeOn(AndroidSchedulers.mainThread)
      .subscribe {b => renderBalance(b)}
    rxdata.RxTransactions.overview.bindToLifecycle
      .subscribeOn(BackgroundThread.scheduler)
      .observeOn(AndroidSchedulers.mainThread)
      .subscribe { s => renderSpending(s) }
  }

  private def renderBalance(balanceData: DataStatus[(String, String)]) {
    val (balance, balanceSmall) = balanceData getOrElse ("-" -> ".--")

    ui.balance.setText(balance)
    ui.balanceSmall.setText(balanceSmall)
    ui.lastSuccessfulSync.setText("Last Sync " + SyncStatus.getLastSuccessfulSyncTime(getActivity))

    val size = if (balance.length <= 2) {
      (60, 150)
    } else {
      (50, 140)
    }
    ui.balance.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size._2)
    ui.balanceIcon.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size._1)
    ui.balanceSmall.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size._1)
  }

  private def renderSpending(data: DataStatus[OverviewData]) {
    // TODO(timgreen): handle loading/nodata
    ui.todaySpending.setText(
      CardTransaction.formatMoney(data.map(_.today) getOrElse None, "--")
    )
    ui.thisWeekSpending.setText(
      CardTransaction.formatMoney(data.map(_.thisWeek) getOrElse None, "--")
    )
    ui.lastWeekSpending.setText(
      CardTransaction.formatMoney(data.map(_.lastWeek) getOrElse None, "--")
    )

    val balls = Util.getBalls(data.map(_.maxJourneyNumber) getOrElse 0).replaceAll("\\s", "")
    List(
      R.id.ball0,
      R.id.ball1,
      R.id.ball2,
      R.id.ball3,
      R.id.ball4,
      R.id.ball5,
      R.id.ball6,
      R.id.ball7
    ).zipWithIndex foreach { case (r, i) =>
      val image = (balls(i), PrefUtil.prefs(getActivity).getString("theme", "dark")) match {
        case ('●', "dark") => R.drawable.dot_solid
        case ('○', "dark") => R.drawable.dot
        case ('●', "light") => R.drawable.dot_solid_light
        case ('○', "light") | _ => R.drawable.dot_light
      }
      ui.get[ImageView](r).setImageResource(image)
    }
  }

  override def preSnapshot() {
    List(
      ui.balance,
      ui.balanceSmall,
      ui.todaySpending,
      ui.thisWeekSpending,
      ui.lastWeekSpending
    ) foreach { textView =>
      textView.setText(textView.getText.toString.replaceAll("[0-9]", "0"))
    }
  }
}
