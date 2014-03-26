package it.timgreen.opal.provider

import android.content.Context
import android.database.Cursor

import it.timgreen.opal.PrefUtil
import it.timgreen.opal.api.CardDetails

import org.json.JSONArray
import scala.collection.mutable

object CardsCache {
  object Columns {
    val id                          = "_id"
    val active                      = "active"
    val cardBalance                 = "cardBalance"
    val cardBalanceInDollars        = "cardBalanceInDollars"
    val cardNickName                = "cardNickName"
    val cardNumber                  = "cardNumber"
    val cardState                   = "cardState"
    val currentCardBalanceInDollars = "currentCardBalanceInDollars"
    val displayName                 = "displayName"
    val svPending                   = "svPending"
    val svPendingInDollars          = "svPendingInDollars"
    val toBeActivated               = "toBeActivated"

    val tempName                    = "temp_name"
  }

  val cardCacheKey = "card_details_list"

  def getCards(implicit context: Context): List[CardDetails] = {
    val json = getPrefs.getString(cardCacheKey, "[]")
    val cards = CardDetails.parseList(new JSONArray(json))
    if (PrefUtil.enableFakeData) {
      cards map { c =>
        c.copy(cardNumber = "1234567812345678")
      }
    } else {
      cards
    }
  }

  private [provider] def updateCards(cardsString: String)(implicit context: Context) {
    val editor = getPrefs.edit
    editor.putString(cardCacheKey, cardsString)
    editor.commit
  }

  def convert(c: Cursor): List[CardDetails] = {
    val list = mutable.ListBuffer[CardDetails]()
    c.moveToFirst
    while (!c.isAfterLast) {
      list += fromValues(c)
      c.moveToNext
    }
    c.close
    list.toList
  }

  private [provider] def fromValues(c: Cursor) = CardDetails(
    index                       = c.getInt(c.getColumnIndex(Columns.id)),
    active                      = c.getInt(c.getColumnIndex(Columns.active)) == 1,
    cardBalance                 = c.getInt(c.getColumnIndex(Columns.cardBalance)),
    cardBalanceInDollars        = c.getString(c.getColumnIndex(Columns.cardBalanceInDollars)),
    cardNickName                = c.getString(c.getColumnIndex(Columns.cardNickName)),
    cardNumber                  = c.getString(c.getColumnIndex(Columns.cardNumber)),
    cardState                   = c.getString(c.getColumnIndex(Columns.cardState)),
    currentCardBalanceInDollars = c.getString(c.getColumnIndex(Columns.currentCardBalanceInDollars)),
    displayName                 = c.getString(c.getColumnIndex(Columns.displayName)),
    svPending                   = c.getInt(c.getColumnIndex(Columns.svPending)),
    svPendingInDollars          = c.getString(c.getColumnIndex(Columns.svPendingInDollars)),
    toBeActivated               = c.getInt(c.getColumnIndex(Columns.toBeActivated)) == 1
  )

  private [provider] val columns = Array(
    Columns.id,
    Columns.active,
    Columns.cardBalance,
    Columns.cardBalanceInDollars,
    Columns.cardNickName,
    Columns.cardNumber,
    Columns.cardState,
    Columns.currentCardBalanceInDollars,
    Columns.displayName,
    Columns.svPending,
    Columns.svPendingInDollars,
    Columns.toBeActivated,

    Columns.tempName
  )

  private [provider] def toRow(card: CardDetails) = Array[AnyRef](
    new Integer(card.index),
    new Integer(if (card.active) 1 else 0),
    new Integer(card.cardBalance),
    card.cardBalanceInDollars,
    card.cardNickName,
    card.cardNumber,
    card.cardState,
    card.currentCardBalanceInDollars,
    card.displayName,
    new Integer(card.svPending),
    card.svPendingInDollars,
    new Integer(if (card.toBeActivated) 1 else 0),

    s"${Option(card.cardNickName).getOrElse("")} ${card.formatedCardNumber}"
  )

  def getPrefs(implicit context: Context) = context.getApplicationContext.getSharedPreferences("cache", Context.MODE_PRIVATE)
}
