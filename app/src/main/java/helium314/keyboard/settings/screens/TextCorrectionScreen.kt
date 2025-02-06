// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.permissions.PermissionsUtil
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.UserDictionaryListFragment
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.switchTo
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.ListPreference
import helium314.keyboard.settings.NonSettingsPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.keyboardNeedsReload

@Composable
fun TextCorrectionScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val autocorrectEnabled = prefs.getBoolean(Settings.PREF_AUTO_CORRECTION, true)
    val suggestionsEnabled = prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
    val items = listOfNotNull(
        NonSettingsPrefs.EDIT_PERSONAL_DICTIONARY,
        R.string.settings_category_correction,
        Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
        Settings.PREF_AUTO_CORRECTION,
        if (autocorrectEnabled) Settings.PREF_MORE_AUTO_CORRECTION else null,
        if (autocorrectEnabled) Settings.PREF_AUTOCORRECT_SHORTCUTS else null,
        if (autocorrectEnabled) Settings.PREF_AUTO_CORRECTION_CONFIDENCE else null,
        Settings.PREF_AUTO_CAP,
        Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
        Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION,
        R.string.settings_category_suggestions,
        Settings.PREF_SHOW_SUGGESTIONS,
        if (suggestionsEnabled) Settings.PREF_ALWAYS_SHOW_SUGGESTIONS else null,
        if (suggestionsEnabled) Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER else null,
        Settings.PREF_KEY_USE_PERSONALIZED_DICTS,
        Settings.PREF_BIGRAM_PREDICTIONS,
        Settings.PREF_SUGGEST_CLIPBOARD_CONTENT,
        Settings.PREF_USE_CONTACTS,
        if (prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true))
            Settings.PREF_ADD_TO_PERSONAL_DICTIONARY else null
    )
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_correction),
        prefs = items
    )
}

fun createCorrectionPrefs(context: Context) = listOf(
    PrefDef(context, NonSettingsPrefs.EDIT_PERSONAL_DICTIONARY, R.string.edit_personal_dictionary) {
        val ctx = LocalContext.current
        Preference(
            name = stringResource(R.string.edit_personal_dictionary),
            onClick = { ctx.getActivity()?.switchTo(UserDictionaryListFragment()) },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                modifier = Modifier.scale(-1f, 1f),
                contentDescription = null
            )
        }
    },
    PrefDef(context, Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE,
        R.string.prefs_block_potentially_offensive_title, R.string.prefs_block_potentially_offensive_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_AUTO_CORRECTION,
        R.string.autocorrect, R.string.auto_correction_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_MORE_AUTO_CORRECTION,
        R.string.more_autocorrect, R.string.more_autocorrect_summary
    ) {
        SwitchPreference(it, true) // todo (later): shouldn't it better be false?
    },
    PrefDef(context, Settings.PREF_AUTOCORRECT_SHORTCUTS,
        R.string.auto_correct_shortcuts, R.string.auto_correct_shortcuts_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_AUTO_CORRECTION_CONFIDENCE, R.string.auto_correction_confidence) {
        val items = listOf(
            stringResource(R.string.auto_correction_threshold_mode_modest) to "0",
            stringResource(R.string.auto_correction_threshold_mode_aggressive) to "1",
            stringResource(R.string.auto_correction_threshold_mode_very_aggressive) to "2",
        )
        ListPreference(it, items, "0")
    },
    PrefDef(context, Settings.PREF_AUTO_CAP,
        R.string.auto_cap, R.string.auto_cap_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD,
        R.string.use_double_space_period, R.string.use_double_space_period_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION,
        R.string.autospace_after_punctuation, R.string.autospace_after_punctuation_summary
    ) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_SHOW_SUGGESTIONS,
        R.string.prefs_show_suggestions, R.string.prefs_show_suggestions_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_ALWAYS_SHOW_SUGGESTIONS,
        R.string.prefs_always_show_suggestions, R.string.prefs_always_show_suggestions_summary
    ) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_KEY_USE_PERSONALIZED_DICTS,
        R.string.use_personalized_dicts, R.string.use_personalized_dicts_summary
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
            val prefs = LocalContext.current.prefs()
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = {
                    prefs.edit().putBoolean(prefDef.key, false).apply()
                },
                text = { Text(stringResource(R.string.disable_personalized_dicts_message)) }
            )
        }

    },
    PrefDef(context, Settings.PREF_BIGRAM_PREDICTIONS,
        R.string.bigram_prediction, R.string.bigram_prediction_summary
    ) {
        SwitchPreference(it, true) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER,
        R.string.center_suggestion_text_to_enter, R.string.center_suggestion_text_to_enter_summary
    ) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_SUGGEST_CLIPBOARD_CONTENT,
        R.string.suggest_clipboard_content, R.string.suggest_clipboard_content_summary
    ) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_USE_CONTACTS,
        R.string.use_contacts_dict, R.string.use_contacts_dict_summary
    ) { def ->
        val activity = LocalContext.current.getActivity() ?: return@PrefDef
        var granted by remember { mutableStateOf(PermissionsUtil.checkAllPermissionsGranted(activity, Manifest.permission.READ_CONTACTS)) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            granted = it
            if (granted)
                activity.prefs().edit().putBoolean(def.key, true).apply()
        }
        SwitchPreference(
            def,
            false,
            allowCheckedChange = {
                if (it && !granted) {
                    launcher.launch(Manifest.permission.READ_CONTACTS)
                    false
                } else true
            }
        )
    },
    PrefDef(context, Settings.PREF_ADD_TO_PERSONAL_DICTIONARY,
        R.string.add_to_personal_dictionary, R.string.add_to_personal_dictionary_summary
    ) {
        SwitchPreference(it, false)
    },
)

@Preview
@Composable
private fun PreferencePreview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            TextCorrectionScreen {  }
        }
    }
}
