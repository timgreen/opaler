package it.timgreen.opal

import it.timgreen.android.model.Trigger
import it.timgreen.android.model.SingleValue

object Bus {
  val currentCardIndex = SingleValue[Option[Int]](None)
  val isSyncing = SingleValue(false)

  val syncTrigger = Trigger()
  val fragmentRefreshTrigger = Trigger()
}
