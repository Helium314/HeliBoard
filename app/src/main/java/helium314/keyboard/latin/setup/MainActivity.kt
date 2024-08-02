package helium314.keyboard.latin.setup

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
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

        // Check if the keyboard is already enabled and current
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
            binding.text1.visibility = View.GONE
            binding.setupOscar.visibility = View.GONE
            binding.tryOscar.visibility = View.VISIBLE

            openSelectionKeyboard()
        }
    }

    private fun isKeyboardEnabledAndCurrent(): Boolean {
        return UncachedInputMethodManagerUtils.isThisImeEnabled(this, inputMethodManager) &&
                UncachedInputMethodManagerUtils.isThisImeCurrent(this, inputMethodManager)
    }

    private fun invokeLanguageAndInputSettings() {
        val intent = Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS)
        startActivity(intent)
    }

    private fun invokeInputMethodPicker() {
        inputMethodManager.showInputMethodPicker()
    }

    private fun openSelectionKeyboard() {
        val intent = Intent(this, KeyboardselectionActivity::class.java)
        startActivity(intent)
        finish() // Close the setup activity
    }

    private fun checkSettingsAndUpdateButtonColors() {
        if (UncachedInputMethodManagerUtils.isThisImeEnabled(this, inputMethodManager)) {
            changeButtonColor(binding.enableId)
        }
        if (UncachedInputMethodManagerUtils.isThisImeCurrent(this, inputMethodManager)) {
            changeButtonColor(binding.setupID)
        }
    }

    private fun changeButtonColor(button: Button) {
        button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        button.setTextColor(Color.GRAY)
//        button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
//        button.compoundDrawablePadding = 50
    }
}
