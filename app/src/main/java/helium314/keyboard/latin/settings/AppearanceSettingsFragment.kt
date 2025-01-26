// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.util.TypedValueCompat
import androidx.core.view.forEach
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.customIconNames
import helium314.keyboard.latin.databinding.ReorderDialogItemBinding
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ResourceUtils
import helium314.keyboard.latin.utils.confirmDialog
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.infoDialog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.lang.Float.max
import java.lang.Float.min
import java.util.*

/**
 * "Appearance" settings sub screen.
 */
class AppearanceSettingsFragment : SubScreenFragment() {
    private var needsReload = false

    private val stylePref: ListPreference by lazy { preferenceScreen.findPreference(Settings.PREF_THEME_STYLE)!! }
    private val iconStylePref: ListPreference by lazy { preferenceScreen.findPreference(Settings.PREF_ICON_STYLE)!! }
    private val colorsPref: ListPreference by lazy { preferenceScreen.findPreference(Settings.PREF_THEME_COLORS)!! }
    private val colorsNightPref: ListPreference? by lazy { preferenceScreen.findPreference(Settings.PREF_THEME_COLORS_NIGHT) }
    private val dayNightPref: TwoStatePreference? by lazy { preferenceScreen.findPreference(Settings.PREF_THEME_DAY_NIGHT) }
    private val userColorsPref: Preference by lazy { preferenceScreen.findPreference("theme_select_colors")!! }
    private val userColorsPrefNight: Preference? by lazy { preferenceScreen.findPreference("theme_select_colors_night") }
    private val splitPref: TwoStatePreference? by lazy { preferenceScreen.findPreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD) }
    private val splitScalePref: Preference? by lazy { preferenceScreen.findPreference(Settings.PREF_SPLIT_SPACER_SCALE) }

    private val dayImageFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        loadImage(uri, false, false)
    }

    private val nightImageFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        loadImage(uri, true, false)
    }

    private val dayImageFilePickerLandscape = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        loadImage(uri, false, true)
    }

    private val nightImageFilePickerLandscape = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        loadImage(uri, true, true)
    }

    private val fontFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        saveCustomTypeface(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs_screen_appearance)

        removeUnsuitablePreferences()
        setupTheme()
        setColorPrefs(sharedPreferences.getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)!!)

        setupScalePrefs(Settings.PREF_KEYBOARD_HEIGHT_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
        setupScalePrefs(Settings.PREF_BOTTOM_PADDING_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
        setupScalePrefs(Settings.PREF_BOTTOM_PADDING_SCALE_LANDSCAPE, 0f)
        if (splitScalePref != null) {
            setupScalePrefs(Settings.PREF_SPLIT_SPACER_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
            splitScalePref?.isVisible = splitPref?.isChecked == true
            splitPref?.setOnPreferenceChangeListener { _, value ->
                splitScalePref?.isVisible = value as Boolean
                true
            }
        }
        findPreference<Preference>("custom_background_image")?.setOnPreferenceClickListener { onClickLoadImage(false) }
        findPreference<Preference>("custom_background_image_landscape")?.setOnPreferenceClickListener { onClickLoadImage(true) }
        findPreference<Preference>("custom_font")?.setOnPreferenceClickListener { onClickCustomFont() }
        findPreference<Preference>(Settings.PREF_CUSTOM_ICON_NAMES)?.setOnPreferenceClickListener {
            if (needsReload)
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
            onClickCustomizeIcons()
        }
    }

    override fun onPause() {
        super.onPause()
        if (needsReload)
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
        needsReload = false
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(prefs, key)
        needsReload = true // may not always necessary, but that's ok
    }

    private fun removeUnsuitablePreferences() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            removePreference(Settings.PREF_THEME_DAY_NIGHT)
            removePreference(Settings.PREF_THEME_COLORS_NIGHT)
        } else {
            // on P there is experimental support for night mode, exposed by some roms like LineageOS
            // try to detect this using UI_MODE_NIGHT_UNDEFINED, but actually the system could always report day too?
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_UNDEFINED
            ) {
                removePreference(Settings.PREF_THEME_DAY_NIGHT)
                removePreference(Settings.PREF_THEME_COLORS_NIGHT)
                removePreference("theme_select_colors_night")
            }
        }
        val metrics = requireContext().resources.displayMetrics
        val widthDp = TypedValueCompat.pxToDp(metrics.widthPixels.toFloat(), metrics)
        val heightDp = TypedValueCompat.pxToDp(metrics.heightPixels.toFloat(), metrics)
        if ((min(widthDp, heightDp) < 600 && max(widthDp, heightDp) < 720)) {
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD)
            removePreference(Settings.PREF_SPLIT_SPACER_SCALE)
        }
    }

    private fun setColorPrefs(style: String) {
        colorsPref.apply {
            entryValues = if (style == KeyboardTheme.STYLE_HOLO) KeyboardTheme.COLORS.toTypedArray()
                else KeyboardTheme.COLORS.filterNot { it == KeyboardTheme.THEME_HOLO_WHITE }.toTypedArray()
            entries = entryValues.getNamesFromResourcesIfAvailable("theme_name_")
            if (value !in entryValues)
                value = entryValues.first().toString()
            summary = entries[entryValues.indexOfFirst { it == value }]

            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                userColorsPref.isVisible = value == KeyboardTheme.THEME_USER
                true
            }
        }
        colorsNightPref?.apply {
            entryValues = if (style == KeyboardTheme.STYLE_HOLO) KeyboardTheme.COLORS_DARK.toTypedArray()
                else KeyboardTheme.COLORS_DARK.filterNot { it == KeyboardTheme.THEME_HOLO_WHITE }.toTypedArray()
            entries = entryValues.getNamesFromResourcesIfAvailable("theme_name_")
            if (value !in entryValues)
                value = entryValues.first().toString()
            summary = entries[entryValues.indexOfFirst { it == value }]

            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                userColorsPrefNight?.isVisible = value == KeyboardTheme.THEME_USER_NIGHT
                true
            }
        }
    }

    private fun setupTheme() {
        stylePref.apply {
            entryValues = KeyboardTheme.STYLES
            entries = entryValues.getNamesFromResourcesIfAvailable("style_name_")
            if (value !in entryValues)
                value = entryValues.first().toString()

            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                setColorPrefs(value.toString())
                true
            }
            summary = entries[entryValues.indexOfFirst { it == value }]
        }
        iconStylePref.apply {
            entryValues = KeyboardTheme.STYLES
            entries = entryValues.getNamesFromResourcesIfAvailable("style_name_")
            if (value !in entryValues)
                value = entryValues.first().toString()

            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                true
            }
            summary = entries[entryValues.indexOfFirst { it == value }]
        }
        dayNightPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            val yesThisIsBoolean = value as Boolean // apparently kotlin smartcast got less smart with 2.0.0
            colorsNightPref?.isVisible = yesThisIsBoolean
            userColorsPrefNight?.isVisible = yesThisIsBoolean && colorsNightPref?.value == KeyboardTheme.THEME_USER_NIGHT
            true
        }
        colorsNightPref?.isVisible = dayNightPref?.isChecked == true
        userColorsPref.isVisible = colorsPref.value == KeyboardTheme.THEME_USER
        userColorsPrefNight?.isVisible = dayNightPref?.isChecked == true && colorsNightPref?.value == KeyboardTheme.THEME_USER_NIGHT
    }

    // performance is not good, but not bad enough to justify work
    private fun onClickCustomizeIcons(): Boolean {
        val ctx = requireContext()
        val padding = ResourceUtils.toPx(8, ctx.resources)
        val ll = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, 3 * padding, padding, padding)
        }
        val builder = AlertDialog.Builder(ctx)
            .setTitle(R.string.customize_icons)
            .setView(ScrollView(context).apply { addView(ll) })
            .setPositiveButton(R.string.dialog_close, null)
        if (sharedPreferences.contains(Settings.PREF_CUSTOM_ICON_NAMES))
            builder.setNeutralButton(R.string.button_default) { _, _ ->
                confirmDialog(
                    ctx,
                    ctx.getString(R.string.customize_icons_reset_message),
                    ctx.getString(android.R.string.ok),
                    { sharedPreferences.edit().remove(Settings.PREF_CUSTOM_ICON_NAMES).apply() }
                )
            }
        val dialog = builder.create()

        val cf = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(ContextCompat.getColor(ctx, R.color.foreground), BlendModeCompat.SRC_IN)
        val iconsAndNames = KeyboardIconsSet.getAllIcons(ctx).keys.map { iconName ->
            val name = iconName.getStringResourceOrName("", ctx)
            if (name == iconName) iconName to iconName.getStringResourceOrName("label_", ctx).toString()
            else iconName to name.toString()
        }
        iconsAndNames.sortedBy { it.second }.forEach { (iconName, name) ->
            val b = ReorderDialogItemBinding.inflate(LayoutInflater.from(ctx), ll, true)
            b.reorderItemIcon.setImageDrawable(KeyboardIconsSet.instance.getNewDrawable(iconName, ctx))
            b.reorderItemIcon.colorFilter = cf
            b.reorderItemIcon.isVisible = true
            b.reorderItemName.text = name
            b.root.setOnClickListener {
                customizeIcon(iconName)
                dialog.dismiss()
            }
            b.reorderItemSwitch.isGone = true
            b.reorderItemDragIndicator.isGone = true
        }
        dialog.show()
        return true
    }

    // todo: icon size is an important difference between holo and others, but really awful to work with
    //  scaling the intrinsic icon width may look awful depending on display density
    private fun customizeIcon(iconName: String) {
        val ctx = requireContext()
        val rv = RecyclerView(ctx)
        rv.layoutManager = GridLayoutManager(ctx, 6)
        val padding = ResourceUtils.toPx(6, resources)
        rv.setPadding(padding, 3 * padding, padding, padding)
        val icons = KeyboardIconsSet.getAllIcons(ctx)
        val iconsList = icons[iconName].orEmpty().toSet().toMutableList()
        val iconsSet = icons.values.flatten().toMutableSet()
        iconsSet.removeAll(iconsList)
        iconsList.addAll(iconsSet)
        val foregroundColor = ContextCompat.getColor(ctx, R.color.foreground)
        val iconColorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(foregroundColor, BlendModeCompat.SRC_IN)

        var currentIconId = KeyboardIconsSet.instance.iconIds[iconName]

        val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = ImageView(ctx)
                v.colorFilter = iconColorFilter
                v.setPadding(padding, padding, padding, padding)
                return object : RecyclerView.ViewHolder(v) { }
            }

            override fun getItemCount(): Int = iconsList.size

            override fun onBindViewHolder(viewHolder: RecyclerView.ViewHolder, position: Int) {
                val icon = ContextCompat.getDrawable(ctx, iconsList[position])?.mutate()
                val imageView = viewHolder.itemView as? ImageView
                imageView?.setImageDrawable(icon)
                if (iconsList[position] == currentIconId) imageView?.setColorFilter(R.color.accent)
                else imageView?.colorFilter = iconColorFilter
                viewHolder.itemView.setOnClickListener { v ->
                    rv.forEach { (it as? ImageView)?.colorFilter = iconColorFilter }
                    (v as? ImageView)?.setColorFilter(R.color.accent)
                    currentIconId = iconsList[position]
                }
            }
        }
        rv.adapter = adapter
        val title = iconName.getStringResourceOrName("", ctx).takeUnless { it == iconName }
            ?: iconName.getStringResourceOrName("label_", ctx)
        val builder = AlertDialog.Builder(ctx)
            .setTitle(title)
            .setView(rv)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                runCatching {
                    val icons2 = customIconNames(sharedPreferences).toMutableMap()
                    icons2[iconName] = currentIconId?.let { resources.getResourceEntryName(it) } ?: return@runCatching
                    sharedPreferences.edit().putString(Settings.PREF_CUSTOM_ICON_NAMES, Json.encodeToString(icons2)).apply()
                    KeyboardIconsSet.instance.loadIcons(ctx)
                }
                onClickCustomizeIcons()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> onClickCustomizeIcons() }
        if (customIconNames(sharedPreferences).contains(iconName))
            builder.setNeutralButton(R.string.button_default) { _, _ ->
                runCatching {
                    val icons2 = customIconNames(sharedPreferences).toMutableMap()
                    icons2.remove(iconName)
                    if (icons2.isEmpty()) sharedPreferences.edit().remove(Settings.PREF_CUSTOM_ICON_NAMES).apply()
                    else sharedPreferences.edit().putString(Settings.PREF_CUSTOM_ICON_NAMES, Json.encodeToString(icons2)).apply()
                    KeyboardIconsSet.instance.loadIcons(ctx)
                }
                onClickCustomizeIcons()
            }

        builder.show()
    }

    private fun onClickLoadImage(landscape: Boolean): Boolean {
        if (Settings.readDayNightPref(sharedPreferences, resources)) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.day_or_night_image)
                .setPositiveButton(R.string.day_or_night_day) { _, _ -> customImageDialog(false, landscape) }
                .setNegativeButton(R.string.day_or_night_night) { _, _ -> customImageDialog(true, landscape) }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        } else {
            customImageDialog(false, landscape)
        }
        return true
    }

    private fun customImageDialog(night: Boolean, landscape: Boolean) {
        val imageFile = Settings.getCustomBackgroundFile(requireContext(), night, landscape)
        val builder = AlertDialog.Builder(requireContext())
            .setMessage(if (landscape) R.string.customize_background_image_landscape else R.string.customize_background_image)
            .setPositiveButton(R.string.button_load_custom) { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                if (landscape) {
                    if (night) nightImageFilePickerLandscape.launch(intent)
                    else dayImageFilePickerLandscape.launch(intent)
                } else {
                    if (night) nightImageFilePicker.launch(intent)
                    else dayImageFilePicker.launch(intent)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (imageFile.exists()) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                imageFile.delete()
                Settings.clearCachedBackgroundImages()
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
            }
        }
        builder.show()
    }

    private fun loadImage(uri: Uri, night: Boolean, landscape: Boolean) {
        val imageFile = Settings.getCustomBackgroundFile(requireContext(), night, landscape)
        FileUtils.copyContentUriToNewFile(uri, requireContext(), imageFile)
        try {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        } catch (_: Exception) {
            infoDialog(requireContext(), R.string.file_read_error)
            imageFile.delete()
        }
        Settings.clearCachedBackgroundImages()
        KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
    }

    private fun onClickCustomFont(): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        val fontFile = Settings.getCustomFontFile(requireContext())
        if (fontFile.exists()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.custom_font)
                .setPositiveButton(R.string.load) { _, _ -> fontFilePicker.launch(intent) }
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.delete) { _, _ ->
                    fontFile.delete()
                    Settings.clearCachedTypeface()
                    KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
                }
                .show()
        } else {
            fontFilePicker.launch(intent)
        }
        return true
    }

    private fun saveCustomTypeface(uri: Uri) {
        val fontFile = Settings.getCustomFontFile(requireContext())
        val tempFile = File(DeviceProtectedUtils.getFilesDir(context), "temp_file")
        FileUtils.copyContentUriToNewFile(uri, requireContext(), tempFile)
        try {
            val typeface = Typeface.createFromFile(tempFile)
            fontFile.delete()
            tempFile.renameTo(fontFile)
            Settings.clearCachedTypeface()
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
        } catch (_: Exception) {
            infoDialog(requireContext(), R.string.file_read_error)
            tempFile.delete()
        }
    }

    private fun setupScalePrefs(prefKey: String, defaultValue: Float) {
        val prefs = sharedPreferences
        val pref = findPreference(prefKey) as? SeekBarDialogPreference
        pref?.setInterface(object : SeekBarDialogPreference.ValueProxy {

            private fun getValueFromPercentage(percentage: Int) =  percentage / PERCENTAGE_FLOAT

            private fun getPercentageFromValue(floatValue: Float) = (floatValue * PERCENTAGE_FLOAT).toInt()

            override fun writeValue(value: Int, key: String) = prefs.edit().putFloat(key, getValueFromPercentage(value)).apply()

            override fun writeDefaultValue(key: String) = prefs.edit().remove(key).apply()

            override fun readValue(key: String) = getPercentageFromValue(prefs.getFloat(prefKey, defaultValue))

            override fun readDefaultValue(key: String) = getPercentageFromValue(defaultValue)

            override fun getValueText(value: Int) = String.format(Locale.ROOT, "%d%%", value)

            override fun feedbackValue(value: Int) = Unit
        })
    }

    private fun Array<CharSequence>.getNamesFromResourcesIfAvailable(prefix: String) =
        map { it.getStringResourceOrName(prefix, requireContext()) }.toTypedArray()

    companion object {
        private const val PERCENTAGE_FLOAT = 100.0f
    }
}
