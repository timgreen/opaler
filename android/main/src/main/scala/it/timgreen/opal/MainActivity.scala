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
import android.graphics.PorterDuff
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.support.v13.app.FragmentPagerAdapter
import android.support.v4.content.FileProvider
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.AdapterView
import android.widget.ListView
import com.google.android.gms.plus.PlusOneButton

import com.afollestad.materialdialogs.MaterialDialog
import com.amulyakhare.textdrawable.TextDrawable
import com.amulyakhare.textdrawable.util.ColorGenerator
import com.github.johnpersano.supertoasts.SuperCardToast
import com.github.johnpersano.supertoasts.SuperToast
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.accountswitcher.AccountHeader
import com.mikepenz.materialdrawer.accountswitcher.AccountHeaderBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile

import it.timgreen.android.billing.InAppBilling
import it.timgreen.android.gms.PlayServiceHelper
import it.timgreen.android.model.ValueModel
import it.timgreen.android.net.NetworkConnectionChecker
import it.timgreen.android.util.Snapshot
import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.provider.CardsCache
import it.timgreen.opal.provider.OpalProvider
import it.timgreen.opal.provider.TransactionTable
import it.timgreen.opal.sync.Observer
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
  with RateSupport {

  import Bus._

  override val translucentStatus = true
  implicit def provideActivity = this

  var drawerMenu: Option[ListView] = None
  var viewPager: Option[ViewPager] = None
  var plusOneButton: Option[PlusOneButton] = None

  def reloadOp() {
    Util.debug(s"reloadOp ${currentCardIndex()}")
    getFragment(0) foreach { _.refresh }
    getFragment(1) foreach { _.refresh }
    updateActionBarTitle
  }
  def startRefreshOp() {
    SuperCardToast.cancelAllSuperCardToasts
  }
  def endRefreshOp() {
    // NOTE(timgreen): this is a hack for startRefreshOp haven't cancel toasts.
    SuperCardToast.cancelAllSuperCardToasts

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
                MainActivity.this.startSync
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
                MainActivity.this.startSync
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
                MainActivity.this.startSync
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
                MainActivity.this.startSync
              }
            })
          )
          t.show
      }
    }
  }

  private def getFragment(index: Int): Option[Fragment with RefreshOps with SnapshotAware] = {
    viewPager flatMap { vp =>
      val tag = s"android:switcher:${vp.getId}:$index"
      Option(getFragmentManager.findFragmentByTag(tag).asInstanceOf[Fragment with RefreshOps with SnapshotAware])
    }
  }

  val syncSyncStatusOp = { () =>
    runOnUiThread(new Runnable {
      override def run() {
        val isSyncingBefore = isSyncing()
        isSyncing() = AccountUtil.getAccount map { account =>
          Observer.isSyncActive(account, OpalProvider.AUTHORITY)
        } getOrElse false
        Util.debug(s"sync status op: $isSyncingBefore -> ${isSyncing()}")

        if (isSyncingBefore != isSyncing()) {
          if (!isSyncing()) {
            // TODO(timgreen): optimise it
            reloadOp
          }
        }
      }
    })
  }

  val syncObserver = new Observer(syncSyncStatusOp)

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
        drawer.setSelectionByIdentifier(position + 1, false)
        updateActionBarTitle
      }
    })

    setupObserver
    updateActionBarTitle

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
  }

  override def onSaveInstanceState(outState: Bundle) {
    val state = drawer.saveInstanceState(outState)
    super.onSaveInstanceState(state)
  }

  override def onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    Util.debug("New intent")
    if (accountsLoaded) {
      ensureInitCardIndex(intent)
    }
    if (intent.hasExtra(MainActivity.initCardIndex)) {
      trackEvent("Launch", "from_widget")
    }
  }

  override def onResume() {
    super.onResume

    syncObserver.addListener
    syncSyncStatusOp()

    if (hasLogin &&
        hasSuitableConnection &&
        (!SyncStatus.hasSyncedBefore || (enableSyncOnStart && SyncStatus.needSync))) {
      startSync
    }

    val PLUS_URL = "https://play.google.com/store/apps/details?id=it.timgreen.opal"
    plusOneButton foreach { _.initialize(PLUS_URL, 0) }

    refreshTripIfNecessary
  }

  override def onPause() {
    super.onPause
    syncObserver.removeListener
  }

  override def onDestroy() {
    drawerMenu = None
    viewPager = None

    super.onDestroy
  }

  // If left drawer is open -> close drawer
  // If on trip page        -> go to overview
  // If on overview         -> exit
  override def onBackPressed {
    if (drawer.isDrawerOpen) {
      drawer.closeDrawer
    } else if (viewPager.map(_.getCurrentItem()) == Some(1)) {
      trackEvent("UI", "switchFragment", Some("back"), Some(0))
      viewPager foreach {
        _.setCurrentItem(0)
      }
      drawerMenu foreach {
        _.setSelection(0)
      }
    } else {
      finish
    }
  }

  private var currentValueOfUse24hourFormat: Option[Boolean] = None
  def refreshTripIfNecessary() {
    val use24hourFormat = PrefUtil.use24hourFormat
    if (currentValueOfUse24hourFormat != None && currentValueOfUse24hourFormat != Some(use24hourFormat)) {
      getFragment(1) foreach { _.refresh }
    }
    currentValueOfUse24hourFormat = Some(use24hourFormat)
  }

  def hasSuitableConnection: Boolean = {
    NetworkConnectionChecker.hasConnection(this)
  }

  private var prevTitle = ""
  def updateActionBarTitle() {
    viewPager foreach { vp =>
      val title = vp.getAdapter.getPageTitle(vp.getCurrentItem)
      getSupportActionBar.setTitle(title)
      if (prevTitle != title) {
        trackView(title + "Fragment")
        prevTitle = title.toString
      }
      val subtitle = for {
        i <- currentCardIndex()
        cardDetails <- CardsCache.getCards.lift(i)
      } yield {
        s"${cardDetails.cardNickName} ${cardDetails.formatedCardNumber}"
      }
      getSupportActionBar.setSubtitle(subtitle getOrElse null)
    }
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
          updateCurrentAccount(i)
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
          .withCheckable(true),
        new PrimaryDrawerItem()
          .withName(R.string.drawer_activity)
          .withIcon(typedArray.getDrawable(1))
          .withIdentifier(Identifier.Activity)
          .withCheckable(true),
        new DividerDrawerItem(),
        new PrimaryDrawerItem()
          .withName(R.string.drawer_donate)
          .withIcon(typedArray.getDrawable(2))
          .withIdentifier(Identifier.Donate)
          .withCheckable(false),
        new PrimaryDrawerItem()
          .withName(R.string.drawer_share)
          .withIcon(typedArray.getDrawable(3))
          .withIdentifier(Identifier.Share)
          .withCheckable(false),
        new SecondaryDrawerItem()
          .withName(R.string.drawer_feedback_and_help)
          .withIcon(typedArray.getDrawable(4))
          .withIdentifier(Identifier.Feedback)
          .withCheckable(false),
        new SecondaryDrawerItem()
          .withName(R.string.drawer_settings)
          .withIcon(typedArray.getDrawable(5))
          .withIdentifier(Identifier.Settings)
          .withCheckable(false)
      )
      .withOnDrawerItemClickListener(new Drawer.OnDrawerItemClickListener() {
        override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long, drawerItem: IDrawerItem): Boolean = {
          if (drawerItem != null) {
            drawerItem.getIdentifier match {
              case id@(Identifier.Overview | Identifier.Activity) =>
                viewPager foreach { _.setCurrentItem(id - 1) }
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
      .withTranslucentActionBarCompatibility(false)
      .withSavedInstance(savedInstanceState)
      .build

    typedArray.recycle

    getLoaderManager.initLoader(0, null, new LoaderManager.LoaderCallbacks[Cursor]() {
      override def onCreateLoader(id: Int, args: Bundle): Loader[Cursor] = {
        if (id == 0) {
          new CursorLoader(MainActivity.this, OpalProvider.Uris.cards, null, null, null, null)
        } else {
          null
        }
      }

      override def onLoadFinished(loader: Loader[Cursor], cursor: Cursor) {
        val profiles = new ArrayList[IProfile[_]]()

        cursor.moveToFirst
        while (!cursor.isAfterLast) {
          val name = cursor.getString(cursor.getColumnIndex(CardsCache.Columns.cardNickName))
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
          profiles.add(
            new ProfileDrawerItem()
              .withName(name)
              .withEmail(cursor.getString(cursor.getColumnIndex(CardsCache.Columns.cardNumber)).grouped(4).mkString(" "))
              .withIcon(icon)
              .withIdentifier(cursor.getInt(cursor.getColumnIndex(CardsCache.Columns.id)))
          )
          cursor.moveToNext
        }

        header.setProfiles(profiles)

        ensureInitCardIndex(getIntent)
        accountsLoaded = true
      }

      override def onLoaderReset(loader: Loader[Cursor]) {
        header.setProfiles(new ArrayList[IProfile[_]]())
      }
    })

    // plusOneButton = Option(drawerLayout.findViewById(R.id.plus_one_button).asInstanceOf[PlusOneButton])
  }

  private def setupObserver() {
    val handler = new Handler
    val contentObserver = new ContentObserver(handler) {
      override def onChange(selfChange: Boolean, uri: Uri) {
        Util.debug("ContentObserver     ==================== " + uri)
        currentCardIndex() foreach { cardIndex =>
          if (uri == OpalProvider.Uris.activities(cardIndex)) {
            Util.debug("ContentObserver     ==================== do load")
            getFragment(0) foreach { _.refresh }
            getFragment(1) foreach { _.refresh }
          }
        }
      }

      override def onChange(selfChange: Boolean) {
        Util.debug("ContentObserver     ====================")
        getFragment(0) foreach { _.refresh }
        getFragment(1) foreach { _.refresh }
      }
    }
    // TODO(timgreen): more accurate uri.
    getContentResolver.registerContentObserver(OpalProvider.Uris.cards, true, contentObserver)

  }

  private def updateCurrentAccount(i: Int) {
    currentCardIndex() = Some(i)
    Usage.lastSelectedCard() = i
    header.setBackgroundRes(i % 4 match {
      case 0 => R.drawable.header_leaf
      case 1 => R.drawable.header_sun
      case 2 => R.drawable.header_aurora
      case 3 => R.drawable.header_ice
    })
    reloadOp
  }

  private var accountsLoaded = false
  private def ensureInitCardIndex(intent: Intent) {
    if (intent.hasExtra(MainActivity.initCardIndex)) {
      val initCardIndex = intent.getIntExtra(MainActivity.initCardIndex, 0)
      header.setActiveProfile(initCardIndex)
      updateCurrentAccount(initCardIndex)
      Util.debug("set init cardIndex: " + initCardIndex)
      intent.removeExtra(MainActivity.initCardIndex)
    } else {
      if (!accountsLoaded) {
        Util.debug("load last card: " + Usage.lastSelectedCard())
        if (header.getProfiles.size > Usage.lastSelectedCard()) {
          header.setActiveProfile(Usage.lastSelectedCard())
        }
        updateCurrentAccount(Usage.lastSelectedCard())
        trackEvent("Launch", "normal")
      }
    }
  }

  private def enableSyncOnStart() = {
    PrefUtil.syncOnStart &&
      (!PrefUtil.autoSyncOnWifiOnly || NetworkConnectionChecker.hasWifiConnection(this))
  }

  def openSettings() {
    val intent = new Intent(this, classOf[SettingActivity])
    startActivity(intent)
    val drawerLayout = findViewById(R.id.drawer_layout).asInstanceOf[DrawerLayout]
    drawerLayout.closeDrawers
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
      p.setName(s"Card: ${p.getIdentifier} / ${header.getProfiles.size}")
      p.setEmail("0000 0000 0000 0000")
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
    viewPager foreach {
      _.setCurrentItem(1)
    }
    drawer.setSelectionByIdentifier(Identifier.Activity, false)
  }

  def startSync() {
    AccountUtil.getAccount foreach { account =>
      startRefreshOp
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
