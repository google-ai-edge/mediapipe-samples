package com.google.mediapipe.examples.llminference

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import net.openid.appauth.AuthorizationService
import net.openid.appauth.TokenRequest
import net.openid.appauth.GrantTypeValues
import java.security.SecureRandom
import java.util.Base64

class OAuthCallbackActivity : Activity() {
  private lateinit var authService: AuthorizationService

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    authService = AuthorizationService(this)

    System.out.println("--------------> OAuthCallbackActivity.onCreate()")
    val data: Uri? = intent.data
    if (data != null) {
      println("---------------> Received URI: $data")  // Log the URI

      // Manually extract the authorization code
      val authCode = data.getQueryParameter("code")
      // val authState = data.getQueryParameter("state")

      if (authCode != null) {
        println("-------> Authorization Code: $authCode")

        // Retrieve the code verifier that was used in the initial request
        val codeVerifier = SecureStorage.getCodeVerifier(applicationContext)

        // Create a Token Request manually
        val tokenRequest = TokenRequest.Builder(
          AuthConfig.authServiceConfig, // Ensure this is properly set up
          AuthConfig.clientId
        )
          .setGrantType(GrantTypeValues.AUTHORIZATION_CODE)
          .setAuthorizationCode(authCode)
          .setRedirectUri(Uri.parse(AuthConfig.redirectUri))
          .setCodeVerifier(codeVerifier) // Required for PKCE
          .build()

        authService.performTokenRequest(tokenRequest) { response, ex ->
          if (response != null) {
            val accessToken = response.accessToken
            SecureStorage.saveToken(
              applicationContext,
              accessToken ?: ""
            )
            println("-------> Access Token Received: $accessToken")

            // Go back to the main app
            startActivity(Intent(this, MainActivity::class.java))
          } else {
            println("-------> OAuth Error: ${ex?.message}")
          }
          finish()
        }
      } else {
        println("-------------> No Authorization Code Found")
        finish()
      }
    } else {
      println("-------------> OAuth Failed: No Data in Intent")
      finish()
    }
  }

  override fun onDestroy() {
    super.onDestroy()

    authService.dispose()
  }
}