package com.google.mediapipe.examples.llminference

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

object SecureStorage {
  private const val PREFS_NAME = "secure_prefs"
  private const val KEY_ACCESS_TOKEN = "access_token"
  private const val KEY_CODE_VERIFIER = "code_verifier"

  fun saveCodeVerifier(context: Context, codeVerifier: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_CODE_VERIFIER, codeVerifier).apply()
  }

  fun getCodeVerifier(context: Context): String? {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getString(KEY_CODE_VERIFIER, null)
  }

  fun saveToken(context: Context, token: String) {
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    val sharedPreferences = EncryptedSharedPreferences.create(
      PREFS_NAME,
      masterKeyAlias,
      context,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    sharedPreferences.edit().putString(KEY_ACCESS_TOKEN, token).apply()
  }

  fun getToken(context: Context): String? {
    val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    val sharedPreferences = EncryptedSharedPreferences.create(
      PREFS_NAME,
      masterKeyAlias,
      context,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    return sharedPreferences.getString(KEY_ACCESS_TOKEN, null)
  }
}
