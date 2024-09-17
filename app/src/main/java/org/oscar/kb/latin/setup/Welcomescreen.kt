package org.oscar.kb.latin.setup

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.oscar.kb.R

class Welcomescreen : AppCompatActivity() {
    lateinit var Getbutton: Button
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcomescreen)
        sharedPreferences = getSharedPreferences("keyboard_prefs", MODE_PRIVATE)
        if (isSetupComplete()) {
            navigateToMainActivity()
            return
        }
        Getbutton=findViewById(R.id.getButton)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        Getbutton.setOnClickListener(){
            setSetupCompleteFlag() // Mark setup as complete
            navigateToMainActivity()
            val intent= Intent(this@Welcomescreen,MainActivity::class.java)
            startActivity(intent)
        }
    }
    private fun isSetupComplete(): Boolean {
        return sharedPreferences.getBoolean("setup_complete", false)
    }

    private fun setSetupCompleteFlag() {
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putBoolean("setup_complete", true)
        editor.apply()
    }

    private fun navigateToMainActivity() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish() // Close the setup wizard activity
        } catch (e: Exception) {
            // Log the exception in Firebase Crashlytics
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }
}