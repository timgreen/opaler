package it.timgreen.opal

import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.content.ContentResolver
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.support.v13.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.view.View
import com.google.android.gms.plus.PlusOneButton

import com.github.johnpersano.supertoasts.SuperCardToast
import com.github.johnpersano.supertoasts.SuperToast

import rx.android.schedulers.AndroidSchedulers
import rx.lang.scala.JavaConversions._
import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.android.billing.InAppBilling
import it.timgreen.android.gms.PlayServiceHelper
import it.timgreen.android.net.NetworkConnectionChecker
import it.timgreen.android.rx.RxActivity
import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.TransactionTable
import it.timgreen.opal.rxdata.BackgroundThread
import it.timgreen.opal.sync.SyncAdapter
import it.timgreen.opal.sync.SyncStatus
import it.timgreen.opal.sync.{ Observer => SyncObserver }

import java.util.{ List => JList, Date, ArrayList }

import scala.collection.JavaConversions._

object MainActivity {
  val initCardIndex = "it.timgreen.opal.InitCardIndex"
}

class MainActivity extends ThemedActivity
  with AccountHelper.Checker
  with AccountHelper.Operator
  with Ads.BottomBanner
  with InAppBilling.BillingSupport
  with RateSupport
  with RxActivity {

  override val translucentStatus = true
  implicit def provideActivity = this

  // Reactive models
  import rxdata.RxCards
  import rxdata.RxCards.currentCardIndex
  import rxdata.RxCards.currentCard
  import rxdata.RxSync.isSyncing
  import rxdata.RxSync.syncTrigger
  import rxdata.RxSync.dataReloadTrigger
  import rxdata.RxTransactions
  val currentFragmentId = BehaviorSubject(Identifier.Overview)

  val actionBarSubtitle: Observable[DataStatus[String]] = currentCard
    .subscribeOn(BackgroundThread.scheduler)
    .observeOn(BackgroundThread.scheduler)
    .map { cardData =>
      cardData map { card =>
        s"${card.cardNickName} ${card.formatedCardNumber}"
      }
    }

  private var syncObserver: SyncObserver = _
  var viewPager: ViewPager = _
  var plusOneButton: Option[PlusOneButton] = None

  def endRefreshOp() {
    // NOTE(timgreen): this is a hack for startRefreshOp haven't cancel toasts.
    SuperCardToast.cancelAllSuperCardToasts
    rxdata.RxSync.markIsSyncing(false)

    SyncStatus.getSyncResult foreach { syncStatus =>
      Util.debug("Show SuperCardToast")
      syncStatus.resultType match {
        case SyncStatus.ResultType.success =>
          // good, ignore
        case SyncStatus.ResultType.ioError =>
          val t = new SuperCardToast(MainActivity.this, SuperToast.Type.BUTTON)
          t.setText("Network error, please try again")
          t.setIndeterminate(true)
          t.setSwipeToDismiss(true)
          t.setButtonIcon(SuperToast.Icon.Dark.REDO, "Retry")
          t.setOnClickWrapper(
            new com.github.johnpersano.supertoasts.util.OnClickWrapper("retry", new SuperToast.OnClickListener() {
              override def onClick(view: View, token: Parcelable) {
                syncTrigger.onNext(0)
              }
            })
          )
          t.show
        case SyncStatus.ResultType.serverError =>
          val t = new SuperCardToast(MainActivity.this, SuperToast.Type.BUTTON)
          t.setText("Oops, opal.com.au is out of service, please try again later")
          t.setIndeterminate(true)
          t.setSwipeToDismiss(true)
          t.setButtonIcon(SuperToast.Icon.Dark.REDO, "Retry")
          t.setOnClickWrapper(
            new com.github.johnpersano.supertoasts.util.OnClickWrapper("retry", new SuperToast.OnClickListener() {
              override def onClick(view: View, token: Parcelable) {
                syncTrigger.onNext(0)
              }
            })
          )
          t.show
        case SyncStatus.ResultType.serverApiChanged =>
          val t = new SuperCardToast(MainActivity.this, SuperToast.Type.BUTTON)
          t.setText("Oops, something wrong when connect to opal.com.au, please try again later")
          t.setIndeterminate(true)
          t.setSwipeToDismiss(true)
          t.setButtonIcon(SuperToast.Icon.Dark.REDO, "Retry")
          t.setOnClickWrapper(
            new com.github.johnpersano.supertoasts.util.OnClickWrapper("retry", new SuperToast.OnClickListener() {
              override def onClick(view: View, token: Parcelable) {
                syncTrigger.onNext(0)
              }
            })
          )
          t.show
        case SyncStatus.ResultType.loginFailed =>
          val t = new SuperCardToast(MainActivity.this, SuperToast.Type.BUTTON)
          t.setText("Login failed, please sign out & re-login")
          t.setIndeterminate(true)
          t.setSwipeToDismiss(true)
          t.setButtonIcon(SuperToast.Icon.Dark.REDO, "Relogin")
          t.setOnClickWrapper(
            new com.github.johnpersano.supertoasts.util.OnClickWrapper("relogin", new SuperToast.OnClickListener() {
              override def onClick(view: View, token: Parcelable) {
                AccountUtil.removeAllAccount
                clearAppData
                startLoginActivity
                finish
              }
            })
          )
          t.show
        case _ =>
          val t = new SuperCardToast(MainActivity.this, SuperToast.Type.BUTTON)
          t.setText("Sync Error, please try again")
          t.setIndeterminate(true)
          t.setSwipeToDismiss(true)
          t.setButtonIcon(SuperToast.Icon.Dark.REDO, "Retry")
          t.setOnClickWrapper(
            new com.github.johnpersano.supertoasts.util.OnClickWrapper("retry", new SuperToast.OnClickListener() {
              override def onClick(view: View, token: Parcelable) {
                syncTrigger.onNext(0)
              }
            })
          )
          t.show
      }
    }
  }

  def getFragment(index: Int): Option[Fragment with SnapshotAware] = {
    val tag = s"android:switcher:${viewPager.getId}:$index"
    Option(getFragmentManager.findFragmentByTag(tag).asInstanceOf[Fragment with SnapshotAware])
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)
    navDrawer = new NavDrawer(this, savedInstanceState)

    val appSectionsPagerAdapter = new AppSectionsPagerAdapter(this, getFragmentManager)
    this.viewPager = findViewById(R.id.pager).asInstanceOf[ViewPager]
    viewPager.setAdapter(appSectionsPagerAdapter)
    viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      override def onPageSelected(position: Int) {
        trackEvent("UI", "switchFragment", Some("swipe"), Some(position))
        currentFragmentId.onNext(position + 1)
      }
    })

    this.syncObserver = rxdata.RxSync.createSyncObserver
    setupObserver

    isSyncing
      .subscribeOn(BackgroundThread.scheduler)
      .observeOn(BackgroundThread.scheduler)
      .subscribe { syncing =>
        if (!syncing) {
          // TODO(timgreen): optimise it
          dataReloadTrigger.onNext(this)
        }
      }

    setCustomDimensions(
      dimensionAd                   -> (if (PrefUtil.isAdDisabled) "disabled" else "enabled"),
      dimensionDbVersion            -> TransactionTable.version.toString,
      dimensionPlayServiceVersion   -> PlayServiceHelper.version(this).toString,
      dimensionPlayServiceVersionOK -> PlayServiceHelper.versionOk(this).toString,
      dimensionAdClicked            -> (if (Ads.hasClickedBottomBannerAdBefore) "bottom_banner" else "none")
    )
    setCustomMetrics(
      metricNumOfCards              -> Usage.numOfCards(),
      metricNumOfTransaction        -> Usage.numOfTransaction(),
      metricNumberOfWidgets         -> Usage.numOfWidgets()
    )

    // TODO(timgreen): find a better way to init the value.

    currentCardIndex.onNext(getInitCardIndex(getIntent) getOrElse Usage.lastSelectedCard())
    RxCards.loadData
    RxTransactions.reload
  }


  override def onSaveInstanceState(outState: Bundle) {
    val state = navDrawer.drawer.saveInstanceState(outState)
    super.onSaveInstanceState(state)
  }

  private def getInitCardIndex(intent: Intent): Option[Int] = {
    if (intent.hasExtra(MainActivity.initCardIndex)) {
      Some(intent.getIntExtra(MainActivity.initCardIndex, 0))
    } else {
      None
    }
  }

  override def onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    Util.debug("New intent")
    getInitCardIndex(intent) foreach { initCardIndex =>
      Util.debug("New intent: " + initCardIndex)
      currentCardIndex.onNext(initCardIndex)
      trackEvent("Launch", "from_widget")
    }
  }

  override def onResume() {
    super.onResume

    syncObserver.addListener

    if (hasLogin &&
        hasSuitableConnection &&
        (!SyncStatus.hasSyncedBefore || (enableSyncOnStart && SyncStatus.needSync))) {
      syncTrigger.onNext(0)
    }

    val PLUS_URL = "https://play.google.com/store/apps/details?id=it.timgreen.opal"
    plusOneButton foreach { _.initialize(PLUS_URL, 0) }

    refreshTripIfNecessary

    //// Update ActionBar
    actionBarSubtitle.bindToLifecycle
      .subscribeOn(BackgroundThread.scheduler)
      .observeOn(AndroidSchedulers.mainThread)
      .subscribe { subtitle =>
        getSupportActionBar.setSubtitle(subtitle getOrElse null)
      }

    // Bind reactive models to view
    currentFragmentId.bindToLifecycle
      .subscribeOn(BackgroundThread.scheduler)
      .observeOn(AndroidSchedulers.mainThread)
      .subscribe { i =>
        val title = getResources.getString(
          if (i != Identifier.Activity) {
            R.string.drawer_overview
          } else {
            R.string.drawer_activity
          })
        getSupportActionBar.setTitle(title)
      }

    //// Sync fragment selection
    currentFragmentId.bindToLifecycle
      .subscribeOn(BackgroundThread.scheduler)
      .observeOn(AndroidSchedulers.mainThread)
      .subscribe { fragmentId =>
        navDrawer.drawer.setSelection(fragmentId, false)
        viewPager.setCurrentItem(fragmentId - 1)
      }

    ////
    currentCardIndex.bindToLifecycle
      .subscribeOn(BackgroundThread.scheduler)
      .observeOn(BackgroundThread.scheduler)
      .subscribe { cardIndex =>
        Usage.lastSelectedCard() = cardIndex
      }

    navDrawer.bindToLifecycleAndSubscribe
  }

  override def onPause() {
    super.onPause
    syncObserver.removeListener
  }

  // If left drawer is open -> close drawer
  // If on trip page        -> go to overview
  // If on overview         -> exit
  override def onBackPressed {
    if (navDrawer.drawer.isDrawerOpen) {
      navDrawer.drawer.closeDrawer
    } else {
      if (viewPager.getCurrentItem() + 1 != Identifier.Overview) {
        trackEvent("UI", "switchFragment", Some("back"), Some(0))
        currentFragmentId.onNext(Identifier.Overview)
      } else {
        finish
      }
    }
  }

  private var currentValueOfUse24hourFormat: Option[Boolean] = None
  def refreshTripIfNecessary() {
    val use24hourFormat = PrefUtil.use24hourFormat
    if (currentValueOfUse24hourFormat != None && currentValueOfUse24hourFormat != Some(use24hourFormat)) {
      dataReloadTrigger.onNext(this)
    }
    currentValueOfUse24hourFormat = Some(use24hourFormat)
  }

  def hasSuitableConnection: Boolean = {
    NetworkConnectionChecker.hasConnection(this)
  }

  var navDrawer: NavDrawer = null

  private def setupObserver() {
    // TODO(timgreen): turn this into observable
    // val handler = new Handler
    // val contentObserver = new ContentObserver(handler) {
    //   override def onChange(selfChange: Boolean, uri: Uri) {
    //     Util.debug("ContentObserver     ==================== " + uri)
    //     currentCardIndex() foreach { cardIndex =>
    //       if (uri == OpalProvider.Uris.activities(cardIndex)) {
    //         Util.debug("ContentObserver     ==================== do load")
    //         dataReloadTrigger.onNext(this)
    //       }
    //     }
    //   }

    //   override def onChange(selfChange: Boolean) {
    //     Util.debug("ContentObserver     ====================")
    //     dataReloadTrigger.onNext(this)
    //   }
    // }
    // // TODO(timgreen): more accurate uri.
    // getContentResolver.registerContentObserver(OpalProvider.Uris.cards, true, contentObserver)
  }

  private def enableSyncOnStart() = {
    PrefUtil.syncOnStart &&
      (!PrefUtil.autoSyncOnWifiOnly || NetworkConnectionChecker.hasWifiConnection(this))
  }

  override protected def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    if (requestCode == 1001) {
      val responseCode = data.getIntExtra("RESPONSE_CODE", 0)
      val purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA")
      val dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE")

      if (resultCode == Activity.RESULT_OK && responseCode == InAppBilling.BILLING_RESPONSE_RESULT_OK) {
        SuperToast.create(this, "Thanks for the cake :-)", SuperToast.Duration.SHORT).show
      }
    }
  }

  def spending(view: View) {
    trackEvent("UI", "switchFragment", Some("clickSpending"), Some(1))
    currentFragmentId.onNext(Identifier.Activity)
  }

  private def startSync() {
    AccountUtil.getAccount foreach { account =>
      SuperCardToast.cancelAllSuperCardToasts
      ContentResolver.cancelSync(account, OpalProvider.AUTHORITY)
      if (hasSuitableConnection) {
        val extras = new Bundle()
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        extras.putBoolean(SyncAdapter.isManualSyncKey, true)
        ContentResolver.requestSync(account, OpalProvider.AUTHORITY, extras)
      } else {
        endRefreshOp
        val t = SuperCardToast.create(MainActivity.this, "No network, please try again later.", SuperToast.Duration.LONG)
        t.setSwipeToDismiss(true)
        t.show
      }
    }
  }
  syncTrigger
    .subscribeOn(BackgroundThread.scheduler)
    .observeOn(AndroidSchedulers.mainThread)
    .subscribe { _ => startSync }
}

class AppSectionsPagerAdapter(activity: MainActivity, fm: FragmentManager) extends FragmentPagerAdapter(fm) {
  override def getCount = 2

  override def getItem(i: Int): Fragment = {
    val fragment = if (i == 0) {
      new OverviewFragment
    } else {
      new TripFragment
    }
    fragment
  }

  override def getPageTitle(position: Int): CharSequence = {
    if (position == 0) {
      activity.getResources.getString(R.string.drawer_overview)
    } else {
      activity.getResources.getString(R.string.drawer_activity)
    }
  }
}
