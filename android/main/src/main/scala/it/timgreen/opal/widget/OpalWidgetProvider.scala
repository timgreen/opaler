package it.timgreen.opal.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent

class OpalWidgetProvider extends AppWidgetProvider {

  override def onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: Array[Int]) {
    super.onUpdate(context, appWidgetManager, appWidgetIds)
    context.startService(new Intent(context, classOf[OpalWidgetUpdateService]))
  }
}
