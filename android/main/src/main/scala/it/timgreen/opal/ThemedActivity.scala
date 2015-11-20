package it.timgreen.opal

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

abstract class ThemedActivity extends AppCompatActivity {
  val translucentStatus = false
  private var currentTheme: Int = _

  override def onCreate(savedInstanceState: Bundle) {
    currentTheme = theme
    setTheme(currentTheme)
    super.onCreate(savedInstanceState)
  }

  override def onResume() {
    super.onResume
    if (currentTheme != theme) {
      recreate
    }
  }

  private def theme: Int = {
    val theme = if (BuildConfig.ENABLE_THEME) {
      PrefUtil.prefs(this).getString("theme", "dark")
    } else {
      "dark"
    }

    (translucentStatus, theme) match {
      case (true, "dark")       => R.style.AppTheme_Dark_TranslucentStatus
      case (false, "dark")      => R.style.AppTheme_Dark
      case (true, "light")      => R.style.AppTheme_Light_TranslucentStatus
      case (false, "light") | _ => R.style.AppTheme_Light
    }
  }
}
