package it.timgreen.opal.api

import scala.xml.Node
import scala.xml.XML

import android.text.format.Time

import java.text.DecimalFormat

import it.timgreen.opal.Util

case class CardTransaction(
  transactionNumber: Int,
  datetime: Time,
  model: Model,
  details: TransactionDetails,
  journeyNumber: Option[Int],
  fareApplied: FareApplied,
  fare: Option[Double],
  discount: Option[Double],
  amount: Option[Double],
  updatedTime: Long
) {
  def weekNumber = datetime.getWeekNumber
  def weekDay = datetime.weekDay
  def julianWeekNumber = Util.getJulianWeekNumber(datetime)
}

object CardTransaction {
  val timezone = "Australia/Sydney"

  def parseList(updatedTime: Long)(html: String): (Boolean, List[CardTransaction]) = {
    // TODO(timgreen): hack fix for entities in html.
    val fixedHtml = html.replaceAll("&[a-zA-Z]+;", "").replaceAll("&", "&amp;")
    val htmlXml = try {
      XML.loadString(s"<div>$fixedHtml</div>")
    } catch {
      case e: Throwable =>
        Util.debug("Faild to parse xml:\n" + fixedHtml, e)
        throw e
    }
    val Some(tableXml) = (htmlXml \\ "table") find { _ \ "@id" exists (_.text == "transaction-data") }
    val optPaginationXml = (htmlXml \\ "div") find { _ \ "@id" exists (_.text == "pagination") }

    // NOTE(timgreen): filter empty line.
    val list = (tableXml \ "tbody" \ "tr").toList filter { _.toString != "<tr><td/></tr>" } map parse(updatedTime)
    val hasNext = optPaginationXml map { d =>
      (d \\ "a") exists {
        _ \ "@title" exists (_.text == "Next page")
      }
    } getOrElse false

    (hasNext, list)
  }

  def parse(updatedTime: Long)(tr: Node): CardTransaction = {
    val tds = tr \ "td"

    if (tds.size != 9) {
      throw new ApiChangedException("tds != 9 " + tr.toString)
    }

    val details = TransactionDetails(tds(3).text)
    CardTransaction(
      transactionNumber = tds(0).text.toInt,
      datetime          = parseDatetime(tds(1)),
      model             = parseModel(tds(2), details),
      details           = details,
      journeyNumber     = optNum(tds(4).text, _.toInt),
      fareApplied       = FareApplied(tds(5).text, optNum(tds(8).text, _.toDouble)),
      fare              = optNum(tds(6).text, _.toDouble),
      discount          = optNum(tds(7).text, _.toDouble),
      amount            = optNum(tds(8).text, _.toDouble),
      updatedTime       = updatedTime
    )
  }

  private def optNum[T](text: String, op: String => T): Option[T] = (text match {
    case "" => None
    case _ => Some(text.replace("$", "").trim)
  }).map(op)

  // NOTE(timgreen): ignore timezone, trust as UTC.
  private val dateP = """(\d\d)/(\d\d)/(\d\d\d\d)""".r
  private val timeP = """(\d\d):(\d\d)""".r
  private def parseDatetime(datetime: Node): Time = {
    val Seq(_, _, date, _, time) = datetime.child

    val dateP(dd, mM, yyyy) = date.text
    val timeP(hh, mm) = time.text
    val t = new Time(timezone)
    t.set(0, mm.toInt, hh.toInt, dd.toInt, mM.toInt - 1, yyyy.toInt)
    t.normalize(false)
    t
  }

  def timeFromLong(t: Long): Time = {
    val time = new Time(timezone)
    time.set(t)
    time
  }

  val formatter = new DecimalFormat("$0.00")
  def formatMoney(m: Option[Double], default: String = ""): String = m match {
    case None => default
    case Some(m) => formatter.format(m)
  }

  private def parseModel(model: Node, details: TransactionDetails): Model = {
    val m = model \ "img" \ "@alt" text

    if (details.isInstanceOf[TransactionDetails.TopUp]) {
      Model.TopUp
    } else if (details.isInstanceOf[TransactionDetails.AutoTopUp]) {
      Model.AutoTopUp
    } else if (details == TransactionDetails.Adjustment) {
      Model.Adjustment
    } else if (details == TransactionDetails.BalanceTransfer) {
      Model.BalanceTransfer
    } else if (details.isInstanceOf[TransactionDetails.Blocked]) {
      Model.Blocked
    } else if (details.isInstanceOf[TransactionDetails.RejectedAction]) {
      Model.RejectedAction
    } else if (m == "train" && isLightRail(details)) {
      Model.LightRail
    } else {
      Model(m)
    }
  }

  private val lightRailStationSuffix = " LR"
  private def isLightRail(details: TransactionDetails) = details match {
    case TransactionDetails.FromTo(from, to) =>
      from.endsWith(lightRailStationSuffix) || to.endsWith(lightRailStationSuffix)
    case TransactionDetails.TapOnReversal(stop) =>
      stop.endsWith(lightRailStationSuffix)
    case TransactionDetails.Blocked(stop) =>
      stop.endsWith(lightRailStationSuffix)
    case TransactionDetails.RejectedAction(stop) =>
      stop.endsWith(lightRailStationSuffix)
    case _ =>
      false
  }
}

sealed class Model(model: String) {
  override def toString = model
  def isTrip = false
}

object Model {
  def apply(model: String): Model = model match {
    case "topup"           => TopUp
    case "autotopup"       => AutoTopUp
    case "adjustment"      => Adjustment
    case "balancetransfer" => BalanceTransfer
    case "train"           => Train
    case "bus"             => Bus
    case "ferry"           => Ferry
    case "light_rail"      => LightRail
    case "blocked"         => Blocked
    case "rejectedaction"  => RejectedAction
    case _                 => new Unknown(model)
  }

  object TopUp           extends Model("topup")
  object AutoTopUp       extends Model("autotopup")
  object Adjustment      extends Model("adjustment")
  object BalanceTransfer extends Model("balancetransfer")
  object Train           extends Model("train") { override def isTrip = true }
  object Bus             extends Model("bus"  ) { override def isTrip = true }
  object Ferry           extends Model("ferry") { override def isTrip = true }
  object LightRail       extends Model("light_rail") { override def isTrip = true }
  object Blocked         extends Model("blocked")
  object RejectedAction  extends Model("rejectedaction")
  class Unknown(model: String) extends Model(model)
}

class TransactionDetails(details: String) {
  override def toString = details
}
object TransactionDetails {
  val tapOnReversalPrefix = "Tap on reversal - "
  val blockedPrefix = "Blocked - "
  val rejectedActionPrefix = "Rejected action - "

  def apply(d: String): TransactionDetails = {
    val details = d.trim
    if (details.startsWith("Top up")) {
      new TopUp(details)
    } else if (details.startsWith("Auto top up")) {
      new AutoTopUp(details)
    } else if (details == "Adjustment") {
      Adjustment
    } else if (details == "Balance transfer") {
      BalanceTransfer
    } else if (details == "No details available") {
      NoDetailsAvailable
    } else if (details.startsWith(tapOnReversalPrefix)) {
      val stop = details.substring(tapOnReversalPrefix.size)
      new TapOnReversal(stop)
    } else if (details.startsWith(blockedPrefix)) {
      val stop = details.substring(blockedPrefix.size)
      new Blocked(stop)
    } else if (details.startsWith(rejectedActionPrefix)) {
      val stop = details.substring(rejectedActionPrefix.size)
      new RejectedAction(stop)
    } else if (details.contains(" to ")) {
      val Array(from, to) = details.split(" to ", 2)
      new FromTo(from, to)
    } else {
      new Unknown(details)
    }
  }

  case class FromTo(from: String, to: String) extends TransactionDetails(s"$from to $to") {
    def isNoTapOn = from.trim.toLowerCase == "no tap on"
    def isNoTapOff = to.trim.toLowerCase == "no tap off"
  }
  case class TapOnReversal(stop: String) extends TransactionDetails(tapOnReversalPrefix + stop)
  class TopUp(details: String) extends TransactionDetails(details)
  class AutoTopUp(details: String) extends TransactionDetails(details)
  object Adjustment extends TransactionDetails("Adjustment")
  object BalanceTransfer extends TransactionDetails("Balance transfer")
  case class Blocked(stop: String) extends TransactionDetails(blockedPrefix + stop)
  case class RejectedAction(stop: String) extends TransactionDetails(rejectedActionPrefix + stop)
  object NoDetailsAvailable extends TransactionDetails("No details available")
  class Unknown(details: String) extends TransactionDetails(details)
}

sealed class FareApplied(fareApplied: String) {
  override def toString = fareApplied
}
object FareApplied {
  def apply(fareApplied: String, amount: Option[Double]): FareApplied = fareApplied match {
    case "" => Normal
    case "Off-peak" => OffPeak
    case "Default fare" => DefaultFare
    case "Day Cap" => DayCap
    case "Travel Reward" =>
      // Remove label 'Travel Reward' for the trip is not covered the reward, e.g. internation
      // airport line.
      if (amount.map(_ > 0) == Some(true)) Normal else TravelReward
    case _ => new Unknown(fareApplied)
  }

  object Normal       extends FareApplied("")
  object OffPeak      extends FareApplied("Off-peak")
  object DefaultFare  extends FareApplied("Default fare")
  object DayCap       extends FareApplied("Day Cap")
  object TravelReward extends FareApplied("Travel Reward")
  class Unknown(fareApplied: String) extends FareApplied(fareApplied)
}

