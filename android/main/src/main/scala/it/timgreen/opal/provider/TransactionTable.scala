package it.timgreen.opal.provider

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.Util
import it.timgreen.opal.api.CardTransaction
import it.timgreen.opal.api.FareApplied
import it.timgreen.opal.api.Model
import it.timgreen.opal.api.TransactionDetails

import java.lang.{ Integer, Long => JLong }

import scala.collection.mutable

private [provider] class TransactionTable(context: Context) extends SQLiteOpenHelper(
  context,
  TransactionTable.databaseName,
  null,  // factory, use default
  TransactionTable.version
) {
  import TransactionTable.Entry

  override def onCreate(db: SQLiteDatabase) {
  }

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    trackEvent("DB", "Upgrade", Some("from_" + oldVersion), Some(newVersion))(context)

    db.beginTransaction
    try {
      oldVersion until newVersion foreach { v =>
        Util.debug(s"Upgrading db $v -> ${v + 1}")
        doUpgrade(db, v)
        Util.debug(s"Upgraded  db $v -> ${v + 1}")
      }
      db.setTransactionSuccessful
    } finally {
      db.endTransaction
    }
  }

  private def doUpgrade(db: SQLiteDatabase, v: Int) = v match {
    case 1 =>
      TransactionTable.dropAllTables(db)
    case 2 =>
      TransactionTable.foreachTransactionTable(db) { tableName =>
        Util.debug(s"  Add column UPDATED_TIME to table $tableName")
        db.execSQL(s"ALTER TABLE $tableName ADD COLUMN ${Entry.UPDATED_TIME} INTEGER")
      }
    case 3 =>
      TransactionTable.foreachTransactionTable(db) { tableName =>
        Util.debug(s"  Remove column raw from table $tableName")
        db.execSQL(s"CREATE TABLE temp AS SELECT * FROM $tableName")
        db.execSQL(s"DROP TABLE IF EXISTS $tableName")
        TransactionTable.createTableSqlWithTableName(tableName) foreach db.execSQL
        val columns = Seq(
          Entry.TRANSACTION_NUMBER,
          Entry.DATETIME,
          Entry.MODEL,
          Entry.DETAILS,
          Entry.JOURNEY_NUMBER,
          Entry.FARE_APPLIED,
          Entry.FARE,
          Entry.DISCOUNT,
          Entry.AMOUNT,
          Entry.WEEK_DAY,
          Entry.WEEK_NUMBER,
          Entry.JULIAN_WEEK_NUMBER,
          Entry.UPDATED_TIME
        )
        db.execSQL(s"INSERT INTO $tableName SELECT ${columns.mkString(", ")} FROM temp")
        db.execSQL(s"DROP TABLE IF EXISTS temp")
      }
    case 4 =>
      TransactionTable.foreachTransactionTable(db) { tableName =>
        db.execSQL(s"UPDATE $tableName set model='light_rail' where details LIKE '% LR to %'")
      }
  }

  def insertAll(cardIndex: Int)(items: List[CardTransaction]) {
    val updatedTime = Util.currentTimeInMs
    bulkInsert(cardIndex)(items.map(TransactionTable.toValues).toArray)
  }

  def bulkInsert(cardIndex: Int)(values: Array[ContentValues]) {
    autoCreateTableOnError(cardIndex) {
      val db = getWritableDatabase
      db.beginTransaction
      try {
        val minTransactionNumber = values.map(_.getAsInteger(Entry.TRANSACTION_NUMBER)).min
        db.delete(
          TransactionTable.tableName(cardIndex),
          s"${Entry.TRANSACTION_NUMBER} >= ?",
          Array(minTransactionNumber.toString)
        )

        values foreach { value =>
          db.insertWithOnConflict(
            TransactionTable.tableName(cardIndex),
            null,
            value,
            SQLiteDatabase.CONFLICT_REPLACE
          )
        }
        db.setTransactionSuccessful
      } finally {
        db.endTransaction
      }
    }
  }

  def query(cardIndex: Int, selection: String = null): Cursor = autoCreateTableOnError(cardIndex) {
    val db = getReadableDatabase
    db.query(
      TransactionTable.tableName(cardIndex),
      TransactionTable.columns,
      selection,
      null,
      null,  // groupBy
      null,  // having
      TransactionTable.defaultOrder,
      null   // limit
    )
  }

  def getMaxTransactionNumber(cardIndex: Int): Int = try {
    val db = getReadableDatabase
    val cursor = db.query(
      TransactionTable.tableName(cardIndex),
      Array(Entry.TRANSACTION_NUMBER),
      null,  // selection
      null,
      null,  // groupBy
      null,  // having
      TransactionTable.defaultOrder,
      "1"
    )

    val r = if (cursor.getCount > 0) {
      cursor.moveToFirst
      cursor.getInt(0)
    } else {
      0
    }
    cursor.close
    r
  } catch {
    case t: Throwable =>
      Util.debug(s"Error when getMaxTransactionNumber for card $cardIndex", t)
      0
  }

  private def autoCreateTableOnError[R](cardIndex: Int)(op: => R): R = try {
    op
  } catch {
    case _: SQLiteException =>
      val db = getWritableDatabase
      db.beginTransaction
      try {
        TransactionTable.createTableSqlWithIndex(cardIndex) foreach db.execSQL
        db.setTransactionSuccessful
      } finally {
        db.endTransaction
      }
      op
  }
}

object TransactionTable {
  val databaseName = "opal"
  val version = 5
  def tableName(cardIndex: Int) = s"transaction_$cardIndex"

  object Entry extends BaseColumns {
    val _ID                = "_id"
    val _COUNT             = "_count"
    val TRANSACTION_NUMBER = _ID
    val DATETIME           = "datetime"
    val MODEL              = "model"
    val DETAILS            = "details"
    val JOURNEY_NUMBER     = "journeyNumber"
    val FARE_APPLIED       = "fareApplied"
    val FARE               = "fare"
    val DISCOUNT           = "discount"
    val AMOUNT             = "amount"

    val WEEK_DAY           = "weekDay"
    val WEEK_NUMBER        = "weekNumber"
    val JULIAN_WEEK_NUMBER = "julianWeekNumber"

    val UPDATED_TIME       = "updatedTime"
  }

  val columns = Array(
    Entry.TRANSACTION_NUMBER,  // 0
    Entry.DATETIME,            // 1
    Entry.MODEL,               // 2
    Entry.DETAILS,             // 3
    Entry.JOURNEY_NUMBER,      // 4
    Entry.FARE_APPLIED,        // 5
    Entry.FARE,                // 6
    Entry.DISCOUNT,            // 7
    Entry.AMOUNT,              // 8

    Entry.WEEK_DAY,            // 9
    Entry.WEEK_NUMBER,         // 10
    Entry.JULIAN_WEEK_NUMBER,  // 11

    Entry.UPDATED_TIME         // 12
  )

  val defaultOrder = s"${Entry.TRANSACTION_NUMBER} DESC"

  def createTableSqlWithIndex(cardIndex: Int): Seq[String] =
    createTableSqlWithTableName(tableName(cardIndex))
  def createTableSqlWithTableName(tableName: String): Seq[String] = Seq(
    s"""
      CREATE TABLE IF NOT EXISTS ${tableName} (
        ${Entry.TRANSACTION_NUMBER} INTEGER PRIMARY KEY,
        ${Entry.DATETIME}           INTEGER NOT NuLL,
        ${Entry.MODEL}              TEXT NOT NULL,
        ${Entry.DETAILS}            TEXT NOT NULL,
        ${Entry.JOURNEY_NUMBER}     INTEGER,
        ${Entry.FARE_APPLIED}       TEXT NOT NULL,
        ${Entry.FARE}               REAL,
        ${Entry.DISCOUNT}           REAL,
        ${Entry.AMOUNT}             REAL,
        ${Entry.WEEK_DAY}           INTEGER NOT NULL,
        ${Entry.WEEK_NUMBER}        INTEGER NOT NULL,
        ${Entry.JULIAN_WEEK_NUMBER} INTEGER NOT NULL,
        ${Entry.UPDATED_TIME}       INTEGER
      );
    """,
    s"""
    CREATE INDEX IF NOT EXISTS julian_week_number_idx_$tableName
    ON ${tableName}(${Entry.JULIAN_WEEK_NUMBER});
    """
  )

  def dropTableSql(cardIndex: Int) = "DROP TABLE IF EXISTS " + tableName(cardIndex)

  def foreachTransactionTable(db: SQLiteDatabase)(op: String => Unit) {
    val c = db.rawQuery("""
      select DISTINCT tbl_name from sqlite_master where
      type == 'table' and tbl_name glob 'transaction_*' """, null)
    c.moveToFirst
    while (!c.isAfterLast) {
      val tableName = c.getString(0)
      op(tableName)
      c.moveToNext
    }
    c.close
  }

  private def dropAllTables(db: SQLiteDatabase) {
    foreachTransactionTable(db) { tableName =>
      Util.debug(s"  Drop table $tableName")
      db.execSQL(s"DROP TABLE IF EXISTS $tableName")
    }
  }

  def clearAllTables(db: SQLiteDatabase) {
    db.beginTransaction
    try {
      foreachTransactionTable(db) { tableName =>
        Util.debug(s"  Clear table $tableName")
        db.execSQL(s"DELETE FROM $tableName")
      }
      db.setTransactionSuccessful
    } finally {
      db.endTransaction
    }
  }

  def toValues(item: CardTransaction): ContentValues = {
    val values = new ContentValues
    import TransactionTable.Entry._
    values.put(TRANSACTION_NUMBER, new Integer(item.transactionNumber))
    values.put(DATETIME, new JLong(item.datetime.toMillis(false)))
    values.put(MODEL, item.model.toString)
    values.put(DETAILS, item.details.toString)
    item.journeyNumber foreach { i => values.put(JOURNEY_NUMBER, new Integer(i)) }
    values.put(FARE_APPLIED, item.fareApplied.toString)
    item.fare foreach { values.put(FARE, _) }
    item.discount foreach { values.put(DISCOUNT, _) }
    item.amount foreach { values.put(AMOUNT, _) }

    values.put(WEEK_DAY, new Integer(item.weekDay))
    values.put(WEEK_NUMBER, new Integer(item.weekNumber))
    values.put(JULIAN_WEEK_NUMBER, new Integer(item.julianWeekNumber))
    values.put(UPDATED_TIME, new JLong(item.updatedTime))

    values
  }

  def fromValues(c: Cursor): CardTransaction = {
    CardTransaction(
      transactionNumber = c.getInt(0),
      datetime          = CardTransaction.timeFromLong(c.getLong(1)),
      model             = Model(c.getString(2)),
      details           = TransactionDetails(c.getString(3)),
      journeyNumber     = if (c.isNull(4)) None else Some(c.getInt(4)),
      fareApplied       = FareApplied(c.getString(5), if (c.isNull(6)) None else Some(c.getDouble(6))),
      fare              = if (c.isNull(6)) None else Some(c.getDouble(6)),
      discount          = if (c.isNull(7)) None else Some(c.getDouble(7)),
      amount            = if (c.isNull(8)) None else Some(c.getDouble(8)),
      updatedTime       = Option(c.getLong(12)).getOrElse(0)
    )
  }

  // NOTE: this method will not close the cursor.
  def convert(c: Cursor): List[CardTransaction] = {
    val list = mutable.ListBuffer[CardTransaction]()
    if (c != null) {
      c.moveToFirst
      while (!c.isAfterLast) {
        list += fromValues(c)
        c.moveToNext
      }
    }
    list.toList
  }
}
