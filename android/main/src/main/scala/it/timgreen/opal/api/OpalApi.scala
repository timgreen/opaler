package it.timgreen.opal.api

import java.net.CookieHandler
import java.net.CookieManager

import it.timgreen.android.net.Http
import it.timgreen.android.net.Http.ConnSetting
import it.timgreen.android.net.Http.Implicits._
import it.timgreen.opal.Util

case class OpalAccount(username: String, password: String)

sealed trait ApiResult

object OpalApi {

  // Setup cookie manager.
  val cookieManager = new CookieManager()
  CookieHandler.setDefault(cookieManager)

  val LOGIN_URL = "https://www.opal.com.au/login/registeredUserUsernameAndPasswordLogin"

  // NOTE(timgreen): set desktop userAgent to avoid redirect to https://m.opal.com.au.
  implicit val connWithDesktopUserAgent = ConnSetting(userAgent = Some("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/39.0.2171.95 Safari/537.36"))

  def login(implicit opalAccount: OpalAccount): LoginResult = {
    Util.debug("Login...")

    val response = Http.post(
      LOGIN_URL,
      "h_username" -> opalAccount.username,
      "h_password" -> opalAccount.password
    ).asJson

    Util.debug("Login..." + response)

    LoginResult(response)
  }

  def getCardDetailsList(implicit opalAccount: OpalAccount): Either[List[CardDetails], Throwable] = try {
    withAutoLogin {
      Http.get(cardDetailsUrl).asJsonArray
    }.left map CardDetails.parseList
  } catch {
    case e: Throwable => Right(e)
  }

  def getCardTransactions(cardIndex: Int, pageIndex: Int, updatedTime: Long)(implicit opalAccount: OpalAccount) = try {
    withAutoLogin {
      Http.get(cardTransactionsUrl(cardIndex, pageIndex))
    }.left map CardTransaction.parseList(updatedTime)
  } catch {
    case e: Throwable => Right(e)
  }


  private def withAutoLogin[T](op: => Http.Response[T])
                              (implicit opalAccount: OpalAccount): Either[T, Throwable] = {
    try {
      op match {
        case Http.Response(_, Left(r)) =>
          Left(r)
        case Http.Response(302, _) =>
          login match {
            case LoginSuccess => op.result
            case failed: LoginFailed => Right(new LoginFailedException(failed))
            case LoginError(e) => Right(e)
          }
        case Http.Response(_, Right(t)) =>
          Right(t)
      }
    } catch {
      case e: Throwable => Right(e)
    }
  }

  private def appendTimestamp(url: String) = {
    url + "_=" + java.util.Calendar.getInstance.getTimeInMillis
  }

  private def cardDetailsUrl = {
    val CARD_DETAILS_URL = "https://www.opal.com.au/registered/getJsonCardDetailsArray?"
    Util.debug(appendTimestamp(CARD_DETAILS_URL))
    appendTimestamp(CARD_DETAILS_URL)
  }

  private def cardTransactionsUrl(cardIndex: Int, pageIndex: Int) = {
    val CARD_TRANSACTIONS_URL = "https://www.opal.com.au/registered/opal-card-transactions/opal-card-activities-list?AMonth=-1&AYear=-1"
    Util.debug(appendTimestamp(CARD_TRANSACTIONS_URL + "&cardIndex=" + cardIndex + "&pageIndex=" + pageIndex + "&"))
    appendTimestamp(CARD_TRANSACTIONS_URL + "&cardIndex=" + cardIndex + "&pageIndex=" + pageIndex + "&")
  }
}
