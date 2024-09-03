package org.oscar.kb.latin.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.oscar.kb.R
import org.oscar.kb.databinding.ActivityMainBinding

import org.oscar.kb.latin.utils.UncachedInputMethodManagerUtils

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var inputMethodManager: InputMethodManager

    private var isEnableComplete = false

    private val inputMethodChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_INPUT_METHOD_CHANGED) {
                checkSettingsAndUpdateButtonColors()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        if (isKeyboardEnabledAndCurrent()) {
            openSelectionKeyboard()
        } else {
            setupButtons()
            checkSettingsAndUpdateButtonColors()
        }

        val filter = IntentFilter(Intent.ACTION_INPUT_METHOD_CHANGED)
        registerReceiver(inputMethodChangedReceiver, filter)
    }
    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(inputMethodChangedReceiver)
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

            if (!isEnableComplete) {
                showEnableKeyboardFirstToast()
                return@setOnClickListener
            }
            invokeInputMethodPicker()
        }

        binding.openKeyboardId.setOnClickListener {
            if (!isKeyboardEnabledAndCurrent()) {
                showCompleteSetupToast()
                return@setOnClickListener
            }
            openSelectionKeyboard()
            binding.text1.visibility = View.GONE
            binding.setupOscar.visibility = View.GONE
            binding.tryOscar.visibility = View.VISIBLE
        }
    }

    private fun isKeyboardEnabledAndCurrent(): Boolean {
        return _root_ide_package_.org.oscar.kb.latin.utils.UncachedInputMethodManagerUtils.isThisImeEnabled(this, inputMethodManager) &&
                _root_ide_package_.org.oscar.kb.latin.utils.UncachedInputMethodManagerUtils.isThisImeCurrent(this, inputMethodManager)
    }

    private fun invokeLanguageAndInputSettings() {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
        startActivityForResult(intent, ENABLE_REQUEST_CODE)
    }

    private fun invokeInputMethodPicker() {
        inputMethodManager.showInputMethodPicker()
    }

    private fun openSelectionKeyboard() {
        val intent = Intent(this, KeyboardselectionActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun checkSettingsAndUpdateButtonColors() {
        if (_root_ide_package_.org.oscar.kb.latin.utils.UncachedInputMethodManagerUtils.isThisImeEnabled(this, inputMethodManager)) {
            isEnableComplete = true
            changeButtonColor(binding.enableId)
        }
        if (_root_ide_package_.org.oscar.kb.latin.utils.UncachedInputMethodManagerUtils.isThisImeCurrent(this, inputMethodManager)) {
            changeButtonColor(binding.setupID)
            binding.openKeyboardId.isEnabled = true
        }
    }

    private fun changeButtonColor(button: Button) {
        button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#E0E0E0"))
        button.setTextColor(Color.GRAY)
        button.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_check, 0, 0, 0)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ENABLE_REQUEST_CODE) {
            checkSettingsAndUpdateButtonColors()
        }
    }

    override fun onResume() {
        super.onResume()
        checkSettingsAndUpdateButtonColors()
    }

    private fun showEnableKeyboardFirstToast() {
        Toast.makeText(this, R.string.enable_text, Toast.LENGTH_SHORT).show()
    }

    private fun showCompleteSetupToast() {
        Toast.makeText(this, R.string.enable_text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val ENABLE_REQUEST_CODE = 1
    }
}