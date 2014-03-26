package it.timgreen.opal

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.View
import android.view.ViewGroup.LayoutParams
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TextView

import it.timgreen.android.AsyncTask
import it.timgreen.android.conversion.TextView._
import it.timgreen.android.conversion.View._
import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.api.LoginError
import it.timgreen.opal.api.LoginFailed
import it.timgreen.opal.api.LoginResult
import it.timgreen.opal.api.LoginSuccess
import it.timgreen.opal.api.OpalAccount
import it.timgreen.opal.api.OpalApi

import java.util.{ List => JList }

class LoginActivity extends Activity with AccountHelper.Operator {
  implicit def getActivity = this

  private var username: EditText = _
  private var password: EditText = _
  private var loginBtn: Button = _
  private var loginMessage: TextView = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_login)
    setupLayoutAdjustForKeyboard

    username = findViewById(R.id.username).asInstanceOf[EditText]
    password = findViewById(R.id.password).asInstanceOf[EditText]
    password.setTypeface(Typeface.DEFAULT)
    loginBtn = findViewById(R.id.loginBtn).asInstanceOf[Button]
    loginMessage = findViewById(R.id.loginMessage).asInstanceOf[TextView]

    val loginFooter = findViewById(R.id.login_footer_html).asInstanceOf[TextView]
    loginFooter.setText(Html.fromHtml(getString(R.string.login_footer)))
    loginFooter.setMovementMethod(LinkMovementMethod.getInstance)


    password.setOnEditorActionListener { () =>
      loginBtn.performClick
      false
    }
  }

  override def onResume() {
    super.onResume
  }

  private def setupLayoutAdjustForKeyboard() {
    val container = findViewById(R.id.container)
    val header = findViewById(R.id.header)
    val main = findViewById(R.id.main)
    val footer = findViewById(R.id.footer)

    container.getViewTreeObserver.addOnGlobalLayoutListener { () =>
      val heightDiff = container.getRootView.getHeight - container.getHeight
      if (heightDiff > 100) {  // keyboard up
        header.setLayoutParams(new TableLayout.LayoutParams(
          LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0))
        main.setLayoutParams(new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1))
        footer.setLayoutParams(new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 0))
      } else {  // keyboard down
        header.setLayoutParams(new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1))
        main.setLayoutParams(new TableLayout.LayoutParams(
          LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, 0))
        footer.setLayoutParams(new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1))
      }
    }
  }

  def login(view: View) {
    var hasError = false
    if (username.getText.toString.trim.isEmpty) {
      username.setError("username is required!")
      username.requestFocus
      hasError = true
    }
    if (password.getText.toString.trim.isEmpty) {
      password.setError("password is required!")
      if (!hasError) {
        password.requestFocus
      }
      hasError = true
    }

    if (!hasError) {
      Some(getCurrentFocus) foreach { v =>
        val inputManager = getSystemService(Context.INPUT_METHOD_SERVICE).asInstanceOf[InputMethodManager]
        inputManager.hideSoftInputFromWindow(v.getWindowToken, InputMethodManager.HIDE_NOT_ALWAYS)
      }

      findViewById(R.id.main).requestFocus  // TODO(timgreen): clear edittext focus
      List(loginBtn, username, password) foreach { _.setEnabled(false) }
      // TODO(timgreen): text
      loginMessage.setText("Login ...")
      new CheckLoginTask().execute(OpalAccount(username.getText.toString, password.getText.toString))
    }
  }

  private def onLoginSuccess() {
    AccountUtil.removeAllAccount
    AccountUtil.addAccount(username.getText.toString, password.getText.toString)

    val intent = new Intent(this, classOf[TutorialActivity])
    startActivity(intent)

    // TODO(timgreen): text
    loginMessage.setText("Login Success.")

    finish
  }

  private def onLoginFailed(failed: LoginFailed) {
    loginMessage.setText(failed.errorMessage)
    List(loginBtn, username, password) foreach { _.setEnabled(true) }
  }

  private def onLoginError(t: Throwable) {
    // TODO(timgreen): error
    t match {
      case _: java.io.IOException =>
        loginMessage.setText("Network error, Please try again later")
      case _ =>
        loginMessage.setText("Opal online service is not available, Please try again later")
    }
    List(loginBtn, username, password) foreach { _.setEnabled(true) }
  }

  class CheckLoginTask extends AsyncTask[OpalAccount, Void, LoginResult] {
    override protected def doInBackgroundWithArray(account: JList[OpalAccount]): LoginResult = {
      OpalApi.login(account.get(0))
    }

    override protected def onPostExecute(result: LoginResult) {
      result match {
        case LoginSuccess =>
          trackEvent("Login", "loginSuccess")(LoginActivity.this)
          onLoginSuccess
        case failed: LoginFailed =>
          trackEvent("Login", "loginFailed")(LoginActivity.this)
          onLoginFailed(failed)
        case LoginError(t) =>
          trackEvent("Login", "loginError", Some(t.toString))(LoginActivity.this)
          onLoginError(t)
      }
    }

    override protected def onCancelled(result: LoginResult) {
      onLoginError(null)
    }
  }
}
