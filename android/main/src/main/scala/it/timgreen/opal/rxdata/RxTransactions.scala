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
  // TODO(timgreen): better not to hold a reference at all.
  private val contextSubject = BehaviorSubject[Context]();

  import it.timgreen.opal.Bus.currentCardIndex

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

  // init
  currentCardIndex subscribe { onCardChange _ }
}

class TransactionWatcher(cardIndex: Int, context: Observable[Context]) {

  val list = BehaviorSubject[DataStatus[List[CardTransaction]]](DataStatus.dataLoading)

  def loadData(context: Context) {
    val cursor = context.getContentResolver.query(OpalProvider.Uris.activities(cardIndex), null, null, null, null)
    list.onNext(DataStatus(TransactionTable.convert(cursor)))
  }

  // TODO(timgreen): use scheduler
  context subscribe { loadData _ }
  // TODO(timgreen): reload
}
