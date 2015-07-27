package it.timgreen.opal

import android.app.Activity
import android.os.Bundle

import hotchemi.android.rate.AppRate

trait RateSupport { self: Activity =>

  override def onCreate(savedInstanceState: Bundle) {
    self.onCreate(savedInstanceState)
    AppRate.`with`(self).monitor
    AppRate.showRateDialogIfMeetsConditions(this);
  }
}
