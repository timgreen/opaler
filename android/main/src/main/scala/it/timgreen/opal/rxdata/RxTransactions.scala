package it.timgreen.opal.rxdata

import android.content.Context

import scala.collection.mutable

import rx.lang.scala.Subscription
import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.opal.DataStatus
import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.TransactionTable

// TODO(timgreen): set scheduler, use same thread for everything here.
object RxTransactions {

  val transactions = BehaviorSubject[DataStatus[List[CardTransaction]]](DataStatus.dataLoading)

  import it.timgreen.opal.Bus.currentCardIndex

  currentCardIndex subscribe { onCardChange _ }

  // TODO(timgreen): better method name
  var context: Context = _
  def reload(implicit context: Context) {
    this.context = context;
    // TODO(timgreen): reload
  }

  private var subscription: Option[Subscription] = _
  private def onCardChange(cardIndex: Int) {
    subscription foreach { _.unsubscribe }
    transactions.onNext(DataStatus.dataLoading)
    subscription = Some(getTransactionsFor(cardIndex).list subscribe transactions)
  }

  private val watchers = mutable.Map[Int, TransactionWatcher]()
  private def getTransactionsFor(cardIndex: Int) =
    watchers.getOrElseUpdate(cardIndex, new TransactionWatcher(cardIndex, context))
}

class TransactionWatcher(cardIndex: Int, context: Context) {

  val list = BehaviorSubject[DataStatus[List[CardTransaction]]](DataStatus.dataLoading)

  private def loadData() {
    val cursor = context.getContentResolver.query(OpalProvider.Uris.activities(cardIndex), null, null, null, null)
    list.onNext(DataStatus(TransactionTable.convert(cursor)))
  }

  // TODO(timgreen): use scheduler
  loadData
  // TODO(timgreen): reload
}
