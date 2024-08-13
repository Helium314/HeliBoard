package helium314.keyboard.latin.setup

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import helium314.keyboard.AIEngine.AIOutputEvent
import helium314.keyboard.AIEngine.OutputTextListener
import helium314.keyboard.AIEngine.SharedViewModel
import helium314.keyboard.AIEngine.SummarizeUiState
import helium314.keyboard.AIEngine.SummarizeViewModel
import helium314.keyboard.AIEngine.SummarizeViewModelFactory
import helium314.keyboard.gemini.GeminiClient
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.suggestions.SuggestionStripView
import helium314.keyboard.latin.suggestions.SummarizeTextProvider
import helium314.keyboard.latin.utils.Log
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import java.io.File
import java.security.AccessController.getContext

class KeyboardselectionActivity : AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener, SummarizeTextProvider, OutputTextListener {
    lateinit var ivOscar: ImageButton

    lateinit var etopenOscar: EditText
    lateinit var drawerLayout: DrawerLayout
    lateinit var ivBack: ImageView
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var ivSummarizeText: ImageView

    private lateinit var tvEnableKeyboard: TextView
    private lateinit var tvWelcomeText: TextView
    private val REQUEST_CODE_SPEECH_INPUT = 1

    val geminiClient = GeminiClient() // Assuming you have a way to create a GeminiClient instance
    val generativeModel = geminiClient.geminiFlashModel
    private var suggestionStripView: SuggestionStripView? = null // Assuming you have a reference


// Assuming you have a way to create a GenerativeModel instance
//    val viewModelFactory = SummarizeViewModelFactory(geminiClient, generativeModel)
//    val viewModel = ViewModelProvider(this, viewModelFactory)[SummarizeViewModel::class.java]
    //private lateinit var viewModel: SummarizeViewModel

    private val mViewModel by lazy {
        ViewModelProvider(
            this,
            SummarizeViewModelFactory(generativeModel)
        )[SummarizeViewModel::class.java]
    }
    private val mSharedViewModel by lazy {
        ViewModelProvider(this)[SharedViewModel::class.java]
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
        tvEnableKeyboard = findViewById(R.id.tv_enableKeyboard)
        tvWelcomeText = findViewById(R.id.tv_welcomeText)
        //Initialize my viewModel here


        //todo not needed now
        //mViewModel.setOutputTextListener(this)

        EventBus.getDefault().register(this)

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            checkPermission()
        }
        //val viewModel: SummarizeViewModel by viewModels()

        ivOscar.setOnClickListener {
            etopenOscar.visibility = View.VISIBLE
            tvWelcomeText.visibility = View.VISIBLE
            ivOscar.visibility = View.GONE
            tvEnableKeyboard.visibility = View.GONE
            etopenOscar.requestFocus()
            showKeyboard()
        }

        etopenOscar.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                hideOscarLogo()
            }
        }

        etopenOscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    hideOscarLogo()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        ivSummarizeText.setOnClickListener {
            if (etopenOscar.text.isNotBlank()) {
                handleSummarize(mViewModel, etopenOscar.text.toString())
                lifecycleScope.launch {
                    val summarizeUiStateFlow: StateFlow<SummarizeUiState> =
                        observeSummarizeUiState(mViewModel, etopenOscar.toString())
                    summarizeUiStateFlow.collect { uiState ->
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

        val rootView = findViewById<View>(android.R.id.content)
        rootView.viewTreeObserver.addOnGlobalLayoutListener {
            val rect = android.graphics.Rect()
            rootView.getWindowVisibleDisplayFrame(rect)
            val screenHeight = rootView.height
            val keypadHeight = screenHeight - rect.bottom
            if (keypadHeight > screenHeight * 0.15) {
                ivOscar.visibility = View.GONE
            } else {
                if (!etopenOscar.hasFocus()) {
                    hideOscarLogo()
                }
            }
        }
    }


//        mViewModel.aiOutputLiveData.observe(this) {
//            // Update UI with aiOutput
//            aiOutputTextView.text = event.aiOutput
//            Log.d("AIOutput", it)
//        }





    @Subscribe
    fun onAIOutputReceived(event: AIOutputEvent) {
        // Update UI with aiOutput
       val aiOutput = event.aiOutput
        Log.d("AIOutput Subscribe", aiOutput)

        if (aiOutput.isNotBlank()) {
            handleSummarize(mViewModel, aiOutput)
            lifecycleScope.launch {
                val summarizeUiStateFlow: StateFlow<SummarizeUiState> =
                    observeSummarizeUiState(mViewModel, aiOutput) // Pass aiOutput here
                summarizeUiStateFlow.collect { uiState ->
                    when (uiState) {
                        SummarizeUiState.Initial, SummarizeUiState.Loading -> {
                            // Handle loading state
                        }

                        is SummarizeUiState.Success -> {
                            // Handle success state
                            val outputText = buildSummarizeContent(uiState)
                            etopenOscar.setText(outputText)
                            mViewModel.updateOutputText(outputText)

                            // tod update universal text
                            //mViewModel.updateOutputTextUniversal(outputText)

// Now you can update the aiOutputTextView
                            Log.d("KeyboardActivity aiText sentBack", outputText)
                            // for updating new text
                            mSharedViewModel.updateOutputText(outputText)
                        }

                        is SummarizeUiState.Error -> {
                            // Handle error state
                            val errorMessage = buildSummarizeContent(uiState)
                            //Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                mViewModel.summarizeStreaming(aiOutput) // Use aiOutput here
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //EventBus.getDefault().unregister(this)
    }

    private fun showKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(etopenOscar, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideOscarLogo() {
        etopenOscar.visibility = View.GONE
        tvWelcomeText.visibility = View.VISIBLE
        ivOscar.visibility = View.VISIBLE
        tvEnableKeyboard.visibility = View.VISIBLE
    }

    private fun checkPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQUEST_CODE_SPEECH_INPUT
        )
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_privacy_policy -> {
                startActivity(Intent(this, PrivacyPolicyActivity::class.java))
            }

            R.id.nav_recommended_us -> {
                val packageName = BuildConfig.APPLICATION_ID
                val playStoreLink = "https://play.google.com/store/apps/details?id=$packageName"

                // Share the Play Store link
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, playStoreLink)
                }
                startActivity(Intent.createChooser(intent, "Share via"))
            }

            R.id.nav_email_us -> {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support.oscar@samyarth.org")
                    putExtra(Intent.EXTRA_SUBJECT, "Feedback on Oscar Keyboard")
                    putExtra(Intent.EXTRA_TEXT, "Hi team,\n\nI have the following feedback:")
                }
                startActivity(Intent.createChooser(emailIntent, "Send Email"))
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
        } else if (etopenOscar.visibility == View.VISIBLE) {
            hideOscarLogo()
        } else {
            super.onBackPressed()
        }
    }

    override fun getSummarizeText(): String {
        return etopenOscar.text.toString()
    }

    override fun setSummarizeText(text: String) {
        this.suggestionStripView?.setSummarizeText(text)
    }

    override fun onOutputTextChanged(outputText: String) {
        //aiOutput.text = outputText
    }

}

fun observeSummarizeUiState(
    summarizeViewModel: SummarizeViewModel,
    aiOutput: String
): StateFlow<SummarizeUiState> {
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
data class AIOutputEvent(val aiOutput: String)
