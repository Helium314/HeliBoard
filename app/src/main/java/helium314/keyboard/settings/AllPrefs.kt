// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.UserDictionaryListFragment
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SpannableStringUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AllPrefs(context: Context) {
    private val list = createPrefDefs(context)

    val map: Map<String, PrefDef> = HashMap<String, PrefDef>(list.size).apply {
        list.forEach {
            if (put(it.key, it) != null)
                throw IllegalArgumentException("key $it added twice")
        }
    }

    // could be more elaborate, but should be good enough for a start
    fun filter(searchTerm: String): List<PrefDef> {
        val term = searchTerm.lowercase()
        val results = mutableSetOf<PrefDef>()
        list.forEach { if (it.title.lowercase().startsWith(term)) results.add(it) }
        list.forEach { if (it.title.lowercase().split(' ').any { it.startsWith(term) }) results.add(it) }
        list.forEach {
            if (it.description?.lowercase()?.split(' ')?.any { it.startsWith(term) } == true)
                results.add(it)
        }
        return results.toList()
    }
}

class PrefDef(
    context: Context,
    val key: String,
    @StringRes titleId: Int,
    @StringRes descriptionId: Int? = null,
    private val compose: @Composable (PrefDef) -> Unit
) {
    val title = context.getString(titleId)
    val description = descriptionId?.let { context.getString(it) }

    @Composable
    fun Preference() {
        compose(this)
    }
}

// this will be looooooong
private fun createPrefDefs(context: Context) = listOf(
    // ---------------- correction ------------------
    PrefDef(context, NonSettingsPrefs.EDIT_PERSONAL_DICTIONARY, R.string.edit_personal_dictionary) {
        val ctx = LocalContext.current
        Preference(
            name = stringResource(R.string.edit_personal_dictionary),
            onClick = { ctx.getActivity()?.switchTo(UserDictionaryListFragment()) },
        )
    },
    PrefDef(context,
        Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
        R.string.prefs_block_potentially_offensive_title,
        R.string.prefs_block_potentially_offensive_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context,
        Settings.PREF_AUTO_CORRECTION,
        R.string.autocorrect,
        R.string.auto_correction_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context,
        Settings.PREF_MORE_AUTO_CORRECTION,
        R.string.more_autocorrect,
        R.string.more_autocorrect_summary
    ) {
        SwitchPreference(it, true) // todo: shouldn't it better be false?
    },
    PrefDef(context,
        Settings.PREF_AUTOCORRECT_SHORTCUTS,
        R.string.auto_correct_shortcuts,
        R.string.auto_correct_shortcuts_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context,
        Settings.PREF_AUTO_CORRECTION_CONFIDENCE,
        R.string.auto_correction_confidence,
    ) { def ->
        var showDialog by remember { mutableStateOf(false) }
        // todo: arrays are arranged in a rather absurd way... this should be improved
        val items = listOf(
            stringResource(R.string.auto_correction_threshold_mode_modest) to "0",
            stringResource(R.string.auto_correction_threshold_mode_aggressive) to "1",
            stringResource(R.string.auto_correction_threshold_mode_very_aggressive) to "2",
        )
        val prefs = DeviceProtectedUtils.getSharedPreferences(LocalContext.current)
        val selected = items.firstOrNull { it.second == prefs.getString(def.key, "0") }
        Preference(
            name = def.title,
            description = selected?.first,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            ListPickerDialog(
                onDismissRequest = {showDialog = false },
                items = items,
                onItemSelected = {
                    if (it != selected)
                        prefs.edit().putString(def.key, it.second).apply()
                },
                selectedItem = selected,
                title = { Text(def.title) },
                getItemName = { it.first }
            )
        }
    },
    PrefDef(context,
        Settings.PREF_AUTO_CAP,
        R.string.auto_cap,
        R.string.auto_cap_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context,
        Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
        R.string.use_double_space_period,
        R.string.use_double_space_period_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context,
        Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION,
        R.string.autospace_after_punctuation,
        R.string.autospace_after_punctuation_summary
    ) {
        SwitchPreference(it, false)
    },
    PrefDef(context,
        Settings.PREF_SHOW_SUGGESTIONS,
        R.string.prefs_show_suggestions,
        R.string.prefs_show_suggestions_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context,
        Settings.PREF_ALWAYS_SHOW_SUGGESTIONS,
        R.string.prefs_always_show_suggestions,
        R.string.prefs_always_show_suggestions_summary
    ) {
        SwitchPreference(it, false)
    },
    PrefDef(context,
        Settings.PREF_KEY_USE_PERSONALIZED_DICTS,
        R.string.use_personalized_dicts,
        R.string.use_personalized_dicts_summary
    ) { prefDef ->
        var showConfirmDialog by remember { mutableStateOf(false) }
        SwitchPreference(
            prefDef,
            true,
            allowCheckedChange = {
                showConfirmDialog = !it
                it
            }
        )
        if (showConfirmDialog) {
            val prefs = DeviceProtectedUtils.getSharedPreferences(LocalContext.current)
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = {
                    prefs.edit().putBoolean(prefDef.key, false).apply()
                },
                text = { Text(stringResource(R.string.disable_personalized_dicts_message)) }
            )
        }

    },
    PrefDef(context,
        Settings.PREF_BIGRAM_PREDICTIONS,
        R.string.bigram_prediction,
        R.string.bigram_prediction_summary
    ) {
        SwitchPreference(it, true) { themeChanged = true }
    },
    PrefDef(context,
        Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER,
        R.string.center_suggestion_text_to_enter,
        R.string.center_suggestion_text_to_enter_summary
    ) {
        SwitchPreference(it, false)
    },
    PrefDef(context,
        Settings.PREF_SUGGEST_CLIPBOARD_CONTENT,
        R.string.suggest_clipboard_content,
        R.string.suggest_clipboard_content_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context,
        Settings.PREF_USE_CONTACTS,
        R.string.use_contacts_dict,
        R.string.use_contacts_dict_summary
    ) {
        val activity = LocalContext.current.getActivity() ?: return@PrefDef
        var granted by remember { mutableStateOf(PermissionsUtil.checkAllPermissionsGranted(activity, Manifest.permission.READ_CONTACTS)) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
        }
        SwitchPreference(
            it,
            false,
            allowCheckedChange = {
                if (it && !granted) {
                    launcher.launch(Manifest.permission.READ_CONTACTS)
                    false
                } else true
            }
        )
    },
    PrefDef(context,
        Settings.PREF_ADD_TO_PERSONAL_DICTIONARY,
        R.string.add_to_personal_dictionary,
        R.string.add_to_personal_dictionary_summary
    ) {
        SwitchPreference(it, false)
    },
    // ---------------- about ------------------
    PrefDef(context, NonSettingsPrefs.APP, R.string.english_ime_name, R.string.app_slogan) {
        Preference(
            name = it.title,
            description = it.description,
            onClick = { },
            icon = R.drawable.ic_launcher_foreground
        )
    },
    PrefDef(context, NonSettingsPrefs.VERSION, R.string.version) {
        var count by rememberSaveable { mutableIntStateOf(0) }
        val ctx = LocalContext.current
        val prefs = DeviceProtectedUtils.getSharedPreferences(ctx)
        Preference(
            name = it.title,
            description = stringResource(R.string.version_text, BuildConfig.VERSION_NAME),
            onClick = {
                if (prefs.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, false) || BuildConfig.DEBUG)
                    return@Preference
                count++
                if (count < 5) return@Preference
                prefs.edit().putBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, true).apply()
                Toast.makeText(ctx, R.string.prefs_debug_settings_enabled, Toast.LENGTH_LONG).show()
            },
            icon = R.drawable.ic_settings_about_foreground
        )
    },
    PrefDef(context, NonSettingsPrefs.LICENSE, R.string.license, R.string.gnu_gpl) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                val intent = Intent()
                intent.data = "https://github.com/Helium314/HeliBoard/blob/main/LICENSE-GPL-3".toUri()
                intent.action = Intent.ACTION_VIEW
                ctx.startActivity(intent)
            },
            icon = R.drawable.ic_settings_about_license_foreground
        )
    },
    PrefDef(context, NonSettingsPrefs.HIDDEN_FEATURES, R.string.hidden_features_title, R.string.hidden_features_summary) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                // Compose dialogs are in a rather sad state. They don't understand HTML, and don't scroll without customization.
                // this should be re-done in compose, but... bah
                val link = ("<a href=\"https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()\">"
                        + ctx.getString(R.string.hidden_features_text) + "</a>")
                val message = ctx.getString(R.string.hidden_features_message, link)
                val dialogMessage = SpannableStringUtils.fromHtml(message)
                val builder = androidx.appcompat.app.AlertDialog.Builder(ctx)
                    .setIcon(R.drawable.ic_settings_about_hidden_features)
                    .setTitle(R.string.hidden_features_title)
                    .setMessage(dialogMessage)
                    .setPositiveButton(R.string.dialog_close, null)
                    .create()
                builder.show()
                (builder.findViewById<View>(android.R.id.message) as TextView).movementMethod = LinkMovementMethod.getInstance()
            },
            icon = R.drawable.ic_settings_about_hidden_features_foreground
        )
    },
    PrefDef(context, NonSettingsPrefs.GITHUB, R.string.about_github_link) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                val intent = Intent()
                intent.data = "https://github.com/Helium314/HeliBoard".toUri()
                intent.action = Intent.ACTION_VIEW
                ctx.startActivity(intent)
            },
            icon = R.drawable.ic_settings_about_github_foreground
        )
    },
    PrefDef(context, NonSettingsPrefs.SAVE_LOG, R.string.save_log) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            scope.launch(Dispatchers.IO) {
                ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                    os.bufferedWriter().use { it.write(Log.getLog().joinToString("\n")) }
                }
            }
        }
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        ctx.getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_log_${System.currentTimeMillis()}.txt"
                    )
                    .setType("text/plain")
                launcher.launch(intent)
            },
            icon = R.drawable.ic_settings_about_log_foreground
        )
    },
)

// todo: move somewhere else
fun Context.getActivity(): ComponentActivity? {
    val componentActivity = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> null
    }
    return componentActivity
}

object NonSettingsPrefs {
    const val EDIT_PERSONAL_DICTIONARY = "edit_personal_dictionary"
    const val APP = "app"
    const val VERSION = "version"
    const val LICENSE = "license"
    const val HIDDEN_FEATURES = "hidden_features"
    const val GITHUB = "github"
    const val SAVE_LOG = "save_log"
}

@JvmField
var themeChanged = false
