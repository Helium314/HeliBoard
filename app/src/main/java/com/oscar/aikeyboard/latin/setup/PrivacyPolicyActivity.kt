package com.oscar.aikeyboard.latin.setup

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import org.samyarth.oskey.R

class PrivacyPolicyActivity : AppCompatActivity() {
    lateinit var privacyPolicy: TextView
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_privacy_policy)

        privacyPolicy=findViewById(R.id.policy)
        privacyPolicy.setOnClickListener {
            startActivity(Intent(this, KeyboardselectionActivity::class.java))
            val termsTextView: TextView = findViewById(R.id.policytext)
            termsTextView.text = HtmlCompat.fromHtml(getString(R.string.privacy_policy), HtmlCompat.FROM_HTML_MODE_LEGACY)
            termsTextView.movementMethod = LinkMovementMethod.getInstance()

        }
    }
}