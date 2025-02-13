package com.google.mediapipe.examples.llminference

import android.app.Activity
import android.content.Intent
import android.net.Uri
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationException
import net.openid.appauth.TokenRequest

class OAuthCallbackActivity : Activity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val authResponse = AuthorizationResponse.fromIntent(intent)
    val authException = AuthorizationException.fromIntent(intent)

    if (authResponse != null) {
      val authService = AuthorizationService(this)
      val tokenRequest = authResponse.createTokenExchangeRequest()

      authService.performTokenRequest(tokenRequest) { response, ex ->
        if (response != null) {
          val accessToken = response.accessToken
          SecureStorage.saveToken(this, accessToken ?: "") // 🔒 Save Securely
          startActivity(Intent(this, MainActivity::class.java)) // Go back to app
        } else {
          println("OAuth Error: ${ex?.message}")
        }
        finish()
      }
    } else {
      println("OAuth Failed: ${authException?.message}")
      finish()
    }
  }
}