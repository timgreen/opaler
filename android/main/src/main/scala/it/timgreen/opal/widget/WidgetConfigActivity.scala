package it.timgreen.opal.widget

import android.app.Activity
import android.app.LoaderManager
import android.appwidget.AppWidgetManager
import android.content.ContentValues
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.database.Cursor
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.CursorAdapter
import android.widget.SimpleCursorAdapter
import android.widget.Spinner
import android.widget.TextView

import it.timgreen.opal.R
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.WidgetSettings

class WidgetConfigActivity extends Activity {

  private var appWidgetId = 0
  private var spinner: Option[Spinner] = _

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setResult(Activity.RESULT_CANCELED)

    appWidgetId = Option(getIntent.getExtras) map {
      _.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
    } getOrElse AppWidgetManager.INVALID_APPWIDGET_ID

    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
      finish
    }

    initConfigDialog
  }

  private def initConfigDialog() {
    setContentView(R.layout.widget_config)

    val okBtn = findViewById(R.id.ok_btn).asInstanceOf[Button]
    okBtn.setEnabled(false)
    val spinner = findViewById(R.id.cards_spinner).asInstanceOf[Spinner]
    this.spinner = Some(spinner)
    val emptyCardSpinner = findViewById(R.id.empty_cards_spinner).asInstanceOf[TextView]
    spinner.setEmptyView(emptyCardSpinner)
    val spinnerAdapter = new SimpleCursorAdapter(
      this,
      android.R.layout.simple_spinner_dropdown_item,
      null,
      Array("temp_name"),
      Array(android.R.id.text1),
      CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER
    )
    getLoaderManager.initLoader(0, null, new LoaderManager.LoaderCallbacks[Cursor]() {
      override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
        if (id == 0) {
          new CursorLoader(WidgetConfigActivity.this, OpalProvider.Uris.cards, null, null, null, null)
        } else {
          null
        }
      }

      override def onLoadFinished(loader: Loader[Cursor], cursor: Cursor) {
        spinnerAdapter.changeCursor(cursor)
        okBtn.setEnabled(cursor.getCount > 0)
      }

      override def onLoaderReset(loader: Loader[Cursor]) {
        spinnerAdapter.changeCursor(null)
      }
    })

    spinnerAdapter.setStringConversionColumn(3)
    spinner.setAdapter(spinnerAdapter)
  }

  def ok(view: View) {
    val cardIndex = spinner.map(_.getSelectedItemPosition).getOrElse(0)
    val values = new ContentValues
    values.put(WidgetSettings.cardIndexKey, new Integer(cardIndex))
    values.put(WidgetSettings.widgetIdKey, new Integer(appWidgetId))
    getContentResolver.insert(OpalProvider.Uris.widgets, values)

    val resultValue = new Intent()
    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    setResult(Activity.RESULT_OK, resultValue)

    // NOTE(timgreen): new added widget update happend before config complete,
    // so request another refresh here.
    startService(new Intent(this, classOf[OpalWidgetUpdateService]))

    finish
  }
}
