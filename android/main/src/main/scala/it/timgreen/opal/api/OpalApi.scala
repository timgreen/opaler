package it.timgreen.opal.api

import java.net.CookieManager
import java.util.concurrent.TimeUnit

import com.squareup.okhttp.OkHttpClient
import it.timgreen.android.net.Http
import it.timgreen.android.net.Http.ConnSetting
import it.timgreen.android.net.Http.Implicits._
import it.timgreen.opal.Util

case class OpalAccount(username: String, password: String)

sealed trait ApiResult

object OpalApi {

  // Setup cookie manager.
  val cookieManager = new CookieManager
  val okHttpClient = new OkHttpClient
  okHttpClient.setCookieHandler(cookieManager)
  okHttpClient.setFollowRedirects(false)
  okHttpClient.setReadTimeout(10000, TimeUnit.MILLISECONDS)
  okHttpClient.setConnectTimeout(15000, TimeUnit.MILLISECONDS)


  val LOGIN_URL = "https://www.opal.com.au/login/registeredUserUsernameAndPasswordLogin"

  // TODO(timgreen): use GTM to update this field
  // NOTE(timgreen): set desktop userAgent to avoid redirect to https://m.opal.com.au.
  implicit val connWithDesktopUserAgent = ConnSetting(
    client = okHttpClient,
    userAgent = Some("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/44.0.2403.89 Chrome/44.0.2403.89 Safari/537.36")
  )

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

  def getCardList(implicit opalAccount: OpalAccount): Either[List[Card], Throwable] = withRetry(5) {
    Card.parseList {
      withAutoLogin {
        Http.get(cardDetailsUrl).asJsonArray
      }
    }
  }

  def getCardTransactions(cardIndex: Int, pageIndex: Int, updatedTime: Long)(implicit opalAccount: OpalAccount) = withRetry(5) {
    CardTransaction.parseList(updatedTime) {
      withAutoLogin {
        Http.get(cardTransactionsUrl(cardIndex, pageIndex))
      }
    }
  }

  private def withAutoLogin[T](op: => Http.Response[T])
                              (implicit opalAccount: OpalAccount): T = {
    op match {
      case Http.Response(_, Left(r)) => r
      case Http.Response(302, _) =>
        login match {
          case LoginSuccess => op.result.left.get
          case failed: LoginFailed => throw new LoginFailedException(failed)
          case LoginError(e) => throw e
        }
      case Http.Response(_, Right(t)) => throw t
    }
  }

  private def withRetry[T](retriesLeft: Int)
                          (op: => T)
                          (implicit opalAccount: OpalAccount): Either[T, Throwable] = {
    val result = try {
      Left(op)
    } catch {
      case e: Throwable => Right(e)
    }

    if (retriesLeft >= 0) {
      result match {
        case Left(_)  => result
        case Right(_) => withRetry(retriesLeft - 1)(op)
      }
    } else {
      result
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
