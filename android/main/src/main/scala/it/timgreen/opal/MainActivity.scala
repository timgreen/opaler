package it.timgreen.opal

import android.app.Activity
import android.app.Fragment
import android.app.FragmentManager
import android.app.LoaderManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.content.CursorLoader
import android.content.Intent
import android.content.Loader
import android.content.res.Configuration
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.support.v13.app.FragmentPagerAdapter
import android.support.v4.app.ActionBarDrawerToggle
import android.support.v4.content.FileProvider
import android.support.v4.view.ViewPager
import android.support.v4.widget.DrawerLayout
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.SimpleCursorAdapter
import android.widget.Spinner
import android.widget.TextView
import com.google.android.gms.plus.PlusOneButton

import com.github.johnpersano.supertoasts.SuperCardToast
import com.github.johnpersano.supertoasts.SuperToast

import it.timgreen.android.billing.InAppBilling
import it.timgreen.android.gms.PlayServiceHelper
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

import java.util.{ List => JList, Date, ArrayList }
import java.io.File

import scala.collection.JavaConversions._

object MainActivity {
  val initCardIndex = "it.timgreen.opal.InitCardIndex"
}

class MainActivity extends Activity
  with AccountHelper.Checker
  with AccountHelper.Operator
  with Ads.BottomBanner
  with InAppBilling.BillingSupport {

  implicit def provideActivity = this

  var drawerToggle: Option[ActionBarDrawerToggle] = None
  var drawerMenu: Option[ListView] = None
  var viewPager: Option[ViewPager] = None
  var spinner: Option[Spinner] = None
  var emptyCardSpinner: Option[TextView] = None
  var plusOneButton: Option[PlusOneButton] = None
  def reloadOp() {
    Util.debug(s"reloadOp $currentCardIndex")
    getFragment(0) foreach { _.refresh(currentCardIndex) }
    getFragment(1) foreach { _.refresh(currentCardIndex) }
    updateActionBarTitle
    updateEmptyCardText
  }
  def startRefreshOp() {
    SuperCardToast.cancelAllSuperCardToasts
    getFragment(0) foreach { _.onRefreshStart }
    getFragment(1) foreach { _.onRefreshStart }
  }
  def endRefreshOp() {
    // NOTE(timgreen): this is a hack for startRefreshOp haven't cancel toasts.
    SuperCardToast.cancelAllSuperCardToasts
    getFragment(0) foreach { _.onRefreshEnd }
    getFragment(1) foreach { _.onRefreshEnd }

    SyncStatus.getSyncResult foreach { syncStatus =>
      Util.debug("Show SuperCardToast")
      // TODO(timgreen): text
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

  private def getFragment(index: Int): Option[Fragment with RefreshOps with SwipeRefreshSupport with SnapshotAware] = {
    viewPager flatMap { vp =>
      val tag = s"android:switcher:${vp.getId}:$index"
      Option(getFragmentManager.findFragmentByTag(tag).asInstanceOf[Fragment with RefreshOps with SwipeRefreshSupport with SnapshotAware])
    }
  }

  var isSyncing = false
  val syncSyncStatusOp = { () =>
    runOnUiThread(new Runnable {
      override def run() {
        val isSyncingBefore = isSyncing
        isSyncing = AccountUtil.getAccount map { account =>
          Observer.isSyncActive(account, OpalProvider.AUTHORITY)
        } getOrElse false
        Util.debug(s"sync status op: $isSyncingBefore -> $isSyncing")

        if (isSyncingBefore != isSyncing) {
          if (isSyncing) {
            startRefreshOp
          } else {
            endRefreshOp
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

    val drawerLayout = findViewById(R.id.drawer_layout).asInstanceOf[DrawerLayout]
    setupDrawer(drawerLayout)

    val appSectionsPagerAdapter = new AppSectionsPagerAdapter(this, getFragmentManager)
    val viewPager = drawerLayout.findViewById(R.id.pager).asInstanceOf[ViewPager]
    this.viewPager = Some(viewPager)
    viewPager.setAdapter(appSectionsPagerAdapter)
    viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      override def onPageSelected(position: Int) {
        trackEvent("UI", "switchFragment", Some("swipe"), Some(position))
        drawerMenu foreach {
          _.setSelection(position)
        }
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

  override def onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    Util.debug("New intent")
    if (spinnerLoaded) {
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
    drawerToggle = None
    drawerMenu = None
    viewPager = None

    super.onDestroy
  }

  override protected def onPostCreate(savedInstanceState: Bundle) {
    super.onPostCreate(savedInstanceState)
    drawerToggle.map(_.syncState)
  }

  override def onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    drawerToggle.map(_.onConfigurationChanged(newConfig))
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    if (drawerToggle.map(_.onOptionsItemSelected(item)).isDefined) {
      // Pass the event to ActionBarDrawerToggle, if it returns
      // true, then it has handled the app icon touch event
      true
    } else {
      // Handle your other action bar items...
      super.onOptionsItemSelected(item)
    }
  }

  // (TODO) If left drawer is open -> close drawer
  // If on trip page        -> go to overview
  // If on overview         -> exit
  override def onBackPressed {
    if (viewPager.map(_.getCurrentItem()) == Some(1)) {
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
      getFragment(1) foreach { _.refresh(currentCardIndex) }
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
      getActionBar.setTitle(title)
      if (prevTitle != title) {
        trackView(title + "Fragment")
        prevTitle = title.toString
      }
      val subtitle = for {
        i <- currentCardIndex
        cardDetails <- CardsCache.getCards.lift(i)
      } yield {
        s"${cardDetails.cardNickName} ${cardDetails.formatedCardNumber}"
      }
      getActionBar.setSubtitle(subtitle getOrElse null)
    }
  }

  var currentCardIndex: Option[Int] = None
  private def setupDrawer(drawerLayout: DrawerLayout) {
    drawerToggle = Some(new OpalActionBarDrawerToggle(this, drawerLayout))
    drawerLayout.setDrawerListener(drawerToggle.get)
    getActionBar.setDisplayHomeAsUpEnabled(true)
    getActionBar.setHomeButtonEnabled(true)

    val spinner = drawerLayout.findViewById(R.id.cards_spinner).asInstanceOf[Spinner]
    this.spinner = Some(spinner)
    val emptyCardSpinner = drawerLayout.findViewById(R.id.empty_cards_spinner).asInstanceOf[TextView]
    this.emptyCardSpinner = Some(emptyCardSpinner)
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
          new CursorLoader(MainActivity.this, OpalProvider.Uris.cards, null, null, null, null)
        } else {
          null
        }
      }

      override def onLoadFinished(loader: Loader[Cursor], cursor: Cursor) {
        spinnerAdapter.changeCursor(cursor)
        ensureInitCardIndex(getIntent)
        spinnerLoaded = true
        Util.debug("spinner loaded")
      }

      override def onLoaderReset(loader: Loader[Cursor]) {
        spinnerAdapter.changeCursor(null)
      }

      if (!BuildConfig.ENABLE_DENOTE) {
        drawerLayout.findViewById(R.id.denote_container).setVisibility(View.GONE)
      }
    })

    spinnerAdapter.setStringConversionColumn(3)
    spinner.setAdapter(spinnerAdapter)
    updateEmptyCardText

    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      override def onItemSelected(parentView: AdapterView[_], selectedItemView: View, pos: Int, id: Long) {
        doSelect(Some(pos))
        drawerLayout.closeDrawers
        Usage.lastSelectedCard() = pos
      }

      override def onNothingSelected(parentView: AdapterView[_]) {
        doSelect(None)
      }

      private def doSelect(selectedCard: Option[Int]) {
        Util.debug(s"select card $selectedCard")
        if (currentCardIndex != selectedCard) {
          currentCardIndex = selectedCard
          reloadOp
        }
      }
    })

    val drawerMenu = drawerLayout.findViewById(R.id.drawer_menu).asInstanceOf[ListView]
    this.drawerMenu = Some(drawerMenu)
    drawerMenu.setSelection(0)
    drawerMenu.setAdapter(new DrawerMenuAdapter(this))
    drawerMenu.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long) {
        trackEvent("UI", "switchFragment", Some("drawerMenuClick"), Some(position))
        Util.debug(s"onMenuItemSelected $position")
        viewPager foreach {
          _.setCurrentItem(position)
        }
        drawerLayout.closeDrawers
      }
      def onNothingSelected(parent: AdapterView[_]) {}
    })

    plusOneButton = Option(drawerLayout.findViewById(R.id.plus_one_button).asInstanceOf[PlusOneButton])
  }

  private def setupObserver() {
    val handler = new Handler
    val contentObserver = new ContentObserver(handler) {
      override def onChange(selfChange: Boolean, uri: Uri) {
        Util.debug("ContentObserver     ==================== " + uri)
        currentCardIndex foreach { cardIndex =>
          if (uri == OpalProvider.Uris.activities(cardIndex)) {
            Util.debug("ContentObserver     ==================== do load")
            getFragment(0) foreach { _.refresh(currentCardIndex) }
            getFragment(1) foreach { _.refresh(currentCardIndex) }
          }
        }
      }

      override def onChange(selfChange: Boolean) {
        Util.debug("ContentObserver     ====================")
        getFragment(0) foreach { _.refresh(currentCardIndex) }
        getFragment(1) foreach { _.refresh(currentCardIndex) }
      }
    }
    // TODO(timgreen): more accurate uri.
    getContentResolver.registerContentObserver(OpalProvider.Uris.cards, true, contentObserver)

  }

  private def updateEmptyCardText() {
    emptyCardSpinner foreach { textView =>
      if (SyncStatus.hasSyncedBefore) {
        textView.setText(getResources.getString(R.string.empty_card_no_card))
      } else {
        textView.setText(getResources.getString(R.string.empty_card_syncing_card_list))
      }
    }
  }

  private var spinnerLoaded = false
  private def ensureInitCardIndex(intent: Intent) {
    if (intent.hasExtra(MainActivity.initCardIndex)) {
      val initCardIndex = intent.getIntExtra(MainActivity.initCardIndex, 0)
      spinner foreach { _.setSelection(initCardIndex) }
      Usage.lastSelectedCard() = initCardIndex
      Util.debug("set init cardIndex: " + initCardIndex)
      intent.removeExtra(MainActivity.initCardIndex)
    } else {
      if (!spinnerLoaded) {
        Util.debug("load last card: " + Usage.lastSelectedCard())
        spinner foreach { s =>
          if (s.getCount > Usage.lastSelectedCard()) {
            s.setSelection(Usage.lastSelectedCard())
          }
        }
        trackEvent("Launch", "normal")
      }
    }
  }

  private def enableSyncOnStart() = {
    PrefUtil.syncOnStart &&
      (!PrefUtil.autoSyncOnWifiOnly || NetworkConnectionChecker.hasWifiConnection(this))
  }

  def openSettings(view: View) {
    val intent = new Intent(this, classOf[SettingActivity])
    startActivity(intent)
    val drawerLayout = findViewById(R.id.drawer_layout).asInstanceOf[DrawerLayout]
    drawerLayout.closeDrawers
  }

  def denote(view: View) {
    InAppBilling.getBuyIntent("buy_timgreen_a_cake", new Date().toString, "it.timgreen.opal") match {
      case Left(pendingIntent) =>
        startIntentSenderForResult(pendingIntent.getIntentSender, 1001, new Intent, 0, 0, 0)
      case Right(r) =>
        if (r == InAppBilling.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
          SuperToast.create(this, "You have already denoted, Thanks!", SuperToast.Duration.SHORT).show
        } else {
          SuperToast.create(this, "Can not make denote, please try again later.", SuperToast.Duration.SHORT).show
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

  def share(view: View) {
    val intent = new Intent(Intent.ACTION_SEND)
    intent.setType("text/plain")
    intent.putExtra(Intent.EXTRA_SUBJECT, "Check out \"Opaler\"")
    intent.putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=it.timgreen.opal")
    startActivity(Intent.createChooser(intent, "Share Opaler"))
  }

  def dialOpal(view: View) {
    val intent = new Intent(Intent.ACTION_DIAL)
    intent.setData(Uri.parse("tel:136725"))
    startActivity(Intent.createChooser(intent, "Call Opal Customer Care"))
  }

  def opalFeedback(view: View) {
    val intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "opalcustomercare@opal.com.au", null))
    intent.putExtra(Intent.EXTRA_SUBJECT, "Opal Customer Care")
    startActivity(Intent.createChooser(intent, "Send Feedback to Opal Customer Care"))
  }

  def appFeedback(view: View) {
    SuperToast.create(this, "Preparing snapshots ...", SuperToast.Duration.SHORT).show

    new Handler().postDelayed(new Runnable() {
      override def run() {
        val intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_EMAIL, Array("iamtimgreen+playdev.opaler@gmail.com"));
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
    val drawerLayout = findViewById(R.id.drawer_layout).asInstanceOf[DrawerLayout]
    val spinner = drawerLayout.findViewById(R.id.cards_spinner).asInstanceOf[Spinner]
    // remove card name from snapshot
    emptyCardSpinner foreach { textView =>
      textView.setText(s"Card: ${spinner.getSelectedItemPosition} / ${spinner.getCount}")
    }
    spinner.getAdapter.asInstanceOf[SimpleCursorAdapter].swapCursor(null)
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
    drawerMenu foreach {
      _.setSelection(1)
    }
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
}

class AppSectionsPagerAdapter(activity: MainActivity, fm: FragmentManager) extends FragmentPagerAdapter(fm) {

  override def getCount = 2

  override def getItem(i: Int): Fragment = {
    val fragment = if (i == 0) {
      new OverviewFragment
    } else {
      new TripFragment
    }
    if (activity.isSyncing) {
      fragment.onRefreshStart
    }
    fragment
  }

  override def getPageTitle(position: Int): CharSequence = {
    // TODO(timgreen): text
    if (position == 0) {
      "Overview"
    } else {
      "Activity"
    }
  }
}

class OpalActionBarDrawerToggle(activity: MainActivity, drawerLayout: DrawerLayout)
  extends ActionBarDrawerToggle(activity,
                                drawerLayout,
                                R.drawable.ic_drawer,
                                R.string.drawer_open,
                                R.string.drawer_close) {

  override def onDrawerClosed(view: View) {
    super.onDrawerClosed(view)
    activity.updateActionBarTitle
  }

  override def onDrawerOpened(drawerView: View) {
    super.onDrawerOpened(drawerView)
    activity.getActionBar.setTitle(R.string.app_name)
    activity.getActionBar.setSubtitle(null)
  }
}

// TODO(timgreen): text
class DrawerMenuAdapter(activity: Activity) extends ArrayAdapter[String](
  activity, R.layout.row_item_drawer_menu, Array("Overview", "Activity")) {
  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val view = if (convertView != null) {
      convertView
    } else {
      val inflater = activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]
      inflater.inflate(R.layout.row_item_drawer_menu, parent, false)
    }
    view.findViewById(R.id.item).asInstanceOf[TextView].setText(getItem(position))
    view.findViewById(R.id.icon).asInstanceOf[ImageView].setImageResource(
      if (position == 0) {
        R.drawable.overview
      } else {
        R.drawable.activity
      }
    )
    view
  }
}
