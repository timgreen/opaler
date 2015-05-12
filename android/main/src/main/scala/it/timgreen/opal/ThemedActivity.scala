package it.timgreen.opal

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

abstract class ThemedActivity extends AppCompatActivity {
  val translucentStatus = false

  override def onCreate(savedInstanceState: Bundle) {
    setTheme(theme)
    super.onCreate(savedInstanceState)
  }

  private def theme: Int = {
    (translucentStatus, PrefUtil.prefs(this).getString("theme", "dark")) match {
      case (true, "dark") => R.style.AppTheme_Dark_TranslucentStatus
      case (false, "dark") => R.style.AppTheme_Dark
      case (true, "light") => R.style.AppTheme_Light_TranslucentStatus
      case (false, "light") | _ => R.style.AppTheme_Light
    }
  }
}
