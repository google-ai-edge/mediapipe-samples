package com.google.mediapipe.examples.llminference

import net.openid.appauth.AuthorizationServiceConfiguration
import android.net.Uri

object AuthConfig {
  // Replace these values with your actual OAuth credentials
  const val clientId = "19943f22-042c-43f8-96bd-6522ffa8bdfe" // Hugging Face Client ID
  const val redirectUri = "com.google.mediapipe.examples.llminference://oauth2callback"

  // OAuth 2.0 Endpoints (Authorization + Token Exchange)
  private const val authEndpoint = "https://huggingface.co/oauth/authorize"
  private const val tokenEndpoint = "https://huggingface.co/oauth/token"

  // OAuth service configuration (AppAuth library requires this)
  val authServiceConfig = AuthorizationServiceConfiguration(
    Uri.parse(authEndpoint), // Authorization endpoint
    Uri.parse(tokenEndpoint) // Token exchange endpoint
  )
}
