package it.timgreen.opal

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.preference.DialogPreference
import android.preference.Preference
import android.preference.PreferenceActivity
import android.provider.Settings
import android.util.AttributeSet
import android.view.MenuItem

import sheetrock.panda.changelog.ChangeLog

import it.timgreen.opal.AnalyticsSupport._
import it.timgreen.opal.account.AccountUtil
import it.timgreen.opal.provider.OpalProvider

class SettingActivity extends PreferenceActivity with AccountHelper.Operator {
  implicit def getActivity = this

  override def onResume() {
    super.onResume
    trackView("SettingActivity")
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    getActionBar.setDisplayHomeAsUpEnabled(true)
    addPreferencesFromResource(R.layout.activity_setting)

    val accountInfo = findPreference("account_info")
    accountInfo.setTitle(AccountUtil.getAccount.map(_.name).getOrElse(""))
    accountInfo.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      override def onPreferenceClick(preference: Preference): Boolean = {
        val intent = new Intent(Settings.ACTION_SYNC_SETTINGS)
        intent.putExtra(Settings.EXTRA_AUTHORITIES, Array(OpalProvider.AUTHORITY ))
        SettingActivity.this.startActivity(intent)
        true
      }
    })

    val pInfo = getPackageManager().getPackageInfo(getPackageName, 0)
    val version = pInfo.versionName
    val about = findPreference("about")
    about.setSummary(
      String.format(getResources.getString(R.string.pref_about_summary), version))
    about.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
      override def onPreferenceClick(preference: Preference): Boolean = {
        val cl = new ChangeLog(SettingActivity.this)
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

class SignoutDialogPreference(context: Context, attrs: AttributeSet) extends DialogPreference(context, attrs) {
  override protected def onDialogClosed(positiveResult: Boolean) {
    if (positiveResult) {
      context.asInstanceOf[SettingActivity].signout
    }
  }
}
