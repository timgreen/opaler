package it.timgreen.opal.api

import org.json.JSONArray
import org.json.JSONObject

case class CardDetails(
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

object CardDetails {
  def parseList(jsonArray: JSONArray): List[CardDetails] = {
    val len = jsonArray.length
    0 until len map { i =>
      parseCardDetails(jsonArray.getJSONObject(i), i)
    } toList
  }

  private def parseCardDetails(json: JSONObject, index: Int) = {
    val card = CardDetails(
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

  def toJsonArray(cardDetailsList: List[CardDetails]): JSONArray = {
    val array = new JSONArray
    cardDetailsList foreach { cardDetails =>
      array.put(toJsonObject(cardDetails))
    }
    array
  }

  private def toJsonObject(cardDetails: CardDetails): JSONObject = {
    val json = new JSONObject
    json.put("active", cardDetails.active)
    json.put("cardBalance", cardDetails.cardBalance)
    json.put("cardBalanceInDollars", cardDetails.cardBalanceInDollars)
    json.put("cardNickName", cardDetails.cardNickName)
    json.put("cardNumber", cardDetails.cardNumber)
    json.put("cardState", cardDetails.cardState)
    json.put("currentCardBalanceInDollars", cardDetails.currentCardBalanceInDollars)
    json.put("displayName", cardDetails.displayName)
    json.put("svPending", cardDetails.svPending)
    json.put("svPendingInDollars", cardDetails.svPendingInDollars)
    json.put("toBeActivated", cardDetails.toBeActivated)

    json
  }
}
