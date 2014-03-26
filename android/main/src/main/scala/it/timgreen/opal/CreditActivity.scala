package it.timgreen.opal

import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

import scala.io.Source

class CreditActivity extends Activity {

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_credit)

    val html = Source.fromInputStream(getResources.openRawResource(R.raw.credit)).mkString
    findViewById(R.id.webview).asInstanceOf[WebView].loadData(html, "text/html", "utf-8")
  }
}
