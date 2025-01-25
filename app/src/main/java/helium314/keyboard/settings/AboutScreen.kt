// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R

@Composable
fun AboutScreen(
    onClickBack: () -> Unit,
) {
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_about),
    ) {
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.APP]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.VERSION]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.LICENSE]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.HIDDEN_FEATURES]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.GITHUB]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.SAVE_LOG]!!.Preference()
    }
}

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            AboutScreen {  }
        }
    }
}
