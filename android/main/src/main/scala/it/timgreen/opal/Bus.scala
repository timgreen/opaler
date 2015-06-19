package it.timgreen.opal

import it.timgreen.android.model.ValueModel

object Bus {
  val currentCardIndex = ValueModel[Option[Int]](None)
  val isSyncing = ValueModel(false)
}
