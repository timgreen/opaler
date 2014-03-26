package it.timgreen.opal.gtm

import android.content.Context
import com.google.android.gms.tagmanager.ContainerHolder
import com.google.android.gms.tagmanager.TagManager

import it.timgreen.opal.BuildConfig
import it.timgreen.opal.R

import java.util.concurrent.TimeUnit

object Gtm {
  // @inline
  // private def containerId = "GTM-MS67C3"

  // @inline
  // private def defaultConfig = R.raw.gtm_ms67c3_v20140531_1507

  // @inline
  // private def tagManager(implicit context: Context) = TagManager.getInstance(context)

  // private var containerHolder: ContainerHolder = _

  // private def getContainerHolder(implicit context: Context) = Gtm.synchronized {
  //   if (containerHolder == null) {
  //     initContainerHolder
  //   }
  //   containerHolder
  // }

  // private def initContainerHolder(implicit context: Context) {
  //   if (BuildConfig.DEBUG) {
  //     tagManager.setVerboseLoggingEnabled(true)
  //   }

  //   val pending = tagManager.loadContainerPreferNonDefault(containerId, defaultConfig)
  //   containerHolder = pending.await(2, TimeUnit.SECONDS)
  // }

  def refresh(implicit context: Context) {
    // getContainerHolder.refresh
  }

  // def dataLayer(implicit context: Context) = tagManager.getDataLayer

  // def container(implicit context: Context) = getContainerHolder.getContainer

  // values
  @inline
  def getOverrideRecordNum(implicit context: Context) = {
    // it.timgreen.opal.Util.debug("gtm" + getContainerHolder.getStatus + " :" + container.getLong("overrideRecordNum"))
    // container.getLong("overrideRecordNum")
    20
  }

  // @inline
  // def getVersion(implicit context: Context) = container.getLong("version")
}
