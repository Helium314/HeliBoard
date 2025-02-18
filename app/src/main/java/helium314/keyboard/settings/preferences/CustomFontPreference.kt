// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.keyboardNeedsReload
import java.io.File

@Composable
fun CustomFontPreference(setting: Setting) {
    val ctx = LocalContext.current
    var showDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    val fontFile = Settings.getCustomFontFile(ctx)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = it.data?.data ?: return@rememberLauncherForActivityResult
        val tempFile = File(DeviceProtectedUtils.getFilesDir(ctx), "temp_file")
        FileUtils.copyContentUriToNewFile(uri, ctx, tempFile)
        try {
            Typeface.createFromFile(tempFile)
            fontFile.delete()
            tempFile.renameTo(fontFile)
            Settings.clearCachedTypeface()
            keyboardNeedsReload = true
        } catch (_: Exception) {
            showErrorDialog = true
            tempFile.delete()
        }
    }
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*")
    Preference(
        name = setting.title,
        onClick = {
            if (fontFile.exists())
                showDialog = true
            else launcher.launch(intent)
        },
    )
    if (showDialog)
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            onConfirmed = { launcher.launch(intent) },
            onNeutral = {
                showDialog = false
                fontFile.delete()
                Settings.clearCachedTypeface()
                keyboardNeedsReload = true
            },
            neutralButtonText = stringResource(R.string.delete),
            confirmButtonText = stringResource(R.string.load),
            title = { Text(stringResource(R.string.custom_font)) }
        )
    if (showErrorDialog)
        InfoDialog(stringResource(R.string.file_read_error)) { showErrorDialog = false }
}
