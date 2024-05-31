// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.MenuProvider
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.rarepebble.colorpicker.ColorPickerView
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.RichInputMethodManager
import helium314.keyboard.latin.common.ColorType
import helium314.keyboard.latin.common.readAllColorsMap
import helium314.keyboard.latin.common.writeAllColorsMap
import helium314.keyboard.latin.databinding.ColorSettingBinding
import helium314.keyboard.latin.databinding.ColorSettingsBinding
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.ResourceUtils

open class ColorsSettingsFragment : Fragment(R.layout.color_settings), MenuProvider {

    private val binding by viewBinding(ColorSettingsBinding::bind)
    open val isNight = false
    open val titleResId = R.string.select_user_colors

    // 0 for default
    // 1 for more colors
    // 2 for all colors
    private var moreColors: Int
        get() = prefs.getInt(Settings.PREF_SHOW_MORE_COLORS, 0)
        set(value) { prefs.edit().putInt(Settings.PREF_SHOW_MORE_COLORS, value).apply() }

    private val prefs by lazy { DeviceProtectedUtils.getSharedPreferences(requireContext()) }

    private val colorPrefsAndNames by lazy {
        listOf(
            Settings.PREF_COLOR_BACKGROUND_SUFFIX to R.string.select_color_background,
            Settings.PREF_COLOR_KEYS_SUFFIX to R.string.select_color_key_background,
            Settings.PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX to R.string.select_color_functional_key_background,
            Settings.PREF_COLOR_SPACEBAR_SUFFIX to R.string.select_color_spacebar_background,
            Settings.PREF_COLOR_TEXT_SUFFIX to R.string.select_color_key,
            Settings.PREF_COLOR_HINT_TEXT_SUFFIX to R.string.select_color_key_hint,
            Settings.PREF_COLOR_SUGGESTION_TEXT_SUFFIX to R.string.select_color_suggestion,
            Settings.PREF_COLOR_SPACEBAR_TEXT_SUFFIX to R.string.select_color_spacebar_text,
            Settings.PREF_COLOR_ACCENT_SUFFIX to R.string.select_color_accent,
            Settings.PREF_COLOR_GESTURE_SUFFIX to R.string.select_color_gesture,
        ).map { it.first to requireContext().getString(it.second) }
    }

    private val colorPrefsToHideInitially by lazy {
        listOf(Settings.PREF_COLOR_SUGGESTION_TEXT_SUFFIX,Settings.PREF_COLOR_SPACEBAR_TEXT_SUFFIX, Settings.PREF_COLOR_GESTURE_SUFFIX) +
            if (prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false)) listOf(Settings.PREF_COLOR_SPACEBAR_SUFFIX)
            else listOf(Settings.PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX)
    }

    override fun onResume() {
        super.onResume()
        if (isNight != ResourceUtils.isNight(requireContext().resources)) {
            // reload to get the right configuration
            forceOppositeTheme = true
            reloadKeyboard(false)
        }
        val activity = activity
        if (activity is AppCompatActivity) {
            val actionBar = activity.supportActionBar ?: return
            actionBar.setTitle(titleResId)
        }
        activity?.addMenuProvider(this)
    }

    override fun onPause() {
        super.onPause()
        forceOppositeTheme = false
        if (isNight != ResourceUtils.isNight(requireContext().resources))
            // reload again so the correct configuration is applied
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
        activity?.removeMenuProvider(this)
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.NONE, 0, Menu.NONE, R.string.main_colors)
        menu.add(Menu.NONE, 1, Menu.NONE, R.string.more_colors)
        menu.add(Menu.NONE, 2, Menu.NONE, R.string.all_colors)
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        // necessary, even though we only have a single menu item
        // because the back arrow on top absurdly is implemented as a menu item
        if (menuItem.itemId in 0..2) {
            if (moreColors == menuItem.itemId) return true
            if (moreColors == 2 || menuItem.itemId == 2) {
                RichInputMethodManager.getInstance().inputMethodManager.hideSoftInputFromWindow(binding.dummyText.windowToken, 0)
                reloadKeyboard(false)
            }
            moreColors = menuItem.itemId
            binding.info.isGone = menuItem.itemId != 2
            updateColorPrefs()
            return true
        }
        return false
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // updateColorPrefs must be called after super.onViewStateRestored because for some reason Android
        // decides to set the checked state of the bottom-most switch to ALL switches during "restore"
        updateColorPrefs()
    }

    private fun updateColorPrefs() {
        binding.colorSettingsContainer.removeAllViews()
        if (moreColors == 2) showAllColors()
        else showMainColors()
    }

    private fun showAllColors() {
        val colors = readAllColorsMap(prefs, isNight)
        ColorType.entries.forEach { type ->
            val color = colors[type] ?: Color.GRAY
            val csb = ColorSettingBinding.inflate(layoutInflater, binding.colorSettingsContainer, true)
            csb.root.tag = type
            csb.colorSwitch.isGone = true
            csb.colorPreview.setColorFilter(color)
            csb.colorText.text = type.name

            val clickListener = View.OnClickListener {
                val hidden = RichInputMethodManager.getInstance().inputMethodManager.hideSoftInputFromWindow(binding.dummyText.windowToken, 0)
                val picker = ColorPickerView(requireContext())
                picker.showAlpha(type != ColorType.MAIN_BACKGROUND) // background behind background looks broken and sometimes is dark, sometimes light
                picker.showHex(true)
                picker.showPreview(true)
                picker.color = color
                val builder = AlertDialog.Builder(requireContext())
                builder
                    .setTitle(type.name)
                    .setView(picker)
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        val colorMap = readAllColorsMap(prefs, isNight) // better re-read it
                        colorMap[type] = picker.color
                        writeAllColorsMap(colorMap, prefs, isNight)
                        updateAllColorPreviews()
                        reloadKeyboard(hidden)
                    }
                val dialog = builder.create()
                dialog.show()
                // Reduce the size of the dialog in portrait mode
                val wrapContent = WindowManager.LayoutParams.WRAP_CONTENT
                val widthPortrait = (resources.displayMetrics.widthPixels * 0.80f).toInt()
                val orientation = (resources.configuration.orientation)
                if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                    dialog.window?.setLayout(wrapContent, wrapContent)
                else
                    dialog.window?.setLayout(widthPortrait, wrapContent)
            }
            csb.colorTextContainer.setOnClickListener(clickListener)
            csb.colorPreview.setOnClickListener(clickListener)
        }
    }

    private fun showMainColors() {
        val prefPrefix = if (isNight) Settings.PREF_THEME_USER_COLOR_NIGHT_PREFIX else Settings.PREF_THEME_USER_COLOR_PREFIX
        colorPrefsAndNames.forEachIndexed { index, (colorPref, colorPrefName) ->
            val autoColor = prefs.getBoolean(prefPrefix + colorPref + Settings.PREF_AUTO_USER_COLOR_SUFFIX, true)
            if (moreColors == 0 && colorPref in colorPrefsToHideInitially && autoColor)
                return@forEachIndexed
            val csb = ColorSettingBinding.inflate(layoutInflater, binding.colorSettingsContainer, true)
            csb.root.tag = index
            csb.colorSwitch.isChecked = !autoColor
            csb.colorPreview.setColorFilter(Settings.readUserColor(prefs, requireContext(), colorPref, isNight))
            csb.colorText.text = colorPrefName
            if (!csb.colorSwitch.isChecked) {
                csb.colorSummary.setText(R.string.auto_user_color)
            }
            val switchListener = CompoundButton.OnCheckedChangeListener { _, b ->
                val hidden = RichInputMethodManager.getInstance().inputMethodManager.hideSoftInputFromWindow(binding.dummyText.windowToken, 0)
                prefs.edit { putBoolean(prefPrefix + colorPref + Settings.PREF_AUTO_USER_COLOR_SUFFIX, !b) }
                if (b) csb.colorSummary.text = ""
                else csb.colorSummary.setText(R.string.auto_user_color)
                reloadKeyboard(hidden)
                updateMainColorPreviews()
            }
            csb.colorSwitch.setOnCheckedChangeListener(switchListener)

            val clickListener = View.OnClickListener {
                val hidden = RichInputMethodManager.getInstance().inputMethodManager.hideSoftInputFromWindow(binding.dummyText.windowToken, 0)
                val initialColor = Settings.readUserColor(prefs, requireContext(), colorPref, isNight)
                val picker = ColorPickerView(requireContext())
                picker.showAlpha(colorPref != Settings.PREF_COLOR_BACKGROUND_SUFFIX) // background behind background looks broken and sometimes is dark, sometimes light
                picker.showHex(true)
                picker.showPreview(true)
                picker.color = initialColor
                // without the observer, the color previews in the background don't update
                // but storing the pref and resetting on cancel is really bad style, so this is disabled for now
/*                picker.addColorObserver { observer ->
                    prefs.edit { putInt(prefPrefix + colorPref, observer.color) }
                    if (!csb.colorSwitch.isChecked) {
                        prefs.edit { putBoolean(prefPrefix + colorPref + Settings.PREF_AUTO_USER_COLOR_SUFFIX, false) }
                        csb.colorSwitch.setOnCheckedChangeListener(null)
                        csb.colorSwitch.isChecked = true
                        csb.colorSummary.text = ""
                        csb.colorSwitch.setOnCheckedChangeListener(switchListener)
                        updateColorPreviews()
                        return@addColorObserver
                    }
                    updateColorPreviews()
                }*/
                val builder = AlertDialog.Builder(requireContext())
                builder
                    .setTitle(colorPrefName)
                    .setView(picker)
                    .setCancelable(false)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        prefs.edit { putInt(prefPrefix + colorPref, picker.color) }
                        if (!csb.colorSwitch.isChecked) {
                            prefs.edit { putBoolean(prefPrefix + colorPref + Settings.PREF_AUTO_USER_COLOR_SUFFIX, false) }
                            csb.colorSwitch.setOnCheckedChangeListener(null)
                            csb.colorSwitch.isChecked = true
                            csb.colorSummary.text = ""
                            csb.colorSwitch.setOnCheckedChangeListener(switchListener)
                            updateMainColorPreviews()
                        } else {
                            updateMainColorPreviews()
                        }
                        reloadKeyboard(hidden)
                    }
                // The Default button appears only when a color has already been defined
                if (csb.colorSwitch.isChecked) {
                    // Reset the color and the color picker to their initial state
                    builder.setNeutralButton(R.string.button_default) { _, _ ->
                        prefs.edit { remove(prefPrefix + colorPref + Settings.PREF_AUTO_USER_COLOR_SUFFIX) }
                        csb.colorSwitch.isChecked = false
                    }
                }
                val dialog = builder.create()
                dialog.show()
                // Reduce the size of the dialog in portrait mode
                val wrapContent = WindowManager.LayoutParams.WRAP_CONTENT
                val widthPortrait = (resources.displayMetrics.widthPixels * 0.80f).toInt()
                val orientation = (resources.configuration.orientation)
                if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                    dialog.window?.setLayout(wrapContent, wrapContent)
                else
                    dialog.window?.setLayout(widthPortrait, wrapContent)
            }
            csb.colorTextContainer.setOnClickListener(clickListener)
            csb.colorPreview.setOnClickListener(clickListener)
        }
    }

    private fun updateMainColorPreviews() {
        binding.colorSettingsContainer.forEach { view ->
            val index = view.tag as? Int ?: return@forEach
            val color = Settings.readUserColor(prefs, requireContext(), colorPrefsAndNames[index].first, isNight)
            view.findViewById<ImageView>(R.id.color_preview)?.setColorFilter(color)
        }
    }

    private fun updateAllColorPreviews() {
        val colorMap = readAllColorsMap(prefs, isNight)
        binding.colorSettingsContainer.forEach { view ->
            val type = view.tag as? ColorType ?: return@forEach
            val color = colorMap[type] ?: Color.GRAY
            view.findViewById<ImageView>(R.id.color_preview)?.setColorFilter(color)
        }
    }

    private fun reloadKeyboard(show: Boolean) {
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
            if (!show) return@execute
            // for some reason showing again does not work when running with executor
            // but when running without it's noticeably slow, and sometimes produces glitches
            Thread.sleep(100)
            RichInputMethodManager.getInstance().inputMethodManager.showSoftInput(binding.dummyText, 0)
        }
    }

    companion object {
        var forceOppositeTheme = false
    }
}

class ColorsNightSettingsFragment : ColorsSettingsFragment() {
    override val isNight = true
    override val titleResId = R.string.select_user_colors_night
}
