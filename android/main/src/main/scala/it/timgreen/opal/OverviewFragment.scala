package it.timgreen.opal

import android.app.Activity
import android.app.Fragment
import android.app.LoaderManager
import android.content.AsyncTaskLoader
import android.content.CursorLoader
import android.content.Loader
import android.database.Cursor
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView

import rx.lang.scala.Observable

import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.provider.CardsCache
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.sync.SyncStatus

class OverviewFragment extends Fragment with SwipeRefreshSupport with SnapshotAware {

  import Bus._

  var rootView: Option[View] = None
  var swipeRefreshLayout: List[SwipeRefreshLayout] = Nil

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
                            savedInstanceState: Bundle): View = {
    val rootView = inflater.inflate(R.layout.fragment_overview, container, false)
    this.rootView = Some(rootView)

    rootView
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    swipeRefreshLayout = rootView.map(_.findViewById(R.id.swipe_container).asInstanceOf[SwipeRefreshLayout]).toList
    initSwipeOptions

    val rxBalance: Observable[DataStatus[(String, String)]] = currentCardDetails map { cardData =>
      cardData map { card =>
        (card.cardBalance / 100).toString ->
        f".${(card.cardBalance % 100)}%02d"
      }
    }
    rxBalance.bindToLifecycle subscribe {b => renderBalance(b)}

    // TODO(timgreen):
    val rxOverview = rx.lang.scala.subjects.BehaviorSubject[OverviewData]()
    rxOverview.bindToLifecycle subscribe { s => renderSpending(s) }
  }

  private def renderBalance(balanceData: DataStatus[(String, String)]) {
    val (balance, balanceSmall) = balanceData getOrElse ("-" -> ".--")

    rootView foreach { rv =>
      rv.findViewById(R.id.balance).asInstanceOf[TextView].setText(balance)
      rv.findViewById(R.id.balance_small).asInstanceOf[TextView].setText(balanceSmall)
      rv.findViewById(R.id.last_successful_sync).asInstanceOf[TextView].setText("Last Sync " + SyncStatus.getLastSuccessfulSyncTime(getActivity))

      val size = if (balance.length <= 2) {
        (60, 150)
      } else {
        (50, 140)
      }
      rv.findViewById(R.id.balance).asInstanceOf[TextView].setTextSize(TypedValue.COMPLEX_UNIT_DIP, size._2)
      rv.findViewById(R.id.balance_icon).asInstanceOf[TextView].setTextSize(TypedValue.COMPLEX_UNIT_DIP, size._1)
      rv.findViewById(R.id.balance_small).asInstanceOf[TextView].setTextSize(TypedValue.COMPLEX_UNIT_DIP, size._1)
    }
  }

  private def renderSpending(data: OverviewData) {
    rootView foreach { rv =>
      rv.findViewById(R.id.today_spending).asInstanceOf[TextView].setText(
        CardTransaction.formatMoney(data.today, "--")
      )
      rv.findViewById(R.id.this_week_spending).asInstanceOf[TextView].setText(
        CardTransaction.formatMoney(data.thisWeek, "--")
      )
      rv.findViewById(R.id.last_week_spending).asInstanceOf[TextView].setText(
        CardTransaction.formatMoney(data.lastWeek, "--")
      )

      val balls = Util.getBalls(data.maxJourneyNumber).replaceAll("\\s", "")
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
        rv.findViewById(r).asInstanceOf[ImageView].setImageResource(image)
      }
    }
  }

  override def preSnapshot() {
    rootView foreach { rv =>
      List(
        R.id.balance,
        R.id.balance_small,
        R.id.today_spending,
        R.id.this_week_spending,
        R.id.last_week_spending
      ) foreach { id =>
        val textView = rv.findViewById(id).asInstanceOf[TextView]
        textView.setText(textView.getText.toString.replaceAll("[0-9]", "0"))
      }
    }
  }

  override def onStart() {
    super.onStart
    // TODO(timgreen):
    // fragmentRefreshTrigger.bindToLifecycle subscribe { _ => refresh }
  }
}
