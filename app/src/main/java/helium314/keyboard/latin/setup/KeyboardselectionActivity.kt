package helium314.keyboard.latin.setup

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import helium314.keyboard.latin.R

class KeyboardselectionActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    lateinit var owelId: ImageButton
    lateinit var drawerLayout: DrawerLayout
lateinit var  arrowID:ImageView

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_keyboardselection)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener(this)

        val menuIcon: ImageView = findViewById(R.id.menu_icon)
        menuIcon.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        val headerView: View = navigationView.getHeaderView(0)
        arrowID = headerView.findViewById(R.id.arrowId)
        owelId = findViewById(R.id.owelId)
        owelId.setOnClickListener {
            startActivity(Intent(this, KeybaordActivity::class.java))
        }

        arrowID.setOnClickListener {
            startActivity(Intent(this, KeybaordActivity::class.java))
        }

        val instructionText = "    Welcome to \n" + "Oscar Keyboard"
        val spannableString = SpannableString(instructionText)

        val startIndex = instructionText.indexOf("Oscar Keyboard")
        val endIndex = startIndex + "Oscar Keyboard".length

        if (startIndex >= 0) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#85BDB9")),
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannableString.setSpan(
                StyleSpan(Typeface.BOLD), // Set style to bold
                startIndex,
                endIndex,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val textViewInstruction = findViewById<TextView>(R.id.textview_Id)
        textViewInstruction.text = spannableString


        val instruction = "Tap on Oscar Keyboard to try the keyboard"
        val spannablestr = SpannableString(instruction)

        val startIdx = instruction.indexOf("Oscar Keyboard")
        val endIdx = startIdx + "Oscar Keyboard".length

        if (startIdx >= 0) {
            spannablestr.setSpan(
                ForegroundColorSpan(Color.BLACK),
                startIdx,
                endIdx,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            spannablestr.setSpan(
                StyleSpan(Typeface.BOLD), // Set style to bold
                startIdx,
                endIdx,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val textView = findViewById<TextView>(R.id.new_text_view)
        textView.text = spannablestr
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_privacy_policy -> {
                startActivity(Intent(this, PrivacyPolicyActivity::class.java))
            }
            R.id.nav_recommended_us -> {
                // Handle recommended us click
            }
            R.id.nav_email_us -> {
                // Handle email us click
            }
            R.id.nav_terms_conditions -> {
                startActivity(Intent(this, TermsOfUseActivity::class.java))
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }
}