package it.timgreen.opal.api

import it.timgreen.android.net.Http

import org.json.JSONObject

sealed trait LoginResult
object LoginSuccess extends LoginResult
case class LoginFailed(
  errorMessage: String,
  accountBlocked: Boolean,
  failedLoginAttempt: Boolean,
  failedLoginAttemptsCount: Int,
  validationFailure: Boolean
) extends LoginResult
case class LoginError(t: Throwable) extends LoginResult

case class LoginFailedException(failed: LoginFailed) extends Exception

object LoginResult {
  private val ERROR_MESSAGE = "errorMessage"
  private val ACCOUNT_BLOCKED = "accountBlocked"
  private val FAILED_LOGIN_ATTEMPT = "failedLoginAttempt"
  private val FAILED_LOGIN_ATTEMPTS_COUNT = "failedLoginAttemptsCount"
  private val VALIDATION_FAILURE = "validationFailure"

  def apply(res: Http.Response[JSONObject]): LoginResult = res match {
    case Http.Response(200, Left(json)) =>
      if (json.has(ERROR_MESSAGE) && json.isNull(ERROR_MESSAGE) &&
          json.has(VALIDATION_FAILURE) && !json.getBoolean(VALIDATION_FAILURE)) {
        LoginSuccess
      } else {
        try {
          LoginFailed(
            json.getString(ERROR_MESSAGE),
            json.getBoolean(ACCOUNT_BLOCKED),
            json.getBoolean(FAILED_LOGIN_ATTEMPT),
            json.getInt(FAILED_LOGIN_ATTEMPTS_COUNT),
            json.getBoolean(VALIDATION_FAILURE)
          )
        } catch {
          case t: Throwable =>
            LoginError(t)
        }
      }
    case Http.Response(_, Right(t)) =>
      LoginError(t)
    case Http.Response(code, _) =>
      LoginError(new Exception("Http response code: " + code))
  }
}
