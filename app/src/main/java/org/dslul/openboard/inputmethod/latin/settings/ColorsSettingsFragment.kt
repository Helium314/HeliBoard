package org.dslul.openboard.inputmethod.latin.settings

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.view.forEachIndexed
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.flag.BubbleFlag
import com.skydoves.colorpickerview.flag.FlagMode
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager
import org.dslul.openboard.inputmethod.latin.databinding.ColorSettingBinding
import org.dslul.openboard.inputmethod.latin.databinding.ColorSettingsBinding
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.ExecutorUtils
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils

open class ColorsSettingsFragment : Fragment(R.layout.color_settings) {

    private val binding by viewBinding(ColorSettingsBinding::bind)
    open val isNight = false
    open val titleResId = R.string.select_user_colors
    private val prefs by lazy { DeviceProtectedUtils.getSharedPreferences(requireContext()) }
    private val colorPrefs = listOf(
        Settings.PREF_COLOR_BACKGROUND_SUFFIX,
        Settings.PREF_COLOR_KEYS_SUFFIX,
        Settings.PREF_COLOR_TEXT_SUFFIX,
        Settings.PREF_COLOR_HINT_TEXT_SUFFIX,
        Settings.PREF_COLOR_ACCENT_SUFFIX,
    )

    override fun onResume() {
        super.onResume()
        val activity: Activity? = activity
        if (activity is AppCompatActivity) {
            val actionBar = activity.supportActionBar ?: return
            actionBar.setTitle(titleResId)
        }
        if (isNight != ResourceUtils.isNight(requireContext().resources))
            // reload to get the right configuration
            // todo: this does not work, keyboard also reloading with some other context
            reloadKeyboard(false)
    }

    override fun onPause() {
        super.onPause()
        if (isNight != ResourceUtils.isNight(requireContext().resources))
            // reload again so the correct configuration is applied
            // todo: this does not work, keyboard also reloading with some other context
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val colorPrefNames = listOf(
            R.string.select_color_background,
            R.string.select_color_key_background,
            R.string.select_color_key,
            R.string.select_color_key_hint,
            R.string.select_color_accent,
        ).map { requireContext().getString(it) }
        val prefPrefix = if (isNight) Settings.PREF_THEME_USER_COLOR_NIGHT_PREFIX else Settings.PREF_THEME_USER_COLOR_PREFIX
        colorPrefs.forEachIndexed { index, colorPref ->
            val csb = ColorSettingBinding.inflate(layoutInflater, binding.colorSettingsContainer, true)
            csb.colorSwitch.isChecked = !prefs.getBoolean(prefPrefix + colorPref + Settings.PREF_AUTO_USER_COLOR_SUFFIX, true)
            csb.colorPreview.setColorFilter(Settings.readUserColor(prefs, requireContext(), colorPrefs[index], isNight))
            csb.colorText.text = colorPrefNames[index]
            if (!csb.colorSwitch.isChecked) {
                csb.colorSummary.setText(R.string.auto_user_color)
            }
            val switchListener = CompoundButton.OnCheckedChangeListener { _, b ->
                val hidden = RichInputMethodManager.getInstance().inputMethodManager.hideSoftInputFromWindow(binding.dummyText.windowToken, 0)
                prefs.edit { putBoolean(prefPrefix + colorPref + Settings.PREF_AUTO_USER_COLOR_SUFFIX, !b) }
                if (b) csb.colorSummary.text = ""
                else csb.colorSummary.setText(R.string.auto_user_color)
                reloadKeyboard(hidden)
                updateColorPreviews()
            }
            csb.colorSwitch.setOnCheckedChangeListener(switchListener)

            val clickListener = View.OnClickListener {
                val hidden = RichInputMethodManager.getInstance().inputMethodManager.hideSoftInputFromWindow(binding.dummyText.windowToken, 0)
                val b = ColorPickerDialog.Builder(requireContext())
                    .setTitle(colorPrefNames[index])
                    // todo: later it should be activated, but currently setting alpha leads to glitches,
                    //  e.g. when setting alpha on key text it's not applied for key icons, but for emojis
                    .attachAlphaSlideBar(false)
                    .setPositiveButton(android.R.string.ok, ColorEnvelopeListener { envelope, _ ->
                        prefs.edit { putInt(prefPrefix + colorPrefs[index], envelope.color) }
                        if (!csb.colorSwitch.isChecked) {
                            prefs.edit { putBoolean(prefPrefix + colorPref + Settings.PREF_AUTO_USER_COLOR_SUFFIX, false) }
                            csb.colorSwitch.setOnCheckedChangeListener(null)
                            csb.colorSwitch.isChecked = true
                            csb.colorSummary.text = ""
                            csb.colorSwitch.setOnCheckedChangeListener(switchListener)
                            reloadKeyboard(hidden)
                            updateColorPreviews()
                            return@ColorEnvelopeListener
                        }
                        reloadKeyboard(hidden)
                        updateColorPreviews()
                    })
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        if (hidden)
                            RichInputMethodManager.getInstance().inputMethodManager.showSoftInput(binding.dummyText, 0)
                    }
                val initialColor = if (prefs.contains(prefPrefix + colorPref))
                    prefs.getInt(prefPrefix + colorPref, Color.GRAY)
                else
                    Settings.readUserColor(prefs, requireContext(), colorPrefs[index], isNight)
                b.colorPickerView.setInitialColor(initialColor)
                b.colorPickerView.setPadding(15)
                // set better color drawable? neither the white circle nor the plus is nice
                b.colorPickerView.setSelectorDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_plus))
                b.colorPickerView.flagView = BubbleFlag(requireContext()).apply { flagMode = FlagMode.ALWAYS }
                b.show()
            }
            csb.colorTextContainer.setOnClickListener(clickListener)
            csb.colorPreview.setOnClickListener(clickListener)
        }
    }

    private fun updateColorPreviews() {
        binding.colorSettingsContainer.forEachIndexed { index, view ->
            val color = Settings.readUserColor(prefs, requireContext(), colorPrefs[index], isNight)
            view.findViewById<ImageView>(R.id.color_preview)?.setColorFilter(color)
        }
    }

    private fun reloadKeyboard(show: Boolean) {
        // todo: any way to make some kind of "light update" to keyboard?
        //  only reloading main keyboard view is necessary...
        //  or get an actual (live) preview instead of the full keyboard?
        //  or accelerate keyboard inflate, a big here issue is emojiCategory creating many keyboards
        KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
        // todo: this does not work, keyboard also reloading with some other context
//        KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(Settings.getDayNightContext(requireContext(), isNight))
        if (!show) return
        Thread.sleep(100) // some pause is necessary to avoid visual glitches
        RichInputMethodManager.getInstance().inputMethodManager.showSoftInput(binding.dummyText, 0)
        return
        // for some reason showing again does not work when running with executor
        // but when running without it's noticeably slow, and sometimes produces glitches
        // todo: decider whether to just hide, or have some slowdown and show again
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
            if (!show) return@execute
            Thread.sleep(100)
            RichInputMethodManager.getInstance().inputMethodManager.showSoftInput(binding.dummyText, 0)
        }
    }


}

class ColorsNightSettingsFragment : ColorsSettingsFragment() {
    override val isNight = true
    override val titleResId = R.string.select_user_colors_night
}
