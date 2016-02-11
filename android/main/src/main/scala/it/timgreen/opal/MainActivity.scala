package it.timgreen.opal

import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.app.LoaderManager
import android.content.ContentResolver
import android.content.Context
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.support.v13.app.FragmentPagerAdapter
import android.support.v4.content.FileProvider
import android.support.v4.view.ViewPager
import android.support.v7.widget.Toolbar
import android.view.View
import com.google.android.gms.plus.PlusOneButton

import com.afollestad.materialdialogs.MaterialDialog
import com.amulyakhare.textdrawable.TextDrawable
import com.amulyakhare.textdrawable.util.ColorGenerator
import com.github.johnpersano.supertoasts.SuperCardToast
import com.github.johnpersano.supertoasts.SuperToast
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.SectionDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile

import rx.lang.scala.Observable
import rx.lang.scala.subjects.BehaviorSubject

import it.timgreen.android.billing.InAppBilling
import it.timgreen.android.gms.PlayServiceHelper
import it.timgreen.android.model.ListenableAwareActivity
import it.timgreen.android.model.SingleValue
import it.timgreen.android.model.Value
import it.timgreen.android.net.NetworkConnectionChecker
import it.timgreen.android.rx.RxActivity
import it.timgreen.android.util.Snapshot
import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.api.CardDetails
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.TransactionTable
import it.timgreen.opal.sync.{ Observer => SyncObserver }
import it.timgreen.opal.sync.SyncAdapter
import it.timgreen.opal.sync.SyncStatus

import java.io.File
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
  with ListenableAwareActivity
  with RxActivity {

  override val translucentStatus = true
  implicit def provideActivity = this

  // Reactive models
  import Bus.currentCardIndex
  import Bus.currentCardDetails
  import rxdata.RxSync.isSyncing
  import rxdata.RxSync.syncTrigger
  import Bus.fragmentRefreshTrigger
  import rxdata.RxCards
  val currentFragmentId = BehaviorSubject(Identifier.Overview)

  val actionBarSubtitle: Observable[DataStatus[String]] = currentCardDetails map { cardData =>
    cardData map { card =>
      s"${card.cardNickName} ${card.formatedCardNumber}"
    }
  }
  val drawerProfiles: Observable[DataStatus[ArrayList[IProfile[_]]]] = RxCards.cards map { cardsData =>
    cardsData map { cards =>
      val profiles = cards map { card =>
        val name = card.cardNickName
        val text = {
          val parts = name.split(' ').filter(_.nonEmpty)
          if (parts.length >= 2) {
            parts(0).substring(0, 1) + parts(1).substring(0, 1)
          } else {
            (name + "  ").substring(0, 2)
          }
        }
        val icon = TextDrawable.builder
          .beginConfig
            .width(512)
            .height(512)
          .endConfig
          .buildRect(text, ColorGenerator.MATERIAL.getColor(name))
        new ProfileDrawerItem()
          .withName(name)
          .withEmail(card.cardNumber.grouped(4).mkString(" "))
          .withIcon(icon)
          .withIdentifier(card.index)
      }

      new ArrayList(profiles)
    }
  }

  private var syncObserver: SyncObserver = _
  var viewPager: Option[ViewPager] = None
  var plusOneButton: Option[PlusOneButton] = None

  def reloadOp() {
    fragmentRefreshTrigger.onNext(0)
  }
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

  private def getFragment(index: Int): Option[Fragment with SnapshotAware] = {
    viewPager flatMap { vp =>
      val tag = s"android:switcher:${vp.getId}:$index"
      Option(getFragmentManager.findFragmentByTag(tag).asInstanceOf[Fragment with SnapshotAware])
    }
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_main)
    setupDrawerAndToolbar(savedInstanceState)

    val appSectionsPagerAdapter = new AppSectionsPagerAdapter(this, getFragmentManager)
    val viewPager = findViewById(R.id.pager).asInstanceOf[ViewPager]
    this.viewPager = Some(viewPager)
    viewPager.setAdapter(appSectionsPagerAdapter)
    viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      override def onPageSelected(position: Int) {
        trackEvent("UI", "switchFragment", Some("swipe"), Some(position))
        currentFragmentId.onNext(position + 1)
      }
    })

    this.syncObserver = rxdata.RxSync.createSyncObserver
    setupObserver

    isSyncing.subscribe(syncing =>
      if (!syncing) {
        // TODO(timgreen): optimise it
        reloadOp
      }
    )

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
    RxCards.reload
  }


  override def onSaveInstanceState(outState: Bundle) {
    val state = drawer.saveInstanceState(outState)
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
    actionBarSubtitle.bindToLifecycle subscribe { subtitle =>
      getSupportActionBar.setSubtitle(subtitle getOrElse null)
    }

    // Bind reactive models to view
    currentFragmentId.bindToLifecycle subscribe { i =>
      val title = getResources.getString(
        if (i != Identifier.Activity) {
          R.string.drawer_overview
        } else {
          R.string.drawer_activity
        })
      getSupportActionBar.setTitle(title)
    }

    //// Sync fragment selection
    currentFragmentId.bindToLifecycle subscribe { fragmentId =>
      drawer.setSelection(fragmentId, false)
      viewPager foreach { _.setCurrentItem(fragmentId - 1) }
    }

    ////
    currentCardIndex.bindToLifecycle subscribe { cardIndex =>
      // TODO(timgreen): remove reloadOp
      reloadOp
      Usage.lastSelectedCard() = cardIndex
    }

    //// Update drawer profiles
    drawerProfiles.combineLatest(currentCardIndex).bindToLifecycle subscribe { pair =>
      val Tuple2(profilesData, cardIndex) = pair
      // TODO(timgreen): show loading / no data
      val profiles = profilesData getOrElse new ArrayList[IProfile[_]]()
      if (header != null) {
        header.setProfiles(profiles)
        if (cardIndex < profiles.size) {
          header.setActiveProfile(cardIndex)
        }
      }
    }
    //// Update drawer background
    currentCardIndex.bindToLifecycle subscribe { cardIndex =>
      if (header != null) {
        header.setBackgroundRes(cardIndex % 4 match {
          case 0 => R.drawable.header_leaf
          case 1 => R.drawable.header_sun
          case 2 => R.drawable.header_aurora
          case 3 => R.drawable.header_ice
        })
      }
    }
  }

  override def onPause() {
    super.onPause
    syncObserver.removeListener
  }

  override def onDestroy() {
    viewPager = None

    super.onDestroy
  }

  // If left drawer is open -> close drawer
  // If on trip page        -> go to overview
  // If on overview         -> exit
  override def onBackPressed {
    if (drawer.isDrawerOpen) {
      drawer.closeDrawer
    } else {
      if (viewPager.map(_.getCurrentItem() + 1) == Some(Identifier.Overview)) {
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
      fragmentRefreshTrigger.onNext(0)
    }
    currentValueOfUse24hourFormat = Some(use24hourFormat)
  }

  def hasSuitableConnection: Boolean = {
    NetworkConnectionChecker.hasConnection(this)
  }

  var drawer: Drawer = null
  var header: AccountHeader = null

  private def setupDrawerAndToolbar(savedInstanceState: Bundle) {
    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    setSupportActionBar(toolbar)
    getSupportActionBar.setDisplayHomeAsUpEnabled(true)
    getSupportActionBar.setHomeButtonEnabled(true)

    header = new AccountHeaderBuilder()
      .withActivity(this)
      .addProfiles(
        new ProfileDrawerItem()
          .withName("No Card")
          .withEmail(" ")
          .withIcon(getResources.getDrawable(R.drawable.logo))
          .withIdentifier(0)
      )
      .withOnAccountHeaderListener(new AccountHeader.OnAccountHeaderListener() {
        override def onProfileChanged(view: View, profile: IProfile[_], current: Boolean): Boolean = {
          val i = profile.asInstanceOf[ProfileDrawerItem].getIdentifier
          currentCardIndex.onNext(i)
          true
        }
      })
      .withHeaderBackground(R.drawable.header_leaf)
      .build

    val attrs = Array[Int](
      R.attr.overview,
      R.attr.activity,
      R.attr.donate,
      R.attr.share,
      R.attr.feedback,
      R.attr.settings
    )
    val typedArray = obtainStyledAttributes(attrs)

    drawer = new DrawerBuilder()
      .withActivity(this)
      .withToolbar(toolbar)
      .withAccountHeader(header)
      .withActionBarDrawerToggle(true)
      .addDrawerItems(
        new PrimaryDrawerItem()
          .withName(R.string.drawer_overview)
          .withIcon(typedArray.getDrawable(0))
          .withIdentifier(Identifier.Overview)
          .withSelectable(true),
        new PrimaryDrawerItem()
          .withName(R.string.drawer_activity)
          .withIcon(typedArray.getDrawable(1))
          .withIdentifier(Identifier.Activity)
          .withSelectable(true),
        new PrimaryDrawerItem()
          .withName(R.string.drawer_donate)
          .withIcon(typedArray.getDrawable(2))
          .withIdentifier(Identifier.Donate)
          .withSelectable(false),
        new PrimaryDrawerItem()
          .withName(R.string.drawer_share)
          .withIcon(typedArray.getDrawable(3))
          .withIdentifier(Identifier.Share)
          .withSelectable(false)
      )
      .addStickyDrawerItems(
        new SectionDrawerItem()
          .withName(BuildConfig.VERSION_NAME)
          .setDivider(false),
        new PrimaryDrawerItem()
          .withName(R.string.drawer_feedback_and_help)
          .withIcon(typedArray.getDrawable(4))
          .withIdentifier(Identifier.Feedback)
          .withSelectable(false),
        new PrimaryDrawerItem()
          .withName(R.string.drawer_settings)
          .withIcon(typedArray.getDrawable(5))
          .withIdentifier(Identifier.Settings)
          .withSelectable(false)
      )
      .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
        override def onItemClick(view: View, position: Int, drawerItem: IDrawerItem[_]): Boolean = {
          if (drawerItem != null) {
            drawerItem.getIdentifier match {
              case id@(Identifier.Overview | Identifier.Activity) =>
                currentFragmentId.onNext(id)
                trackEvent("UI", "switchFragment", Some("drawerItemClick"), Some(id - 1))
              case Identifier.Donate   => donate
              case Identifier.Share    => share
              case Identifier.Feedback => feedback
              case Identifier.Settings => openSettings
            }
            true
          }
          false
        }
      })
      .withTranslucentStatusBar(true)
      .withSavedInstance(savedInstanceState)
      .build

    typedArray.recycle

    // plusOneButton = Option(drawerLayout.findViewById(R.id.plus_one_button).asInstanceOf[PlusOneButton])
  }

  private def setupObserver() {
    // TODO(timgreen): turn this into observable
    // val handler = new Handler
    // val contentObserver = new ContentObserver(handler) {
    //   override def onChange(selfChange: Boolean, uri: Uri) {
    //     Util.debug("ContentObserver     ==================== " + uri)
    //     currentCardIndex() foreach { cardIndex =>
    //       if (uri == OpalProvider.Uris.activities(cardIndex)) {
    //         Util.debug("ContentObserver     ==================== do load")
    //         fragmentRefreshTrigger.onNext(0)
    //       }
    //     }
    //   }

    //   override def onChange(selfChange: Boolean) {
    //     Util.debug("ContentObserver     ====================")
    //     fragmentRefreshTrigger.onNext(0)
    //   }
    // }
    // // TODO(timgreen): more accurate uri.
    // getContentResolver.registerContentObserver(OpalProvider.Uris.cards, true, contentObserver)
  }

  private def enableSyncOnStart() = {
    PrefUtil.syncOnStart &&
      (!PrefUtil.autoSyncOnWifiOnly || NetworkConnectionChecker.hasWifiConnection(this))
  }

  def openSettings() {
    val intent = new Intent(this, classOf[SettingActivity])
    startActivity(intent)
    drawer.closeDrawer
  }

  def donate() {
    InAppBilling.getBuyIntent("buy_timgreen_a_cake", new Date().toString, "it.timgreen.opal") match {
      case Left(pendingIntent) =>
        startIntentSenderForResult(pendingIntent.getIntentSender, 1001, new Intent, 0, 0, 0)
      case Right(r) =>
        if (r == InAppBilling.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
          SuperToast.create(this, "You have already donated, Thanks!", SuperToast.Duration.SHORT).show
        } else {
          SuperToast.create(this, "Can not make donate, please try again later.", SuperToast.Duration.SHORT).show
        }
    }
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

  def share() {
    val intent = new Intent(Intent.ACTION_SEND)
    intent.setType("text/plain")
    intent.putExtra(Intent.EXTRA_SUBJECT, "Check out \"Opaler\"")
    intent.putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=it.timgreen.opal")
    startActivity(Intent.createChooser(intent, "Share Opaler"))
  }

  def feedback() {
    new MaterialDialog.Builder(this)
      .title(getResources.getString(R.string.drawer_feedback_and_help))
      .items(Array("Call Opal Customer Care", "Email Opal Customer Care", "Write to Developer"))
      .itemsCallback(new MaterialDialog.ListCallback() {
        override def onSelection(dialog: MaterialDialog, which: Int, text: String) {
          which match {
            case 0 => dialOpal
            case 1 => opalFeedback
            case 2 => appFeedback
          }
        }
      })
      .build
      .show
  }

  def dialOpal() {
    val intent = new Intent(Intent.ACTION_DIAL)
    intent.setData(Uri.parse("tel:136725"))
    startActivity(Intent.createChooser(intent, "Call Opal Customer Care"))
  }

  def opalFeedback() {
    val intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "opalcustomercare@opal.com.au", null))
    intent.putExtra(Intent.EXTRA_SUBJECT, "Opal Customer Care")
    startActivity(Intent.createChooser(intent, "Send Feedback to Opal Customer Care"))
  }

  def appFeedback() {
    SuperToast.create(this, "Preparing snapshots ...", SuperToast.Duration.SHORT).show

    new Handler().postDelayed(new Runnable() {
      override def run() {
        val intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_EMAIL, Array("iamtimgreen+playdev.opaler@gmail.com"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "Opaler Feedback")
        intent.putExtra(Intent.EXTRA_TEXT, s"\n\n\n--------\nOpaler version: ${BuildConfig.VERSION_NAME}")

        runOnUiThread(new Runnable() {
          override def run() {
            val snapshotOverview = fragmentSnapshot(0, "overview.png")
            val snapshotActivities = fragmentSnapshot(1, "activities.png")
            val snapshotDrawer = drawerSnapshot("drawer.png")
            val snapshots = snapshotOverview.toList ::: snapshotActivities.toList ::: snapshotDrawer.toList
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList(snapshots))

            val mailApps = getPackageManager.queryIntentActivities(
              new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "", null)),
              PackageManager.MATCH_DEFAULT_ONLY
            ).map(_.activityInfo.packageName).toSet
            Util.debug("mail apps: " + mailApps.mkString(", "))

            val supportedMailApps = getPackageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) map {
              _.activityInfo.packageName
            } filter { pn =>
              mailApps.contains(pn)
            }

            supportedMailApps foreach { packageName =>
              snapshots foreach { uri =>
                grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
            }

            if (supportedMailApps.nonEmpty) {
              val targetIntents = supportedMailApps map { pn =>
                new Intent(intent).setPackage(pn)
              }
              val chooserIntent = Intent.createChooser(targetIntents.head, "Send Feedback to Opaler Developer")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.tail.toArray)
              startActivity(chooserIntent)
            } else {
              SuperToast.create(MainActivity.this, "No available mail app.", SuperToast.Duration.SHORT).show
            }
            // recreate to 'fix' the snapshot masks
            recreate
          }
        })
      }
    }, 100)
  }

  private def fragmentSnapshot(index: Int, filename: String): Option[Uri] = getFragment(index) map { fragment =>
    fragment.preSnapshot
    snapshot(fragment.getView, filename)
  }

  private def drawerSnapshot(filename: String): Option[Uri] = {
    val drawerLayout = drawer.getSlider
    // remove card name from snapshot
    header.getProfiles foreach { p =>
      p.withName(s"Card: ${p.getIdentifier} / ${header.getProfiles.size}")
      p.withEmail("0000 0000 0000 0000")
    }
    // NOTE(timgreen): trigger protected header.updateHeaderAndList
    header.setProfiles(header.getProfiles)
    Some(snapshot(drawerLayout, filename))
  }

  private def snapshot(view: View, filename: String): Uri = {
    val bitmap = Snapshot.getSnapshot(view)
    val file = openFileOutput(filename, Context.MODE_PRIVATE)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, file)
    file.close
    Util.debug(getFilesDir() + "/" + filename)
    val fileUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileprovider", new File(getFilesDir(), filename))
    Util.debug(fileUri.toString)

    fileUri
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
  syncTrigger.subscribe { _ => startSync }

  object Identifier {
    val Overview = 1  // same as position + 1 in viewPager
    val Activity = 2  // same as position + 1 in ViewPager
    val Donate   = 3
    val Share    = 4
    val Feedback = 5
    val Settings = 6
  }
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
