package com.google.mediapipe.examples.llminference

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent

class LicenseAcknowledgmentActivity : AppCompatActivity() {
  private lateinit var acknowledgeButton: Button
  private lateinit var continueButton: Button
  private val licenseUrl = "https://huggingface.co/google/gemma-1.1-2b-it"

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_license_acknowledgment)

    acknowledgeButton = findViewById(R.id.btnAcknowledge)
    continueButton = findViewById(R.id.btnContinue)

    // Disable "Continue" button initially
    continueButton.isEnabled = false

    acknowledgeButton.setOnClickListener {
      val customTabsIntent = CustomTabsIntent.Builder().build()
      customTabsIntent.launchUrl(this, Uri.parse(licenseUrl))

      // Enable "Continue" if user viewed license
      continueButton.isEnabled = true
    }

    continueButton.setOnClickListener {
      Toast.makeText(this, "Please try again", Toast.LENGTH_LONG).show()
      startActivity(Intent(this, MainActivity::class.java))
      finish()
    }

  }

  override fun onResume() {
    super.onResume()
    // Enable "Continue" if user viewed license
    // continueButton.isEnabled = true
  }
}
