package helium314.keyboard.latin.setup

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import helium314.keyboard.latin.R
import helium314.keyboard.latin.databinding.ActivityMainBinding
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var inputMethodManager: InputMethodManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        if (isKeyboardEnabledAndCurrent()) {
            openSelectionKeyboard()
        } else {
            setupButtons()
        }
    }

    override fun onResume() {
        super.onResume()
        checkSettingsAndUpdateButtonColors()
    }

    private fun setupButtons() {
        binding.enableId.setOnClickListener {
            binding.text1.visibility = View.VISIBLE
            binding.setupOscar.visibility = View.GONE
            binding.tryOscar.visibility = View.GONE

            invokeLanguageAndInputSettings()
        }

        binding.setupID.setOnClickListener {
            binding.text1.visibility = View.GONE
            binding.setupOscar.visibility = View.VISIBLE
            binding.tryOscar.visibility = View.GONE

            invokeInputMethodPicker()
        }

        binding.openKeyboardId.setOnClickListener {
            if (!isKeyboardEnabledAndCurrent()) {
                showToast("Please enable the keyboard before trying it out.")
                return@setOnClickListener
            }

            binding.text1.visibility = View.GONE
            binding.setupOscar.visibility = View.GONE
            binding.tryOscar.visibility = View.VISIBLE

            openSelectionKeyboard()
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isKeyboardEnabledAndCurrent(): Boolean {
        return UncachedInputMethodManagerUtils.isThisImeEnabled(this, inputMethodManager) &&
                UncachedInputMethodManagerUtils.isThisImeCurrent(this, inputMethodManager)
    }

    private fun invokeLanguageAndInputSettings() {
        val intent = Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
        startActivity(intent)
    }
    private var isSetupComplete = true

    private fun invokeInputMethodPicker() {
        if (!isSetupComplete) {
            showToast("Please complete the setup settings.")
            return
        }
        inputMethodManager.showInputMethodPicker()
    }

    private fun completeSetup() {
        isSetupComplete = true
        changeButtonColor(binding.setupID)
    }


    private fun openSelectionKeyboard() {
        val intent = Intent(this, KeyboardselectionActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun checkSettingsAndUpdateButtonColors() {
        if (UncachedInputMethodManagerUtils.isThisImeEnabled(this, inputMethodManager)) {
            changeButtonColor(binding.enableId)
        }
        if (UncachedInputMethodManagerUtils.isThisImeCurrent(this, inputMethodManager)) {
            changeButtonColor(binding.setupID)
            completeSetup()
        }
    }

    private fun changeButtonColor(button: Button) {
        button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        button.setTextColor(Color.GRAY)
        button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
    }
}
