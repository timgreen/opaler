package it.timgreen.opal.provider

import android.content.Context

import org.json.JSONObject

import scala.collection.JavaConversions._

case class WidgetSettings(
  cardIndex: Int
)

object WidgetSettings {
  val widgetIdKey = "widgetId"
  val cardIndexKey = "cardIndex"

  private def getPrefs(implicit context: Context) = {
    context.getApplicationContext.getSharedPreferences("widget", Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS)
  }

  private[provider] def getSettings(appWidgetId: Int)(implicit context: Context): WidgetSettings = {
    val json = try {
      new JSONObject(getPrefs.getString(appWidgetId.toString, "{}"))
    } catch {
      case _: Throwable =>
        new JSONObject()
    }
    fromJsonObject(json)
  }

  private[provider] def setSetting(appWidgetId: Int, settings: WidgetSettings)(implicit context: Context) {
    getPrefs.edit
      .putString(appWidgetId.toString, toJsonObject(settings).toString)
      .commit
  }

  private[provider] def removeUnusedSettings(activieAppWidgetIds: Array[Int])(implicit context: Context): Int = {
    val set = activieAppWidgetIds.map(_.toString).toSet
    val prefs = getPrefs
    val unused = prefs.getAll.keySet.filterNot(x => set.contains(x))
    if (unused.nonEmpty) {
      val editor = prefs.edit
      unused foreach editor.remove
      editor.commit
    }
    unused.size
  }

  private def toJsonObject(widgetSettings: WidgetSettings): JSONObject = {
    val json = new JSONObject
    json.put("cardIndex", widgetSettings.cardIndex)

    json
  }

  private def fromJsonObject(json: JSONObject) = WidgetSettings(
    cardIndex = json.optInt("cardIndex")
  )
}
