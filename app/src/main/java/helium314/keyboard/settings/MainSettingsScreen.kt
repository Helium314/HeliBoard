// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.app.Activity
import android.view.View
import android.widget.RelativeLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.fragment.app.commit
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

@Composable
fun MainSettingsScreen(
    onClickAbout: () -> Unit,
    onClickTextCorrection: () -> Unit,
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.ime_settings),
    ) {
        Preference(
            name = stringResource(R.string.settings_screen_correction),
            onClick = onClickTextCorrection,
            icon = R.drawable.ic_settings_correction_foreground
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                modifier = Modifier.scale(-1f, 1f), // no rotate drawable allowed in compose
                contentDescription = null
            )
        }
        Preference(
            name = stringResource(R.string.settings_screen_about),
            onClick = onClickAbout,
            icon = R.drawable.ic_settings_about_foreground
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                modifier = Modifier.scale(-1f, 1f),
                contentDescription = null
            )
        }
        PreferenceCategory(
            title = "old screens"
        ) {
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

fun Activity.switchTo(fragment: androidx.fragment.app.Fragment) {
    (this as AppCompatActivity).supportFragmentManager.commit {
        findViewById<RelativeLayout>(R.id.settingsFragmentContainer).visibility = View.VISIBLE
        replace(R.id.settingsFragmentContainer, fragment)
        addToBackStack(null)
    }
}

@Preview
@Composable
private fun PreviewScreen() {
    Theme(true) {
        Surface {
            MainSettingsScreen({}, {}, {})
        }
    }
}
