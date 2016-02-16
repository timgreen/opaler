package it.timgreen.opal.rxdata

import android.content.Context
import android.text.format.Time

import scala.collection.mutable

import rx.lang.scala.Observable
import rx.lang.scala.Subscription
import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.DataStatus
import it.timgreen.opal.Util
import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.TransactionTable

// TODO(timgreen): set scheduler, use same thread for everything here.
object RxTransactions {

  private val transactions = BehaviorSubject[DataStatus[List[CardTransaction]]](DataStatus.dataLoading)
  val transactionViewDatas: Observable[DataStatus[List[TransactionViewData]]] = transactions map {
    _ map processTransaction
  }
  val overview: Observable[DataStatus[OverviewData]] = transactions map {
    _ map processOverview
  }

  // TODO(timgreen): better not to hold a reference at all.
  private val contextSubject = BehaviorSubject[Context]();

  // TODO(timgreen): better method name
  def reload(implicit context: Context) {
    contextSubject.onNext(context);
    // TODO(timgreen): reload
  }

  private var subscription: Option[Subscription] = None
  private def onCardChange(cardIndex: Int) {
    subscription foreach { _.unsubscribe }
    transactions.onNext(DataStatus.dataLoading)
    subscription = Some(getTransactionsFor(cardIndex).list subscribe transactions)
  }

  private val watchers = mutable.Map[Int, TransactionWatcher]()
  private def getTransactionsFor(cardIndex: Int) =
    watchers.getOrElseUpdate(cardIndex, new TransactionWatcher(cardIndex, contextSubject))

  private def processTransaction(transactions: List[CardTransaction]): List[TransactionViewData] = {
    var lastColor = false
    var lastJourneyNumber: Option[Int] = None

    transactions.reverse map { cardTransaction =>
      val alternateColor = if (lastJourneyNumber == cardTransaction.journeyNumber || cardTransaction.journeyNumber == None) {
        lastColor
      } else {
        !lastColor
      }

      if (cardTransaction.journeyNumber != None) {
        lastJourneyNumber = cardTransaction.journeyNumber
      }
      lastColor = alternateColor

      TransactionViewData(cardTransaction, alternateColor)
    } reverse
  }

  private def processOverview(transactions: List[CardTransaction]): OverviewData = {
    val time = new Time(CardTransaction.timezone)
    time.setToNow
    val thisWeek = Util.getJulianWeekNumber(time)

    def calcSpend(filter: CardTransaction => Boolean): Option[Double] = {
      val l = transactions.filter(filter).filter(i => i.model.isTrip && i.amount.isDefined).map(_.amount.get)
      if (l.isEmpty) {
        None
      } else {
        Some(l.sum)
      }
    }

    OverviewData(
      today = calcSpend(t => t.julianWeekNumber == thisWeek && t.weekDay == time.weekDay),
      thisWeek = calcSpend(_.julianWeekNumber == thisWeek),
      lastWeek = calcSpend(_.julianWeekNumber == thisWeek - 1),
      lastTrip = transactions find { t => t.model.isTrip && t.julianWeekNumber == thisWeek },
      maxJourneyNumber = (0 :: transactions.filter(_.julianWeekNumber == thisWeek).map(_.journeyNumber.getOrElse(0))).max
    )
  }

  // init
  RxCards.currentCardIndex subscribe { onCardChange _ }
}

private[rxdata] class TransactionWatcher(cardIndex: Int, context: Observable[Context]) {

  val list = BehaviorSubject[DataStatus[List[CardTransaction]]](DataStatus.dataLoading)

  def loadData(context: Context) {
    val cursor = context.getContentResolver.query(OpalProvider.Uris.activities(cardIndex), null, null, null, null)
    list.onNext(DataStatus(TransactionTable.convert(cursor)))
  }

  // TODO(timgreen): use scheduler
  context subscribe { loadData _ }
  RxSync.dataReloadTrigger.combineLatest(RxCards.currentCardIndex).filter(_._2 == cardIndex) subscribe { d =>
    loadData(d._1)
  }
}

case class TransactionViewData(
  trip: CardTransaction,
  alternateColor: Boolean
)

case class OverviewData(
  today: Option[Double],
  thisWeek: Option[Double],
  lastWeek: Option[Double],
  lastTrip: Option[CardTransaction],
  maxJourneyNumber: Int
)
