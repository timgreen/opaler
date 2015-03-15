package it.timgreen.opal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.DialogPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.provider.Settings
import android.support.v7.app.ActionBarActivity
import android.support.v7.widget.Toolbar
import android.util.AttributeSet
import android.view.MenuItem

import sheetrock.panda.changelog.ChangeLog

import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.provider.OpalProvider

class SettingActivity extends ActionBarActivity with AccountHelper.Operator {
  implicit def getActivity = this

  override def onResume() {
    super.onResume
    trackView("SettingActivity")
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_setting)

    val toolbar = findViewById(R.id.toolbar).asInstanceOf[Toolbar]
    setSupportActionBar(toolbar)
    getSupportActionBar().setHomeButtonEnabled(true)
    getSupportActionBar.setDisplayHomeAsUpEnabled(true)

    getFragmentManager
      .beginTransaction
      .replace(R.id.content_frame, new SettingsFragment)
      .commit
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case android.R.id.home =>
        onBackPressed
        true
      case _ =>
        super.onOptionsItemSelected(item)
    }
  }

  def signout() {
    AccountUtil.removeAllAccount
    clearAppData
    finish
  }
}

class SettingsFragment extends PreferenceFragment {

  implicit def providerContext = getActivity

  override def onCreate(bundle: Bundle) {
    super.onCreate(bundle)

    addPreferencesFromResource(R.xml.settings)

    val accountInfo = findPreference("account_info")
    accountInfo.setTitle(AccountUtil.getAccount.map(_.name).getOrElse(""))
    accountInfo.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      override def onPreferenceClick(preference: Preference): Boolean = {
        val intent = new Intent(Settings.ACTION_SYNC_SETTINGS)
        intent.putExtra(Settings.EXTRA_AUTHORITIES, Array(OpalProvider.AUTHORITY ))
        getActivity.startActivity(intent)
        true
      }
    })

    val pInfo = getActivity.getPackageManager.getPackageInfo(getActivity.getPackageName, 0)
    val version = pInfo.versionName
    val about = findPreference("about")
    about.setSummary(
      String.format(getResources.getString(R.string.pref_about_summary), version))
    about.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      override def onPreferenceClick(preference: Preference): Boolean = {
        val cl = new ChangeLog(getActivity)
        cl.getFullLogDialog.show
        true
      }
    })

    val devGroup = findPreference("dev_group")
    BuildConfig.BUILD_TYPE match {
      case "debug" | "alpha" =>
      case _ =>
        devGroup.setEnabled(false)
        getPreferenceScreen.removePreference(devGroup)
    }
  }
}

class SignoutDialogPreference(context: Context, attrs: AttributeSet) extends DialogPreference(context, attrs) {
  override protected def onDialogClosed(positiveResult: Boolean) {
    if (positiveResult) {
      context.asInstanceOf[SettingActivity].signout
    }
  }
}
