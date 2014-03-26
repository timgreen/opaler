package it.timgreen.android.security

import android.util.Base64

import java.security.MessageDigest

object Hash {

  def sha256(str: String): String = {
    try {
      val md = MessageDigest.getInstance("SHA-256")
      md.update(str.getBytes)
      Base64.encodeToString(md.digest, Base64.DEFAULT)
    } catch {
      case _: Throwable => str
    }
  }
}
