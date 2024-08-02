package helium314.keyboard.latin.setup

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
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

    private fun setupButtons() {
        // Set up click listeners
        binding.enableId.setOnClickListener {
            binding.text1.visibility = View.VISIBLE
            binding.setupOscar.visibility = View.GONE
            binding.tryOscar.visibility = View.GONE

            invokeLanguageAndInputSettings()
        }

        binding.setupID.setOnClickListener {
//            changeButtonColors(binding.setupID, true)
            binding.text1.visibility = View.GONE
            binding.setupOscar.visibility = View.VISIBLE
            binding.tryOscar.visibility = View.GONE

            invokeInputMethodPicker()
        }

        binding.openKeyboardId.setOnClickListener {
//            changeButtonColors(binding.openKeyboardId, true)
            binding.text1.visibility = View.GONE
            binding.setupOscar.visibility = View.GONE
            binding.tryOscar.visibility = View.VISIBLE

            openSelectionKeyboard()
        }
    }

    private fun changeButtonColors(button: View, isCompleted: Boolean) {
        if (isCompleted) {
            button.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.gesture_trail_color_lxx_light))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(resources.getColor(R.color.key_shifted_letter_hint_inactivated_color_holo))
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
}