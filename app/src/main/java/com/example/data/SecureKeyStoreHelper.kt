package com.example.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureKeyStoreHelper {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "HangoutSecureTokenKeyAlias"
    private const val AES_MODE = "AES/GCM/NoPadding"
    private const val IV_SEPARATOR = "]"

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(spec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey
    }

    /**
     * Encrypt plain text using KeyStore AES key in GCM mode.
     * Returns a string consisting of base64-encoded IV + separator + base64-encoded cipher text.
     */
    fun encrypt(plainText: String?): String? {
        if (plainText == null) return null
        return try {
            val cipher = Cipher.getInstance(AES_MODE)
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val ivString = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
            val encryptedString = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
            "$ivString$IV_SEPARATOR$encryptedString"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Decrypt AES/GCM encrypted string.
     */
    fun decrypt(encryptedData: String?): String? {
        if (encryptedData == null || !encryptedData.contains(IV_SEPARATOR)) return null
        return try {
            val parts = encryptedData.split(IV_SEPARATOR)
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val encryptedBytes = Base64.decode(parts[1], Base64.NO_WRAP)

            val cipher = Cipher.getInstance(AES_MODE)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
            String(cipher.doFinal(encryptedBytes), Charsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
