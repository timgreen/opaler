package it.timgreen.opal.api

import org.json.JSONArray
import org.json.JSONObject

case class Card(
  index: Int,
  active: Boolean,
  cardBalance: Int,
  cardBalanceInDollars: String,
  cardNickName: String,
  cardNumber: String,
  cardState: String,
  currentCardBalanceInDollars: String,
  displayName: String,
  svPending: Int,
  svPendingInDollars: String,
  toBeActivated: Boolean) {
  def formatedCardNumber = cardNumber.grouped(4).mkString(" ")
}

object Card {
  def parseList(jsonArray: JSONArray): List[Card] = {
    val len = jsonArray.length
    0 until len map { i =>
      parseCard(jsonArray.getJSONObject(i), i)
    } toList
  }

  private def parseCard(json: JSONObject, index: Int) = {
    val card = Card(
      index = index,
      active = json.getBoolean("active"),
      cardBalance = json.getInt("cardBalance"),
      cardBalanceInDollars = json.getString("cardBalanceInDollars"),
      cardNickName = json.getString("cardNickName").replaceAll("\\\\x27", "'"),
      cardNumber = json.getString("cardNumber"),
      cardState = json.getString("cardState"),
      currentCardBalanceInDollars = json.getString("currentCardBalanceInDollars"),
      displayName = json.getString("displayName").replaceAll("\\\\x27", "'"),
      svPending = json.getInt("svPending"),
      svPendingInDollars = json.getString("svPendingInDollars"),
      toBeActivated = json.getBoolean("toBeActivated")
    )

    // NOTE(timgreen): opal.com.au return "null" for cardNickName & displayName if user set it to
    // empty.
    card.copy(
      cardNickName = if (card.cardNickName == "null") "" else card.cardNickName,
      displayName =  if (card.displayName == "null") "" else card.displayName
    )
  }

  def toJsonArray(cards: List[Card]): JSONArray = {
    val array = new JSONArray
    cards foreach { card =>
      array.put(toJsonObject(card))
    }
    array
  }

  private def toJsonObject(card: Card): JSONObject = {
    val json = new JSONObject
    json.put("active", card.active)
    json.put("cardBalance", card.cardBalance)
    json.put("cardBalanceInDollars", card.cardBalanceInDollars)
    json.put("cardNickName", card.cardNickName)
    json.put("cardNumber", card.cardNumber)
    json.put("cardState", card.cardState)
    json.put("currentCardBalanceInDollars", card.currentCardBalanceInDollars)
    json.put("displayName", card.displayName)
    json.put("svPending", card.svPending)
    json.put("svPendingInDollars", card.svPendingInDollars)
    json.put("toBeActivated", card.toBeActivated)

    json
  }
}
