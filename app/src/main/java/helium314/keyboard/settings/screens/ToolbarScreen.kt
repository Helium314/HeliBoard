package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ReorderDialog

@Composable
fun ToolbarScreen(
    onClickBack: () -> Unit,
) {
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_toolbar),
    ) {
        SettingsActivity2.allPrefs.map[Settings.PREF_PINNED_TOOLBAR_KEYS]!!.Preference()
    }
}

fun createToolbarPrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_PINNED_TOOLBAR_KEYS, R.string.pinned_toolbar_keys) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true },
        )
        if (showDialog) {
            ReorderDialog(
                onConfirmed = {  },
                onDismissRequest = { showDialog = false },
                items = (1..40).toList(),
                displayItem = { Text(it.toString(), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                getKey = { it.hashCode() }
            )
        }
    },
)

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            ToolbarScreen { }
        }
    }
}
