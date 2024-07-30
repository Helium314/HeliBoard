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
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.android.material.navigation.NavigationView
import helium314.keyboard.AIEngine.SummarizeUiState
import helium314.keyboard.AIEngine.SummarizeViewModel
import helium314.keyboard.AIEngine.SummarizeViewModelFactory
import helium314.keyboard.gemini.GeminiClient
import helium314.keyboard.latin.R
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


class KeyboardselectionActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener {
    lateinit var ivOscar: ImageButton
    lateinit var etopenOscar: EditText
    lateinit var drawerLayout: DrawerLayout
    lateinit var ivBack: ImageView
    lateinit var owelBackground: ImageView
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var ivSummarizeText: ImageView

     val geminiClient = GeminiClient() // Assuming you have a way to create a GeminiClient instance
        val generativeModel = geminiClient.geminiFlashModel

// Assuming you have a way to create a GenerativeModel instance
//    val viewModelFactory = SummarizeViewModelFactory(geminiClient, generativeModel)
//    val viewModel = ViewModelProvider(this, viewModelFactory)[SummarizeViewModel::class.java]
    //private lateinit var viewModel: SummarizeViewModel

    private val mViewModel by lazy {
        ViewModelProvider(this, SummarizeViewModelFactory(generativeModel))[SummarizeViewModel::class.java]
    }


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_keyboardselection)
        sharedPreferences = getSharedPreferences("KeyboardPrefs", MODE_PRIVATE)

        drawerLayout = findViewById(R.id.drawer_layout)
        val navigationView: NavigationView = findViewById(R.id.navigation_view)
        navigationView.setNavigationItemSelectedListener(this)

        val menuIcon: ImageView = findViewById(R.id.iv_navDrawer)
        menuIcon.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        val headerView: View = navigationView.getHeaderView(0)
        ivBack = headerView.findViewById(R.id.iv_back)
        ivOscar = findViewById(R.id.iv_oscar)
        etopenOscar = findViewById(R.id.et_openOscar)
        ivSummarizeText = findViewById(R.id.iv_summarizeText)
        //Initialize my viewModel here


        //val viewModel: SummarizeViewModel by viewModels()

        etopenOscar.setOnClickListener {
            owelBackground.visibility = View.GONE
            ivOscar.visibility = View.GONE
            ivOscar.visibility = View.GONE
            owelBackground.visibility = View.GONE
        }

        ivSummarizeText.setOnClickListener {
            if (etopenOscar.text.isNotBlank()) {
                handleSummarize(mViewModel, etopenOscar.text.toString())
                lifecycleScope.launch {
                val summarizeUiStateFlow: StateFlow<SummarizeUiState> =
                    observeSummarizeUiState(mViewModel)
                summarizeUiStateFlow.collect() { uiState ->
                    when (uiState) {
                        SummarizeUiState.Initial, SummarizeUiState.Loading -> {
                            // Handle loading state
                        }

                        is SummarizeUiState.Success -> {
                            // Handle success state
                            val outputText = buildSummarizeContent(uiState)
                            etopenOscar.setText(outputText)

                        }

                        is SummarizeUiState.Error -> {
                            // Handle error state
                            val errorMessage = buildSummarizeContent(uiState)
                            //Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                mViewModel.summarizeStreaming(etopenOscar.text.toString())
            }
            }
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
                    StyleSpan(Typeface.BOLD),
                    startIndex,
                    endIndex,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            val textViewInstruction = findViewById<TextView>(R.id.tv_welcomeText)
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

            val textView = findViewById<TextView>(R.id.tv_enableKeyboard)
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

    fun observeSummarizeUiState(summarizeViewModel: SummarizeViewModel): StateFlow<SummarizeUiState> {
        return summarizeViewModel.uiState
    }

    fun handleSummarize(summarizeViewModel: SummarizeViewModel, text: String) {
        if (text.isNotBlank()) {
            summarizeViewModel.summarizeStreaming(text)
        }
    }

    fun getSummarizeUiState(summarizeViewModel: SummarizeViewModel): SummarizeUiState {
        return summarizeViewModel.uiState.value // Assuming uiState is a StateFlow
    }

    fun buildSummarizeContent(uiState: SummarizeUiState): String {
        return when (uiState) {
            SummarizeUiState.Initial, SummarizeUiState.Loading -> ""
            is SummarizeUiState.Success -> uiState.outputText
            is SummarizeUiState.Error -> uiState.errorMessage
        }
    }
