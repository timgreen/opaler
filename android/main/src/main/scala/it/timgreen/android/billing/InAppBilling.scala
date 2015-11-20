package it.timgreen.android.billing

import android.app.Activity
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import com.android.vending.billing.IInAppBillingService

import java.util.ArrayList
import org.json.JSONObject

import scala.collection.JavaConversions._

object InAppBilling {

  case class SkuDetails(
    productId: String,
    price: String
  )

  case class PurchaseData(
    orderId: String,
    packageName: String,
    productId: String,
    purchaseTime: Long,
    purchaseState: Int,
    developerPayload: String,
    purchaseToken: String
  )

  case class OwnedItem(
    sku: String,
    purchaseData: PurchaseData
  )

  private var mService: Option[IInAppBillingService] = None

  private val serviceConn = new ServiceConnection() {
    override def onServiceDisconnected(name: ComponentName) {
      mService = None
    }

    override def onServiceConnected(name: ComponentName, service: IBinder) {
      mService = Some(IInAppBillingService.Stub.asInterface(service))
    }
  }

  def bind(context: Context) {
    val intent = new Intent("com.android.vending.billing.InAppBillingService.BIND")
    intent.setPackage("com.android.vending")
    context.bindService(intent, serviceConn, Context.BIND_AUTO_CREATE)
  }

  def unbind(context: Context) {
    context.unbindService(serviceConn)
  }

  def isInAppBillingAvailable = mService.isDefined

  trait BillingSupport { activity: Activity =>
    override def onCreate(savedInstanceState: Bundle) {
      activity.onCreate(savedInstanceState)
      InAppBilling.bind(activity)
    }

    override def onDestroy() {
      InAppBilling.unbind(this)
      activity.onDestroy
    }
  }

  val IAP_VERSION = 3
  val TYPE_IN_APP = "inapp"
  val KEY_RESPONSE_CODE = "RESPONSE_CODE"
  val KEY_DETAILS_LIST = "DETAILS_LIST"
  val KEY_ITEM_ID_LIST = "ITEM_ID_LIST"

  val BILLING_RESPONSE_RESULT_OK = 0
  val BILLING_RESPONSE_RESULT_USER_CANCELED = 1
  val BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE = 2
  val BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE = 3
  val BILLING_RESPONSE_RESULT_ITEM_UNAVAILABLE = 4
  val BILLING_RESPONSE_RESULT_DEVELOPER_ERROR = 5
  val BILLING_RESPONSE_RESULT_ERROR = 6
  val BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED = 7
  val BILLING_RESPONSE_RESULT_ITEM_NOT_OWNED = 8

  def getSkuDetails(skuList: List[String], packageName: String): Option[List[SkuDetails]] = mService flatMap { service =>
    val querySkus = new Bundle
    querySkus.putStringArrayList(KEY_ITEM_ID_LIST, new ArrayList(skuList))
    val res = service.getSkuDetails(IAP_VERSION, packageName, TYPE_IN_APP, querySkus)
    if (res.getInt(KEY_RESPONSE_CODE) == 0) {
      try {
        val skuDetails = res.getStringArrayList(KEY_DETAILS_LIST) map { json =>
          val o = new JSONObject(json)
          SkuDetails(
            productId = o.getString("productId"),
            price     = o.getString("price")
          )
        } toList

        Some(skuDetails)
      } catch {
        case _: Throwable =>
          None
      }
    } else {
      None
    }
  }

  def getBuyIntent(sku: String, payload: String, packageName: String): Either[PendingIntent, Int] = mService.toLeft(BILLING_RESPONSE_RESULT_SERVICE_UNAVAILABLE).left flatMap { service =>
    val bundle = service.getBuyIntent(IAP_VERSION, packageName, sku, TYPE_IN_APP, payload)
    bundle.getInt(KEY_RESPONSE_CODE) match {
      case BILLING_RESPONSE_RESULT_OK =>
        Option(bundle.getParcelable("BUY_INTENT")).toLeft(BILLING_RESPONSE_RESULT_BILLING_UNAVAILABLE)
      case responseCode =>
        Right(responseCode)
    }
  }

  def getPurchases(packageName: String, iapType: String = TYPE_IN_APP, continuationToken: String = null): Option[List[OwnedItem]] = mService flatMap { service =>
    val ownedItems = service.getPurchases(IAP_VERSION, packageName, iapType, continuationToken)
    if (ownedItems.getInt(KEY_RESPONSE_CODE) != 0) {
      None
    } else {
      val continuationToken = ownedItems.getString("INAPP_CONTINUATION_TOKEN")
      val ownedSkus = ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST").toList
      val purchaseDatas = ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST") map { json =>
        val o = new JSONObject(json)
        PurchaseData(
          orderId          = o.getString("orderId"),
          packageName      = o.getString("packageName"),
          productId        = o.getString("productId"),
          purchaseTime     = o.getLong("purchaseTime"),
          purchaseState    = o.getInt("purchaseState"),
          developerPayload = o.getString("developerPayload"),
          purchaseToken    = o.getString("purchaseToken")
        )
      }
      val signatureList = ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE")

      Some(ownedSkus.zipWithIndex map { case (sku, i) =>
        OwnedItem(
          sku = sku,
          purchaseData = purchaseDatas(i)
        )
      })
    }
  }

  def consumePurchase(packageName: String, token: String): Option[Int] = mService flatMap { service =>
    Some(service.consumePurchase(IAP_VERSION, packageName, token))
  }
}
