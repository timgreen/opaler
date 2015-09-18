package it.timgreen.android.net

import org.json.JSONArray
import org.json.JSONObject

import com.squareup.okhttp.FormEncodingBuilder
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.{ Request => OkRequest, Response => OkResponse }

object Http {

  case class ConnSetting(
    client: OkHttpClient,
    getResponseOn: Int => Boolean = ConnSetting.onlyAccpet(200),
    userAgent: Option[String] = None
  )
  object ConnSetting {
    val defaultClient = new OkHttpClient
    implicit val lowPriorityProvider = ConnSetting(
      client = defaultClient,
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
          case e: Throwable =>
            android.util.Log.d("TIM_HTTP_DEBUG", "Faild to parse res:\n" + result.left, e)
            Right(e)
        }
      )
  }

  object Implicits {
    implicit class RichStringResponse(sr: Response[String]) {
      def asJson: Response[JSONObject] = sr map { new JSONObject(_) }
      def asJsonArray: Response[JSONArray] = sr map { new JSONArray(_) }
    }
  }

  private def getRequestBuilder(url: String)(implicit setting: ConnSetting): OkRequest.Builder = {
    val builder = (new OkRequest.Builder)
      .url(url)
    setting.userAgent foreach { userAgent =>
      builder.header("User-agent", userAgent)
    }

    builder
  }

  private def safeGetResponse(response: OkResponse)
                             (implicit setting: ConnSetting): String = {
    if (setting.getResponseOn(response.code)) {
      response.body.string
    } else {
      throw UnexpectedResponseCode(response.code, response.headers + "\n" + response.body.string)
    }
  }

  def get(url: String)(implicit setting: ConnSetting): Response[String] = {
    var code = -1
    val request = getRequestBuilder(url)
      .get
      .build
    try {
      val response = setting.client.newCall(request).execute
      code = response.code
      val str = safeGetResponse(response)
      Response(response.code, Left(str))
    } catch {
      case e: Throwable =>  Response(code, Right(e))
    }
  }

  def post(url: String, params: (String, String)*)
          (implicit setting: ConnSetting): Response[String] = {
    var code = -1
    val formBodyBuilder = new FormEncodingBuilder
    params foreach { case (k, v) =>
      formBodyBuilder.add(k, v)
    }
    val request = getRequestBuilder(url)
      .post(formBodyBuilder.build)
      .build
    try {
      val response = setting.client.newCall(request).execute
      code = response.code
      val str = safeGetResponse(response)
      Response(response.code, Left(str))
    } catch {
      case e: Throwable =>  Response(code, Right(e))
    }
  }
}
