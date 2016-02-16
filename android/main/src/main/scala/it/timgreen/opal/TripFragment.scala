package it.timgreen.opal

import android.content.Context
import android.os.Bundle
import android.support.v4.widget.SwipeRefreshLayout
import android.text.Html
import android.text.format.Time
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.SectionIndexer
import android.widget.TextView

import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter
import se.emilsjolander.stickylistheaders.StickyListHeadersListView

import it.timgreen.android.conversion.View._
import it.timgreen.android.rx.RxFragment
import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.api.Model
import it.timgreen.opal.api.TransactionDetails
import it.timgreen.opal.rxdata.TransactionViewData

import scala.collection.mutable

class TripFragment extends RxFragment with SwipeRefreshSupport with SnapshotAware {

  var adapter: TransactionListAdapter = _
  var rootView: View = _
  var swipeRefreshLayout: List[SwipeRefreshLayout] = Nil

  object ui {
    lazy val listView: StickyListHeadersListView = get(android.R.id.list)
    def get[T](id: Int): T = {
      rootView.findViewById(id).asInstanceOf[T]
    }
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
                            savedInstanceState: Bundle): View = {

    this.rootView = inflater.inflate(R.layout.fragment_trip, container, false)
    adapter = new TransactionListAdapter(getActivity)
    ui.listView.setAdapter(adapter)
    updateEmptyView

    this.rootView
  }

  override def onViewCreated(view: View, savedInstanceState: Bundle) {
    super.onViewCreated(view, savedInstanceState)
    swipeRefreshLayout =
      ui.get[SwipeRefreshLayout](R.id.swipe_container) ::
      ui.get[SwipeRefreshLayout](android.R.id.empty) ::
      Nil
    initSwipeOptions
  }

  override def preSnapshot() {
    val listView = ui.listView.getWrappedList
    0 to listView.getChildCount foreach { i =>
      val v = listView.getChildAt(i)
      Option(v).map(_.findViewById(R.id.amount)) foreach { vv =>
        val tv = vv.asInstanceOf[TextView]
        tv.setText(tv.getText.toString.replaceAll("[0-9]", "0"))
      }
      Option(v).map(_.findViewById(R.id.details)) foreach { vv =>
        val tv = vv.asInstanceOf[TextView]
        tv.setTextColor(0x00000000)
      }
    }
  }

  override def onStart() {
    super.onStart
    rxdata.RxTransactions.transactionViewDatas.bindToLifecycle subscribe { d =>
      renderList(d)
    }
  }

  private def updateEmptyView() {
    val emptyView: View = ui.get(android.R.id.empty)
    if (adapter.isEmpty) {
      emptyView.setVisibility(View.VISIBLE)
    } else {
      emptyView.setVisibility(View.GONE)
    }
  }

  private def renderList(data: DataStatus[List[TransactionViewData]]) {
    adapter.setNotifyOnChange(false)
    adapter.clear
    adapter.addAll(data.getOrElse(List()): _*)
    adapter.setNotifyOnChange(true)
    adapter.notifyDataSetChanged

    updateEmptyView
  }

}

class TransactionListAdapter(context: Context)
  extends ArrayAdapter[TransactionViewData](context, R.layout.row_item_transaction) // with SectionIndexer
  with StickyListHeadersAdapter {

  private var headerData = Map[Int, WeekGroup]()

  private val (textColorPrimary, tripBackground, tripAlternateBackground) = obtainColors

  private def obtainColors = {
    val typedArray = context.obtainStyledAttributes(Array(
      android.R.attr.textColorPrimary,
      R.attr.tripBackground,
      R.attr.tripAlternateBackground
    ))
    val textColorPrimary = typedArray.getColor(0, 0)
    val tripBackground = typedArray.getColor(1, 0)
    val tripAlternateBackground = typedArray.getColor(2, 0)
    typedArray.recycle

    (textColorPrimary, tripBackground, tripAlternateBackground)
  }

  override def addAll(items: TransactionViewData*) {
    super.addAll(items: _*)
    headerData = generateHeaderData(items)
  }

  private def generateHeaderData(items: Seq[TransactionViewData]): Map[Int, WeekGroup] = {
    val weekGroups = mutable.Map[Int, WeekGroup]()
    var currentWeek = WeekGroup(-1)
    items foreach { data =>
      val cardTransaction = data.trip
      if (cardTransaction.julianWeekNumber != currentWeek.julianWeekNumber) {
        currentWeek = WeekGroup(cardTransaction.julianWeekNumber)
        weekGroups(cardTransaction.julianWeekNumber) = currentWeek
      }
      if (currentWeek.numOfJourney < cardTransaction.journeyNumber.getOrElse(0)) {
        currentWeek.numOfJourney = cardTransaction.journeyNumber.getOrElse(0)
      }
      if (cardTransaction.model.isTrip) {
        currentWeek.numOfTrip += 1
      }
      if (cardTransaction.model != Model.TopUp &&
          cardTransaction.model != Model.AutoTopUp &&
          cardTransaction.model != Model.Adjustment &&
          cardTransaction.model != Model.BalanceTransfer) {
        cardTransaction.amount foreach { currentWeek.amount += _ }
      }
    }

    weekGroups.toMap
  }

  override def getHeaderId(position: Int) = getItem(position).trip.julianWeekNumber

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    createNormalItem(convertView, parent, getItem(position), position)
  }

  override def getHeaderView(position: Int, convertView: View, parent: ViewGroup): View = {
    val data = headerData(getItem(position).trip.julianWeekNumber)
    createWeekGroup(convertView, parent, data)
  }

  private def createWeekGroup(convertView: View, parent: ViewGroup, data: WeekGroup): View = {
    val weekGroupView = if (convertView != null) {
      convertView
    } else {
      val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      inflater.inflate(R.layout.row_item_week_group, parent, false)
    }

    val startDay = Time.getJulianMondayFromWeeksSinceEpoch(data.julianWeekNumber)
    val startDayTime = new Time(CardTransaction.timezone)
    startDayTime.setJulianDay(startDay)
    val endDayTime = new Time(CardTransaction.timezone)
    endDayTime.setJulianDay(startDay + 6)
    weekGroupView.findViewById(R.id.week).asInstanceOf[TextView].setText {
      val start = Util.format(startDayTime, "yyyy LLL dd").split(' ')
      val end = Util.format(endDayTime, "yyyy LLL dd").split(' ')
      if (start(0) != end(0)) {
        s"${start.mkString(" ")} - ${end.mkString(" ")}"
      } else if (start(1) != end(1)) {
        s"${start.mkString(" ")} - ${end.tail.mkString(" ")}"
      } else {
        s"${start.mkString(" ")} - ${end.last}"
      }

    }
    weekGroupView.findViewById(R.id.amount).asInstanceOf[TextView].setText(CardTransaction.formatMoney(Option(data.amount)))
    val journeyNumIcons = Array(
      " ",
      "①",
      "②",
      "③",
      "④",
      "⑤",
      "⑥",
      "⑦",
      "⑧",
      "⑨",
      "⑩",
      "⑪",
      "⑫",
      "⑬",
      "⑭",
      "⑮",
      "⑯",
      "⑰",
      "⑱",
      "⑲",
      "⑳",
      "⑳+"
    )
    weekGroupView.findViewById(R.id.journey_no).asInstanceOf[TextView].setText(journeyNumIcons.lift(data.numOfJourney).getOrElse(journeyNumIcons.last))

    weekGroupView
  }

  private def createNormalItem(convertView: View, parent: ViewGroup, data: TransactionViewData, position: Int): View = {
    val rowView = if (convertView != null) {
      convertView
    } else {
      val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      inflater.inflate(R.layout.row_item_transaction, parent, false)
    }

    // Define width
    val density = getContext.getResources.getDisplayMetrics.density
    val paddingW = 8 * 2
    val iconW = 48
    val colFareAppliedAndAmountW = 80
    val colDateTimeW = 90

    val row0Spec = GridLayout.spec(0)
    val row1Spec = GridLayout.spec(1)
    val col0Spec = GridLayout.spec(0)
    val row0 = new GridLayout.LayoutParams(row0Spec, col0Spec)
    val row1 = new GridLayout.LayoutParams(row1Spec, col0Spec)
    row0.width = (colDateTimeW * density).toInt
    row1.width = (colDateTimeW * density).toInt
    row0.height = LayoutParams.WRAP_CONTENT
    row1.height = LayoutParams.WRAP_CONTENT
    if (position >= 1 &&
        getItem(position - 1).trip.weekDay == data.trip.weekDay &&
        getItem(position - 1).trip.julianWeekNumber == data.trip.julianWeekNumber) {
      // Same day as previous row, ignore day here
      rowView.findViewById(R.id.weekDay).asInstanceOf[TextView].setText("")
      rowView.findViewById(R.id.weekDay).setLayoutParams(row1)
      rowView.findViewById(R.id.time).setLayoutParams(row0)
    } else {
      rowView.findViewById(R.id.weekDay).asInstanceOf[TextView].setText(Util.format(data.trip.datetime, "E, LLL dd"))
      rowView.findViewById(R.id.weekDay).setLayoutParams(row0)
      rowView.findViewById(R.id.time).setLayoutParams(row1)
    }

    rowView.setBackgroundColor(
      if (data.alternateColor) {
        tripAlternateBackground
      } else {
        tripBackground
      }
    )

    rowView.findViewById(R.id.time).asInstanceOf[TextView].setText(
      Util.format(
        data.trip.datetime,
        if (PrefUtil.use24hourFormat(context)) {
          "HH:mm"
        } else {
          "hh:mm aa"
        }
      )
    )
    val amountText = rowView.findViewById(R.id.amount).asInstanceOf[TextView]
    amountText.setText(data.trip.amount.map(a => CardTransaction.formatMoney(Some(a))).getOrElse(""))
    if (data.trip.amount.filter(_ > 0).isDefined) {
      amountText.setTextColor(context.getResources.getColor(R.color.trip_view_positive_amount))
    } else {
      amountText.setTextColor(textColorPrimary)
    }

    val fareApplied = rowView.findViewById(R.id.fareApplied).asInstanceOf[TextView]
    fareApplied.setText(data.trip.fareApplied.toString)
    data.trip.model match {
      case Model.TopUp | Model.AutoTopUp | Model.BalanceTransfer | Model.Adjustment =>
        rowView.findViewById(R.id.model).asInstanceOf[ImageView].setImageResource(R.drawable.model_topup)
      case Model.Train =>
        rowView.findViewById(R.id.model).asInstanceOf[ImageView].setImageResource(R.drawable.model_train)
      case Model.Bus =>
        rowView.findViewById(R.id.model).asInstanceOf[ImageView].setImageResource(R.drawable.model_bus)
      case Model.Ferry =>
        rowView.findViewById(R.id.model).asInstanceOf[ImageView].setImageResource(R.drawable.model_ferry)
      case Model.LightRail =>
        rowView.findViewById(R.id.model).asInstanceOf[ImageView].setImageResource(R.drawable.model_light_rail)
      case _ =>
        rowView.findViewById(R.id.model).asInstanceOf[ImageView].setImageDrawable(null)
    }

    val detailsTextView = rowView.findViewById(R.id.details).asInstanceOf[TextView]
    data.trip.details match {
      case fromTo: TransactionDetails.FromTo =>
        if (fromTo.isNoTapOn && data.trip.amount.map(_ < 0) == Some(true)) {
          detailsTextView.setText(Html.fromHtml(s"<font color='#F2977B'>${fromTo.from}</font> <font color='#${context.getResources.getString(R.color.trip_view_secondary).substring(3)}'>→</font> ${fromTo.to}"))
        } else if (fromTo.isNoTapOff && data.trip.amount.map(_ < 0) == Some(true)) {
          detailsTextView.setText(Html.fromHtml(s"${fromTo.from} <font color='#${context.getResources.getString(R.color.trip_view_secondary).substring(3)}'>→</font> <font color='#F2977B'>${fromTo.to}</font>"))
        } else {
          detailsTextView.setText(Html.fromHtml(s"${fromTo.from} <font color='#${context.getResources.getString(R.color.trip_view_secondary).substring(3)}'>→</font> ${fromTo.to}"))
        }
      case tapOnReversal: TransactionDetails.TapOnReversal =>
        detailsTextView.setText(Html.fromHtml(s"<font color='#${context.getResources.getString(R.color.trip_view_secondary).substring(3)}'>Tap on reversal - </font> ${tapOnReversal.stop}"))
      case details =>
        detailsTextView.setText(details.toString)
    }
    val w = (parent.getWidth / density).toInt - (paddingW + iconW + colFareAppliedAndAmountW + colDateTimeW)
    val rowSpec = GridLayout.spec(0, 2)
    val colSpec = GridLayout.spec(1)
    val params = new GridLayout.LayoutParams(rowSpec, colSpec)
    params.width = (w * density).toInt
    params.height = LayoutParams.WRAP_CONTENT
    detailsTextView.setLayoutParams(params)

    rowView
  }
}

case class WeekGroup(
  julianWeekNumber: Int,
  var numOfJourney: Int = 0,
  var numOfTrip: Int = 0,
  var amount: Double = 0) {
}
