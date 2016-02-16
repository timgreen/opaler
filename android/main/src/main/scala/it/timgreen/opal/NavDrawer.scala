package it.timgreen.opal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v4.content.FileProvider
import android.support.v7.widget.Toolbar
import android.view.View

import com.afollestad.materialdialogs.MaterialDialog
import com.amulyakhare.textdrawable.TextDrawable
import com.amulyakhare.textdrawable.util.ColorGenerator
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

import java.io.File
import java.util.{ List => JList, Date, ArrayList }

import scala.collection.JavaConversions._

import it.timgreen.android.billing.InAppBilling
import it.timgreen.android.util.Snapshot

class NavDrawer(mainActivity: MainActivity, savedInstanceState: Bundle) {
  import rxdata.RxCards
  import rxdata.RxCards.currentCardIndex

  val (header, drawer) = setupDrawerAndToolbar

  private val profiles: Observable[DataStatus[ArrayList[IProfile[_]]]] = RxCards.cards map { cardsData =>
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

  def bindToLifecycleAndSubscribe() {
    //// Update drawer profiles
    mainActivity.toRichObservable(profiles.combineLatest(currentCardIndex)).bindToLifecycle subscribe { pair =>
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
    mainActivity.toRichObservable(currentCardIndex).bindToLifecycle subscribe { cardIndex =>
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

  def drawerSnapshot(filename: String): Option[Uri] = {
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

  private def setupDrawerAndToolbar(): (AccountHeader, Drawer) = {
    val toolbar = mainActivity.findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    mainActivity.setSupportActionBar(toolbar)
    mainActivity.getSupportActionBar.setDisplayHomeAsUpEnabled(true)
    mainActivity.getSupportActionBar.setHomeButtonEnabled(true)

    val header = new AccountHeaderBuilder()
      .withActivity(mainActivity)
      .addProfiles(
        new ProfileDrawerItem()
          .withName("No Card")
          .withEmail(" ")
          .withIcon(mainActivity.getResources.getDrawable(R.drawable.logo))
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
    val typedArray = mainActivity.obtainStyledAttributes(attrs)

    val drawer = new DrawerBuilder()
      .withActivity(mainActivity)
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
                mainActivity.currentFragmentId.onNext(id)
                // TODO(timgreen):
                // mainActivity.trackEvent("UI", "switchFragment", Some("drawerItemClick"), Some(id - 1))
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
    (header, drawer)
  }

  def openSettings() {
    val intent = new Intent(mainActivity, classOf[SettingActivity])
    mainActivity.startActivity(intent)
    drawer.closeDrawer
  }

  def donate() {
    InAppBilling.getBuyIntent("buy_timgreen_a_cake", new Date().toString, "it.timgreen.opal") match {
      case Left(pendingIntent) =>
        mainActivity.startIntentSenderForResult(pendingIntent.getIntentSender, 1001, new Intent, 0, 0, 0)
      case Right(r) =>
        if (r == InAppBilling.BILLING_RESPONSE_RESULT_ITEM_ALREADY_OWNED) {
          SuperToast.create(mainActivity, "You have already donated, Thanks!", SuperToast.Duration.SHORT).show
        } else {
          SuperToast.create(mainActivity, "Can not make donate, please try again later.", SuperToast.Duration.SHORT).show
        }
    }
  }

  def share() {
    val intent = new Intent(Intent.ACTION_SEND)
    intent.setType("text/plain")
    intent.putExtra(Intent.EXTRA_SUBJECT, "Check out \"Opaler\"")
    intent.putExtra(Intent.EXTRA_TEXT, "https://play.google.com/store/apps/details?id=it.timgreen.opal")
    mainActivity.startActivity(Intent.createChooser(intent, "Share Opaler"))
  }

  def feedback() {
    new MaterialDialog.Builder(mainActivity)
      .title(mainActivity.getResources.getString(R.string.drawer_feedback_and_help))
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
    mainActivity.startActivity(Intent.createChooser(intent, "Call Opal Customer Care"))
  }

  def opalFeedback() {
    val intent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "opalcustomercare@opal.com.au", null))
    intent.putExtra(Intent.EXTRA_SUBJECT, "Opal Customer Care")
    mainActivity.startActivity(Intent.createChooser(intent, "Send Feedback to Opal Customer Care"))
  }

  def appFeedback() {
    SuperToast.create(mainActivity, "Preparing snapshots ...", SuperToast.Duration.SHORT).show

    new Handler().postDelayed(new Runnable() {
      override def run() {
        val intent = new Intent(Intent.ACTION_SEND_MULTIPLE)
        intent.setType("text/plain")
        intent.putExtra(Intent.EXTRA_EMAIL, Array("iamtimgreen+playdev.opaler@gmail.com"))
        intent.putExtra(Intent.EXTRA_SUBJECT, "Opaler Feedback")
        intent.putExtra(Intent.EXTRA_TEXT, s"\n\n\n--------\nOpaler version: ${BuildConfig.VERSION_NAME}")

        mainActivity.runOnUiThread(new Runnable() {
          override def run() {
            val snapshotOverview = fragmentSnapshot(0, "overview.png")
            val snapshotActivities = fragmentSnapshot(1, "activities.png")
            val snapshotDrawer = drawerSnapshot("drawer.png")
            val snapshots = snapshotOverview.toList ::: snapshotActivities.toList ::: snapshotDrawer.toList
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList(snapshots))

            val mailApps = mainActivity.getPackageManager.queryIntentActivities(
              new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", "", null)),
              PackageManager.MATCH_DEFAULT_ONLY
            ).map(_.activityInfo.packageName).toSet
            Util.debug("mail apps: " + mailApps.mkString(", "))

            val supportedMailApps = mainActivity.getPackageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) map {
              _.activityInfo.packageName
            } filter { pn =>
              mailApps.contains(pn)
            }

            supportedMailApps foreach { packageName =>
              snapshots foreach { uri =>
                mainActivity.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
              }
            }

            if (supportedMailApps.nonEmpty) {
              val targetIntents = supportedMailApps map { pn =>
                new Intent(intent).setPackage(pn)
              }
              val chooserIntent = Intent.createChooser(targetIntents.head, "Send Feedback to Opaler Developer")
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, targetIntents.tail.toArray)
              mainActivity.startActivity(chooserIntent)
            } else {
              SuperToast.create(mainActivity, "No available mail app.", SuperToast.Duration.SHORT).show
            }
            // recreate to 'fix' the snapshot masks
            mainActivity.recreate
          }
        })
      }
    }, 100)
  }

  private def fragmentSnapshot(index: Int, filename: String): Option[Uri] = mainActivity.getFragment(index) map { fragment =>
    fragment.preSnapshot
    snapshot(fragment.getView, filename)
  }

  private def snapshot(view: View, filename: String): Uri = {
    val bitmap = Snapshot.getSnapshot(view)
    val file = mainActivity.openFileOutput(filename, Context.MODE_PRIVATE)
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, file)
    file.close
    Util.debug(mainActivity.getFilesDir() + "/" + filename)
    val fileUri = FileProvider.getUriForFile(mainActivity, BuildConfig.APPLICATION_ID +
    ".fileprovider", new File(mainActivity.getFilesDir(), filename))
    Util.debug(fileUri.toString)

    fileUri
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
