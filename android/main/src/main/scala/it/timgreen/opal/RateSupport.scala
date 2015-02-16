package it.timgreen.opal

import android.app.Activity
import android.os.Bundle

import com.github.fernandodev.easyratingdialog.library.EasyRatingDialog

trait RateSupport { self: Activity =>

  private var easyRatingDialog: Option[EasyRatingDialog] = _

  override def onCreate(savedInstanceState: Bundle) {
    self.onCreate(savedInstanceState)
    easyRatingDialog = Some(new EasyRatingDialog(self))
  }

  override def onStart() {
    self.onStart
    easyRatingDialog foreach { _.onStart }
  }

  override def onResume() {
    self.onResume
    easyRatingDialog foreach { _.showIfNeeded }
  }
}
