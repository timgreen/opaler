package it.timgreen.opal.provider

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

import java.lang.{ Integer, Long => JLong }

import it.timgreen.opal.BuildConfig

class OpalProvider extends ContentProvider {
  import OpalProvider._

  implicit def provideContext = getContext

  private var transactionTable: TransactionTable = _

  override def onCreate: Boolean = {
    transactionTable = new TransactionTable(getContext)
    true
  }

  override def getType(uri: Uri): String = sUriMatcher.`match`(uri) match {
    case Route.ACTIVITIES =>
      Type.ACTIVITY
    case Route.ACTIVITIES_ID =>
      Type.ACTIVITY_ITEM
    case Route.ACTIVITIES_MAX_ID =>
      Type.ACTIVITY_MAX_ID_ITEM
    case Route.CARDS =>
      Type.CARD
    case Route.CARDS_ID =>
      Type.CARD_ITEM
    case Route.CARD_OVERVIEW =>
      Type.CARD_OVERVIEW_ITEM
    case Route.WIDGETS =>
      Type.WIDGET
    case Route.WIDGETS_ID =>
      Type.WIDGET_ITEM
    case _ =>
      throw new UnsupportedOperationException("Unknown uri: " + uri)
  }

  override def query(uri: Uri, projection: Array[String], selection: String,
    selectionArgs: Array[String], sortOrder: String): Cursor = {
    val result = sUriMatcher.`match`(uri) match {
      case Route.CARDS =>
        val matrixCursor = new MatrixCursor(CardsCache.columns)
        CardsCache.getCards foreach { card =>
          matrixCursor.addRow(CardsCache.toRow(card))
        }
        matrixCursor.setNotificationUri(getContext.getContentResolver, uri)
        matrixCursor
      case Route.ACTIVITIES =>
        val segments = uri.getPathSegments
        val cardIndex = segments.get(segments.size - 2).toInt
        val cursor = transactionTable.query(cardIndex, selection)
        cursor.setNotificationUri(getContext.getContentResolver, uri)
        cursor
      case Route.ACTIVITIES_MAX_ID =>
        val segments = uri.getPathSegments
        val cardIndex = segments.get(segments.size - 3).toInt
        val maxId = transactionTable.getMaxTransactionNumber(cardIndex)
        val matrixCursor = new MatrixCursor(Array("max_id"))
        matrixCursor.addRow(Array[AnyRef](new Integer(maxId)))
        matrixCursor.setNotificationUri(getContext.getContentResolver, uri)
        matrixCursor
      case Route.WIDGETS_ID =>
        val segments = uri.getPathSegments
        val widgetId = segments.get(segments.size - 1).toInt
        val widgetSetting = WidgetSettings.getSettings(widgetId)
        val matrixCursor = new MatrixCursor(Array(WidgetSettings.cardIndexKey))
        matrixCursor.addRow(Array[AnyRef](new Integer(widgetSetting.cardIndex)))
        matrixCursor.setNotificationUri(getContext.getContentResolver, uri)
        matrixCursor
      case Route.CARDS_ID | Route.CARD_OVERVIEW | Route.ACTIVITIES_ID | Route.WIDGETS =>
        throw new UnsupportedOperationException("Query not supported on URI: " + uri)
      case _ =>
        throw new UnsupportedOperationException("Unknown uri: " + uri)
    }

    result
  }

  override def insert(uri: Uri, values: ContentValues): Uri = {
    val result = sUriMatcher.`match`(uri) match {
      case Route.WIDGETS =>
        val widgetId = values.getAsInteger(WidgetSettings.widgetIdKey)
        val cardIndex = values.getAsInteger(WidgetSettings.cardIndexKey)
        WidgetSettings.setSetting(widgetId, WidgetSettings(cardIndex = cardIndex))

        Uris.widget(widgetId)
      case _ =>
        throw new UnsupportedOperationException("Unknown uri: " + uri)
    }

    result
  }

  override def bulkInsert(uri: Uri, values: Array[ContentValues]): Int = sUriMatcher.`match`(uri) match {
    case Route.ACTIVITIES =>
      val segments = uri.getPathSegments
      val cardIndex = segments.get(segments.size - 2).toInt

      transactionTable.bulkInsert(cardIndex)(values)

      getContext.getContentResolver.notifyChange(uri, null, false)
      getContext.getContentResolver.notifyChange(Uris.activitiesMaxId(cardIndex), null, false)
      values.size
    case Route.CARDS | Route.CARDS_ID | Route.CARD_OVERVIEW | Route.ACTIVITIES_ID | Route.ACTIVITIES_MAX_ID =>
      throw new UnsupportedOperationException("BulkInsert not supported on URI: " + uri)
    case _ =>
      throw new UnsupportedOperationException("Unknown uri: " + uri)
  }

  override def delete(uri: Uri, selection: String, selectionArgs: Array[String]): Int = sUriMatcher.`match`(uri) match {
    case Route.ACTIVITIES =>
      val db = transactionTable.getWritableDatabase
      try {
        TransactionTable.clearAllTables(db)
      } finally {
        db.close
      }
      1
    case Route.WIDGETS =>
      val widgetIds = selectionArgs.map { _.toInt }
      WidgetSettings.removeUnusedSettings(widgetIds)
    case _ =>
      throw new UnsupportedOperationException("Unknown uri: " + uri)
  }

  override def update(uri: Uri, values: ContentValues, selection: String,
    selectionArgs: Array[String]): Int = {
    val result = sUriMatcher.`match`(uri) match {
      case Route.CARDS =>
        CardsCache.updateCards(values.getAsString(CardsCache.cardCacheKey))
        getContext.getContentResolver.notifyChange(uri, null, false)
        1
      case Route.CARDS_ID | Route.CARD_OVERVIEW | Route.ACTIVITIES | Route.ACTIVITIES_ID | Route.ACTIVITIES_MAX_ID =>
        throw new UnsupportedOperationException("Query not supported on URI: " + uri)
      case _ =>
        throw new UnsupportedOperationException("Unknown uri: " + uri)
    }

    result
  }
}

object OpalProvider {
  val AUTHORITY = BuildConfig.APPLICATION_ID + ".provider";

  object Route {
    val CARDS = 1
    val CARDS_ID = 2
    val ACTIVITIES = 3
    val ACTIVITIES_ID = 4
    val ACTIVITIES_MAX_ID = 5
    val CARD_OVERVIEW = 6
    val WIDGETS = 7
    val WIDGETS_ID = 8
  }

  object Type {
    val ACTIVITY = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.timgreen.opal.activity"
    val ACTIVITY_ITEM = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.timgreen.opal.activity"
    val ACTIVITY_MAX_ID_ITEM = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.timgreen.opal.activity.maxid"
    val CARD = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.timgreen.opal.card"
    val CARD_ITEM = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.timgreen.opal.card"
    val CARD_OVERVIEW_ITEM = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.timgreen.opal.card.overview"
    val WIDGET = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.timgreen.opal.widget"
    val WIDGET_ITEM = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.timgreen.opal.widget"
  }

  object Uris {
    val base = Uri.parse("content://" + AUTHORITY)
    val cards = base.buildUpon.appendPath("cards").build
    def card(cardIndex: Int) = cards.buildUpon
      .appendPath(cardIndex.toString)
      .build
    def activities(cardIndex: Int) = card(cardIndex).buildUpon
      .appendPath("activities")
      .build
    def activitiesMaxId(cardIndex: Int) = activities(cardIndex).buildUpon
      .appendPath("maxid")
      .build
    def overview(cardIndex: Int) = card(cardIndex).buildUpon
      .appendPath("overview")
      .build
    def widgets = base.buildUpon
      .appendPath("widgets")
      .build
    def widget(widgetId: Int) = widgets.buildUpon
      .appendPath(widgetId.toString)
      .build
  }

  private val sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH)
  sUriMatcher.addURI(AUTHORITY, "cards", Route.CARDS)
  sUriMatcher.addURI(AUTHORITY, "cards/#", Route.CARDS_ID)
  sUriMatcher.addURI(AUTHORITY, "cards/#/activities", Route.ACTIVITIES)
  sUriMatcher.addURI(AUTHORITY, "cards/#/activities/#", Route.ACTIVITIES_ID)
  sUriMatcher.addURI(AUTHORITY, "cards/#/activities/maxid", Route.ACTIVITIES_MAX_ID)
  sUriMatcher.addURI(AUTHORITY, "cards/#/overview", Route.CARD_OVERVIEW)
  sUriMatcher.addURI(AUTHORITY, "widgets", Route.WIDGETS)
  sUriMatcher.addURI(AUTHORITY, "widgets/#", Route.WIDGETS_ID)
}
