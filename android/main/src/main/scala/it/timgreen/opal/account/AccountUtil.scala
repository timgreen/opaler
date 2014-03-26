package it.timgreen.opal.account

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.os.Bundle

import it.timgreen.opal.BuildConfig
import it.timgreen.opal.provider.OpalProvider

import scala.collection.JavaConversions._

object AccountUtil {
  val accountType = BuildConfig.APPLICATION_ID + ".account"
  val syncFrequency = 60 * 60 * 4 // 4 hour (in seconds)

  def getAccountManager(implicit context: Context) = context.getSystemService(Context.ACCOUNT_SERVICE).asInstanceOf[AccountManager]

  def addAccount(username: String, password: String)(implicit context: Context) {
    val account = new Account(username, accountType)
    val accountManager = getAccountManager
    if (accountManager.addAccountExplicitly(account, password, null)) {
      // Inform the system that this account supports sync
      ContentResolver.setIsSyncable(account, OpalProvider.AUTHORITY, 1)
      enableAutoSync(account)
    }
  }

  def enableAutoSync(account: Account) {
    ContentResolver.setSyncAutomatically(account, OpalProvider.AUTHORITY, true)
    val hasCorrectPeriodicSync = {
      val periodicSyncs = ContentResolver.getPeriodicSyncs(account, OpalProvider.AUTHORITY)
      (periodicSyncs.size == 1) && (periodicSyncs exists { _.period != syncFrequency })
    }
    if (!hasCorrectPeriodicSync) {
      ContentResolver.removePeriodicSync(account, OpalProvider.AUTHORITY, new Bundle())
      ContentResolver.addPeriodicSync(account, OpalProvider.AUTHORITY, new Bundle(), syncFrequency)
    }
  }

  def disableAutoSync(account: Account) {
    ContentResolver.setSyncAutomatically(account, OpalProvider.AUTHORITY, false)
    ContentResolver.removePeriodicSync(account, OpalProvider.AUTHORITY, new Bundle())
  }

  def removeAllAccount(implicit context: Context) {
    val accountManager = getAccountManager
    accountManager.getAccountsByType(accountType) foreach { a =>
      accountManager.removeAccount(a, null, null)
    }
  }

  def getAccount(implicit context: Context): Option[Account] = {
    val accountManager = getAccountManager
    accountManager.getAccountsByType(accountType).headOption
  }

  def getPassword(account: Account)(implicit context: Context): String = {
    val accountManager = getAccountManager
    accountManager.getPassword(account)
  }
}
