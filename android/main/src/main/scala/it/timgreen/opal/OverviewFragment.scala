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

import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.provider.CardsCache
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.sync.SyncStatus

class OverviewFragment extends Fragment with SwipeRefreshSupport
  with LoaderManager.LoaderCallbacks[OverviewData] with SnapshotAware {

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
  }

  private val CARDS_LOADER_ID = -1
  private val cardsLoaderCallbacks = new LoaderManager.LoaderCallbacks[Cursor]() {
    override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
      if (id == CARDS_LOADER_ID) {
        new CursorLoader(getActivity, OpalProvider.Uris.cards, null, null, null, null)
      } else {
        null
      }
    }

    override def onLoadFinished(loader: Loader[Cursor], cursor: Cursor) {
      rootView foreach { rv =>
        val (balance, balanceSmall) =
          currentCardIndex() flatMap { cardIndex =>
            if (cursor != null && cursor.moveToPosition(cardIndex)) {
              val balance = cursor.getInt(cursor.getColumnIndex(CardsCache.Columns.cardBalance)) +
                cursor.getInt(cursor.getColumnIndex(CardsCache.Columns.svPending))
              Some(
                (balance / 100).toString ->
                  f".${(balance % 100)}%02d"
              )
            } else {
              None
            }
          } getOrElse ("0" -> ".00")
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

    override def onLoaderReset(loader: Loader[Cursor]) {
      rootView foreach { rv =>
        rv.findViewById(R.id.balance).asInstanceOf[TextView].setText("-")
        rv.findViewById(R.id.balance_small).asInstanceOf[TextView].setText(".--")
      }
    }
  }

  private def refresh() {
    currentCardIndex() foreach { cardIndex =>
      getLoaderManager.restartLoader(CARDS_LOADER_ID, null, cardsLoaderCallbacks)
      getLoaderManager.restartLoader(cardIndex, null, this)
    }
  }

  override def onCreateLoader(cardIndex: Int, args: Bundle): Loader[OverviewData] = {
    Util.debug(s"overview, create loader $cardIndex")
    new OverviewLoader(getActivity, cardIndex)
  }
  override def onLoadFinished(loader: Loader[OverviewData], data: OverviewData) {
    Util.debug(s"overview, load finished " + data)
    if (currentCardIndex() == Some(data.cardIndex)) {
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
    } else {
      Util.debug(s"overview($currentCardIndex), ignore loader result(${data.cardIndex})")
    }
  }
  override def onLoaderReset(loader: Loader[OverviewData]) {
    Util.debug("overview, load reset")
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
    currentCardIndex.on(tag = this) { _ => refresh }
    fragmentRefreshTrigger.bindToLifecycle subscribe { _ => refresh }
  }

  override def onStop() {
    currentCardIndex.removeByTag(this)
    super.onStop
  }
}

class OverviewLoader(context: Activity, cardIndex: Int) extends AsyncTaskLoader[OverviewData](context) {
  override def loadInBackground: OverviewData = {
    Util.debug(s"overview, start loader data $cardIndex")
    val loaderStartTime = Util.currentTimeInMs

    val overviewData = Overview.getOverviewData(context, cardIndex)

    val loadingTime = Util.currentTimeInMs - loaderStartTime
    trackTiming(
      "UI",
      loadingTime,
      Some("Loading"),
      Some("OverviewView")
    )(context)
    Util.debug(s"overview, finish loader data $cardIndex in ${loadingTime / 1000.0}")

    overviewData
  }

  override protected def onStartLoading {
    forceLoad
  }
}
