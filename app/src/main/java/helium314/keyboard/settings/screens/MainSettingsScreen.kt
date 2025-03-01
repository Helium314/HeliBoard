// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.AboutFragment
import helium314.keyboard.latin.settings.AdvancedSettingsFragment
import helium314.keyboard.latin.settings.AppearanceSettingsFragment
import helium314.keyboard.latin.settings.CorrectionSettingsFragment
import helium314.keyboard.latin.settings.GestureSettingsFragment
import helium314.keyboard.latin.settings.LanguageSettingsFragment
import helium314.keyboard.latin.settings.PreferencesSettingsFragment
import helium314.keyboard.latin.settings.ToolbarSettingsFragment
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.displayName
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.switchTo
import helium314.keyboard.settings.NextScreenIcon
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.previewDark

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickPreferences: () -> Unit,
    onClickToolbar: () -> Unit,
    onClickGestureTyping: () -> Unit,
    onClickAdvanced: () -> Unit,
    onClickAppearance: () -> Unit,
    onClickLanguage: () -> Unit,
    onClickLayouts: () -> Unit,
    onClickDictionaries: () -> Unit,
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
        settings = emptyList(),
    ) {
        val enabledSubtypes = SubtypeSettings.getEnabledSubtypes(true)
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Preference(
                name = stringResource(R.string.language_and_layouts_title),
                description = enabledSubtypes.joinToString(", ") { it.displayName(ctx) },
                onClick = onClickLanguage,
                icon = R.drawable.ic_settings_languages_foreground
            ) { NextScreenIcon() }
            Preference(
                name = stringResource(R.string.settings_screen_preferences),
                onClick = onClickPreferences,
                icon = R.drawable.ic_settings_preferences_foreground
            ) { NextScreenIcon() }
            Preference(
                name = stringResource(R.string.settings_screen_appearance),
                onClick = onClickAppearance,
                icon = R.drawable.ic_settings_appearance_foreground
            ) { NextScreenIcon() }
            Preference(
                name = stringResource(R.string.settings_screen_toolbar),
                onClick = onClickToolbar,
                icon = R.drawable.ic_settings_toolbar_foreground
            ) { NextScreenIcon() }
            if (JniUtils.sHaveGestureLib)
                Preference(
                    name = stringResource(R.string.settings_screen_gesture),
                    onClick = onClickGestureTyping,
                    icon = R.drawable.ic_settings_gesture_foreground
                ) { NextScreenIcon() }
            Preference(
                name = stringResource(R.string.settings_screen_correction),
                onClick = onClickTextCorrection,
                icon = R.drawable.ic_settings_correction_foreground
            ) { NextScreenIcon() }
            Preference(
                name = stringResource(R.string.settings_screen_secondary_layouts),
                onClick = onClickLayouts,
                icon = R.drawable.ic_ime_switcher
            ) { NextScreenIcon() }
            Preference(
                name = stringResource(R.string.dictionary_settings_category),
                onClick = onClickDictionaries,
                icon = R.drawable.ic_dictionary
            ) { NextScreenIcon() }
            Preference(
                name = stringResource(R.string.settings_screen_advanced),
                onClick = onClickAdvanced,
                icon = R.drawable.ic_settings_advanced_foreground
            ) { NextScreenIcon() }
            Preference(
                name = stringResource(R.string.settings_screen_about),
                onClick = onClickAbout,
                icon = R.drawable.ic_settings_about_foreground
            ) { NextScreenIcon() }
            PreferenceCategory(title = "old screens")
            Preference(
                name = stringResource(R.string.language_and_layouts_title),
                onClick = { ctx.getActivity()?.switchTo(LanguageSettingsFragment()) }
            )
            Preference(
                name = stringResource(R.string.settings_screen_preferences),
                onClick = { ctx.getActivity()?.switchTo(PreferencesSettingsFragment()) }
            )
            Preference(
                name = stringResource(R.string.settings_screen_appearance),
                onClick = { ctx.getActivity()?.switchTo(AppearanceSettingsFragment()) }
            )
            Preference(
                name = stringResource(R.string.settings_screen_toolbar),
                onClick = { ctx.getActivity()?.switchTo(ToolbarSettingsFragment()) }
            )
            if (JniUtils.sHaveGestureLib)
                Preference(
                    name = stringResource(R.string.settings_screen_gesture),
                    onClick = { ctx.getActivity()?.switchTo(GestureSettingsFragment()) }
                )
            Preference(
                name = stringResource(R.string.settings_screen_correction),
                onClick = { ctx.getActivity()?.switchTo(CorrectionSettingsFragment()) }
            )
            Preference(
                name = stringResource(R.string.settings_screen_advanced),
                onClick = { ctx.getActivity()?.switchTo(AdvancedSettingsFragment()) }
            )
            Preference(
                name = stringResource(R.string.settings_screen_about),
                onClick = { ctx.getActivity()?.switchTo(AboutFragment()) }
            )
        }
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            MainSettingsScreen({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
    }
}
