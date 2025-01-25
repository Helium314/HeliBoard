// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log

@Composable
fun TextCorrectionScreen(
    onClickBack: () -> Unit,
) {
    val prefs = DeviceProtectedUtils.getSharedPreferences(LocalContext.current)
    val act = LocalContext.current.getActivity() as? SettingsActivity2
    val b = act?.prefChanged!!.collectAsState()
    if (b.value < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val autocorrectEnabled = prefs.getBoolean(Settings.PREF_AUTO_CORRECTION, true)
    val personalizedSuggestionsEnabled = prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true)
    val suggestionsEnabled = prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true)
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_correction),
    ) {
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.EDIT_PERSONAL_DICTIONARY]!!.Preference()
        PreferenceCategory(stringResource(R.string.settings_category_correction)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_AUTO_CORRECTION]!!.Preference()
            AnimatedVisibility(visible = autocorrectEnabled, modifier = Modifier.fillMaxWidth()) {
                SettingsActivity2.allPrefs.map[Settings.PREF_MORE_AUTO_CORRECTION]!!.Preference()
            }
            AnimatedVisibility(visible = autocorrectEnabled, modifier = Modifier.fillMaxWidth()) {
                SettingsActivity2.allPrefs.map[Settings.PREF_AUTOCORRECT_SHORTCUTS]!!.Preference()
            }
            AnimatedVisibility(visible = autocorrectEnabled, modifier = Modifier.fillMaxWidth()) {
                SettingsActivity2.allPrefs.map[Settings.PREF_AUTO_CORRECTION_CONFIDENCE]!!.Preference()
            }
            SettingsActivity2.allPrefs.map[Settings.PREF_AUTO_CAP]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION]!!.Preference()
        }
        PreferenceCategory(stringResource(R.string.settings_category_suggestions)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_SHOW_SUGGESTIONS]!!.Preference()
            AnimatedVisibility(visible = suggestionsEnabled, modifier = Modifier.fillMaxWidth()) {
                SettingsActivity2.allPrefs.map[Settings.PREF_ALWAYS_SHOW_SUGGESTIONS]!!.Preference()
            }
            SettingsActivity2.allPrefs.map[Settings.PREF_KEY_USE_PERSONALIZED_DICTS]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_BIGRAM_PREDICTIONS]!!.Preference()
            AnimatedVisibility(visible = suggestionsEnabled, modifier = Modifier.fillMaxWidth()) {
                SettingsActivity2.allPrefs.map[Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER]!!.Preference()
            }
            SettingsActivity2.allPrefs.map[Settings.PREF_SUGGEST_CLIPBOARD_CONTENT]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_USE_CONTACTS]!!.Preference()
            AnimatedVisibility(visible = personalizedSuggestionsEnabled, modifier = Modifier.fillMaxWidth()) {
                SettingsActivity2.allPrefs.map[Settings.PREF_ADD_TO_PERSONAL_DICTIONARY]!!.Preference()
            }
        }
    }
}

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
