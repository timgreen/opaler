package it.timgreen.opal

import android.os.Bundle
import android.support.v7.app.AppCompatActivity

abstract class ThemedActivity extends AppCompatActivity {

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setTheme(PrefUtil.getTheme(this))
  }
}
