package it.timgreen.opal

import android.content.Context

object Usage {

  @inline
  private def usage(implicit context: Context) =
    context.getApplicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)

  class ValueInt(key: String, defaultVal: Int = 0) {
    def apply()(implicit context: Context): Int =
      usage.getInt(key, defaultVal)

    def update(v: Int)(implicit context: Context) {
      Util.debug(s"Usage, $key = $v")
      usage.edit.putInt(key, v).commit
    }
  }

  val lastSelectedCard   = new ValueInt("last_selected_card")
  val numOfCards         = new ValueInt("num_of_cards", 1)
  val numOfTransaction   = new ValueInt("num_of_transaction")
  val numOfWidgets       = new ValueInt("num_of_widgets")
}
