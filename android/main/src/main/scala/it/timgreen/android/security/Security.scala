package it.timgreen.android.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

import android.os.Build
import android.util.Base64

// See http://android-developers.blogspot.com.au/2013/02/using-cryptography-to-store-credentials.html.
object Security {

  val ALGORITHM = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
    "PBKDF2WithHmacSHA1And8bit"
  } else {
    "PBKDF2WithHmacSHA1"
  }
  val CIPHER_ALGORITHM = "AES/CBC/PKCS5Padding"

  def generateSalt(length: Int): Array[Byte] = {
    val secureRandom = new SecureRandom
    val output = new Array[Byte](length)
    secureRandom.nextBytes(output);
    output
  }

  def generateKey(passphraseOrPin: Array[Char], salt: Array[Byte]): SecretKey = {
    // Number of PBKDF2 hardening rounds to use. Larger values increase
    // computation time. You should select a value that causes computation
    // to take >100ms.
    val iterations = 1000

    // Generate a 256-bit key
    val outputKeyLength = 256

    val secretKeyFactory = SecretKeyFactory.getInstance(ALGORITHM)
    val keySpec = new PBEKeySpec(passphraseOrPin, salt, iterations, outputKeyLength)
    val secretKey = secretKeyFactory.generateSecret(keySpec)

    secretKey
  }

  def encrypt(value: String)(passphraseOrPin: Array[Char], salt: Array[Byte]): String = {
    val bytes = value.getBytes("UTF-8")
    Base64.encodeToString(
      doWork(bytes, Cipher.ENCRYPT_MODE)(passphraseOrPin, salt),
      Base64.NO_WRAP
    )
  }

  def decrypt(value: String)(passphraseOrPin: Array[Char], salt: Array[Byte]): String = {
    val bytes = Base64.decode(value, Base64.DEFAULT)
    new String(doWork(bytes, Cipher.DECRYPT_MODE)(passphraseOrPin, salt), "UTF-8")
  }

  private def doWork(bytes: Array[Byte], mode: Int)
                    (passphraseOrPin: Array[Char], salt: Array[Byte]): Array[Byte] = {
    val key = generateKey(passphraseOrPin, salt)
    val encriptionKey = new SecretKeySpec(key.getEncoded, "AES");
    val cipher = Cipher.getInstance(CIPHER_ALGORITHM)
    cipher.init(mode, encriptionKey, new IvParameterSpec(salt))
    cipher.doFinal(bytes)
  }
}
