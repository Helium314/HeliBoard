package helium314.keyboard.latin.setup

import android.annotation.SuppressLint
import android.content.SharedPreferences
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
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import helium314.keyboard.latin.R

class KeyboardselectionActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    lateinit var owelId: ImageButton
    lateinit var drawerLayout: DrawerLayout
    lateinit var arrowID: ImageView
    lateinit var owelLogo: ImageView
    lateinit var owelBackground: ImageView
    lateinit var txtKey: TextView
    lateinit var imgKey: ImageView
    private lateinit var sharedPreferences: SharedPreferences

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_keyboardselection)
        sharedPreferences = getSharedPreferences("KeyboardPrefs", MODE_PRIVATE)

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
        owelLogo = findViewById(R.id.owelLogo)
        owelBackground = findViewById(R.id.owelBackground)
        txtKey = findViewById(R.id.txtKey)
        imgKey = findViewById(R.id.imgKey)
//        val mainActivity = MainActivity() // Or get reference from elsewhere
//        mainActivity.setKeyboardSetupDone()

        owelId.setOnClickListener {
            // Hide the three ImageViews
            owelLogo.visibility = View.GONE
            owelBackground.visibility = View.GONE
            owelId.visibility = View.GONE

            // Show the LinearLayout containing txtKey and imgKey
            txtKey.visibility = View.VISIBLE
            imgKey.visibility = View.VISIBLE
        }

//        arrowID.setOnClickListener {
////            startActivity(Intent(this, KeyboardselectionActivity::class.java))
//        }

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
                StyleSpan(Typeface.BOLD),
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
                StyleSpan(Typeface.BOLD),
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
//                val browserIntent = Intent(
//                    Intent.ACTION_VIEW,
//                    Uri.parse("https://www.termsfeed.com/sample-privacy-policy/")
//                )
//                startActivity(browserIntent)
            }

            R.id.nav_recommended_us -> {
            }

            R.id.nav_email_us -> {
//                val emailIntent = Intent(Intent.ACTION_SEND).apply {
//                    type = "text/plain"
//                    putExtra(Intent.EXTRA_EMAIL, arrayOf("kalyani143jk@gmail.com"))
//                    putExtra(Intent.EXTRA_SUBJECT, "Support Request")
//                    putExtra(Intent.EXTRA_TEXT, "Hello, I need support regarding...")
//                }
//                if (emailIntent.resolveActivity(packageManager) != null) {
//                    startActivity(Intent.createChooser(emailIntent, "Send Email"))
//                } else {
                    Toast.makeText(this, "No email client installed", Toast.LENGTH_SHORT).show()
//                }
            }

            R.id.nav_terms_conditions -> {
//                val browserIntent = Intent(
//                    Intent.ACTION_VIEW,
//                    Uri.parse("https://www.termsfeed.com/sample-terms-and-conditions/")
//                )
//                startActivity(browserIntent)
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
