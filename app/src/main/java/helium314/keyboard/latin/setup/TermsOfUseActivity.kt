package helium314.keyboard.latin.setup

import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.oscar.aikeyboard.R

class TermsOfUseActivity : AppCompatActivity() {
    lateinit var terms:TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_terms_of_use)

        terms=findViewById(R.id.terms)
        terms.setOnClickListener{
            startActivity(Intent(this, KeyboardselectionActivity::class.java))
            val termsTextView: TextView = findViewById(R.id.termstext)
            termsTextView.text = HtmlCompat.fromHtml(getString(R.string.terms_of_use), HtmlCompat.FROM_HTML_MODE_LEGACY)
            termsTextView.movementMethod = LinkMovementMethod.getInstance()

        }
    }
}