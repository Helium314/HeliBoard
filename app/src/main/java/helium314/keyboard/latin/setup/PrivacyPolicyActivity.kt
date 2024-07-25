package helium314.keyboard.latin.setup

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import helium314.keyboard.latin.R

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
        }
    }
}