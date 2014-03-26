package it.timgreen.android.net

import scala.io.Source

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

import org.json.JSONArray
import org.json.JSONObject

import android.os.Build

object Http {

  case class ConnSetting(
    readTimeoutInMilli: Int = 10000,
    connectTimeoutInMilli: Int = 15000,
    followRedirects: Boolean = false,
    getResponseOn: Int => Boolean = ConnSetting.onlyAccpet(200),
    userAgent: Option[String] = None
  )
  object ConnSetting {
    implicit val lowPriorityProvider = ConnSetting(
      getResponseOn = onlyAccpet(200)
    )

    def onlyAccpet(codes: Int*)(code: Int): Boolean = codes.contains(code)
  }

  case class UnexpectedResponseCode(code: Int, str: String) extends Exception(s"Unexpected response code $code:\n$str")

  case class Response[R](
    code: Int = -1,
    result: Either[R, Throwable]
  ) {
    def map[RR](op: R => RR): Response[RR] =
      Response(
        code,
        try {
          result.left.map(op)
        } catch {
          case e: Throwable => Right(e)
        }
      )
  }

  object Implicits {
    implicit class RichStringResponse(sr: Response[String]) {
      def asJson: Response[JSONObject] = sr map { new JSONObject(_) }
      def asJsonArray: Response[JSONArray] = sr map { new JSONArray(_) }
    }
  }

  def withConn[R](url: String)(op: HttpURLConnection => R)(implicit setting: ConnSetting): R = {
    val conn = getConn(url)
    val r = op(conn)
    conn.disconnect
    r
  }

  private def getConn(url: String)(implicit setting: ConnSetting) = {
    val conn = new URL(url).openConnection().asInstanceOf[HttpURLConnection]
    conn.setInstanceFollowRedirects(setting.followRedirects)
    conn.setReadTimeout(setting.readTimeoutInMilli)
    conn.setConnectTimeout(setting.connectTimeoutInMilli)
    setting.userAgent foreach { userAgent =>
      conn.setRequestProperty("User-agent", userAgent)
    }
    if (Build.VERSION.SDK_INT > 13) {
      conn.setRequestProperty("Connection", "close")
    }
    conn
  }

  private def safeGetResponse(conn: HttpURLConnection, code: Int)
                             (implicit setting: ConnSetting): String = {
    val is = conn.getInputStream
    val str = Source.fromInputStream(is).mkString
    is.close
    if (setting.getResponseOn(code)) {
      str
    } else {
      throw UnexpectedResponseCode(code, conn.getHeaderFields + "\n" + str)
    }
  }

  def get(url: String)(implicit setting: ConnSetting): Response[String] = {
    var code = -1;
    try {
      val str = withConn(url) { conn =>
        conn.setRequestMethod("GET")
        conn.connect
        code = conn.getResponseCode()
        safeGetResponse(conn, code)
      }
      Response(code, Left(str))
    } catch {
      case e: Throwable =>  Response(code, Right(e))
    }
  }

  def post(url: String, params: (String, String)*)
          (implicit setting: ConnSetting): Response[String] = {
    var code = -1;
    try {
      val str = withConn(url) { conn =>
        conn.setRequestMethod("POST")
        conn.setDoOutput(true)
        conn.setDoInput(true)

        val postBody = params map { case (k, v) =>
          k + "=" + URLEncoder.encode(v, "UTF-8")
        } mkString("&")

        conn.setFixedLengthStreamingMode(postBody.getBytes("UTF-8").length)
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

        val os = conn.getOutputStream
        os.write(postBody.getBytes("UTF-8"))
        os.flush
        os.close

        code = conn.getResponseCode()
        safeGetResponse(conn, code)
      }
      Response(code, Left(str))
    } catch {
      case e: Throwable =>  Response(code, Right(e))
    }
  }
}
