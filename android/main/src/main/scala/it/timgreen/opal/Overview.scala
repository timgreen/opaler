package it.timgreen.opal

import android.content.Context
import android.text.format.Time

import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.TransactionTable

case class OverviewData(
  cardIndex: Int,
  today: Option[Double],
  thisWeek: Option[Double],
  lastWeek: Option[Double],
  lastTrip: Option[CardTransaction],
  maxJourneyNumber: Int
)

object Overview {

  def getOverviewData(context: Context, cardIndex: Int): OverviewData = {
    val time = new Time(CardTransaction.timezone)
    time.setToNow
    val thisWeek = Util.getJulianWeekNumber(time)

    val list = try {
      val cursor = context.getContentResolver.query(
        OpalProvider.Uris.activities(cardIndex),
        null,
        s"${TransactionTable.Entry.JULIAN_WEEK_NUMBER} >= ${thisWeek - 1}",
        null,
        null
      )
      val data = TransactionTable.convert(cursor)
      cursor.close
      data
    } catch {
      case t: Throwable =>
        // TODO(timgreen): handle this error.
        Util.debug(s"Error when loading last week transactions for card $cardIndex from db", t)
        Nil
    }

    def calcSpend(filter: CardTransaction => Boolean): Option[Double] = {
      val l = list.filter(filter).filter(i => i.model.isTrip && i.amount.isDefined).map(_.amount.get)
      if (l.isEmpty) {
        None
      } else {
        Some(l.sum)
      }
    }

    OverviewData(
      cardIndex = cardIndex,
      today = calcSpend(t => t.julianWeekNumber == thisWeek && t.weekDay == time.weekDay),
      thisWeek = calcSpend(_.julianWeekNumber == thisWeek),
      lastWeek = calcSpend(_.julianWeekNumber == thisWeek - 1),
      lastTrip = list find { t => t.model.isTrip && t.julianWeekNumber == thisWeek },
      maxJourneyNumber = (0 :: list.filter(_.julianWeekNumber == thisWeek).map(_.journeyNumber.getOrElse(0))).max
    )
  }
}
