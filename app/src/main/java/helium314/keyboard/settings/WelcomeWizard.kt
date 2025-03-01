// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun WelcomeWizard(
    close: () -> Unit,
    finish: () -> Unit
) {
    val ctx = LocalContext.current
    val width = LocalConfiguration.current.screenWidthDp
    val height = LocalConfiguration.current.screenHeightDp
    val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    fun determineStep(): Int = when {
        !UncachedInputMethodManagerUtils.isThisImeEnabled(ctx, imm) -> 0
        !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm) -> 2
        else -> 3
    }
    var step by rememberSaveable { mutableIntStateOf(determineStep()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(step) {
        if (step == 2)
            scope.launch {
                while (step == 2 && !UncachedInputMethodManagerUtils.isThisImeCurrent(ctx, imm)) {
                    delay(50)
                }
                step = 3
            }
    }
    val useWideLayout = height < 500 && width > height
    val appName = stringResource(ctx.applicationInfo.labelRes)
    @Composable fun bigText() {
        val resource = if (step == 0) R.string.setup_welcome_title else R.string.setup_steps_title
        Text(
            stringResource(resource, appName),
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 36.dp)
        )
    }
    @Composable fun steps() {
        if (step == 0)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.setup_welcome_image), null)
                Row(Modifier.clickable { step = 1 }
                    .padding(top = 4.dp, start = 4.dp, end = 4.dp)
                    .background(color = MaterialTheme.colorScheme.primary)
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.setup_start_action),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        else
            Column {
                val title: String
                val instruction: String
                val icon: Painter
                val actionText: String
                val action: () -> Unit
                val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    step = determineStep()
                }
                if (step == 1) {
                    title = stringResource(R.string.setup_step1_title, appName)
                    instruction = stringResource(R.string.setup_step1_instruction, appName)
                    icon = painterResource(R.drawable.ic_setup_key)
                    actionText = stringResource(R.string.setup_step1_action)
                    action = {
                        val intent = Intent()
                        intent.setAction(Settings.ACTION_INPUT_METHOD_SETTINGS)
                        intent.addCategory(Intent.CATEGORY_DEFAULT)
                        launcher.launch(intent)
                    }
                } else if (step == 2) {
                    title = stringResource(R.string.setup_step2_title, appName)
                    instruction = stringResource(R.string.setup_step2_instruction, appName)
                    icon = painterResource(R.drawable.ic_setup_select)
                    actionText = stringResource(R.string.setup_step2_action)
                    action = imm::showInputMethodPicker
                } else { // step 3
                    title = stringResource(R.string.setup_step3_title)
                    instruction = stringResource(R.string.setup_step3_instruction, appName)
                    icon = painterResource(R.drawable.sym_keyboard_language_switch)
                    actionText = stringResource(R.string.setup_step3_action)
                    action = close
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1", color = if (step == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary)
                    Text("2", color = if (step == 2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary)
                    Text("3", color = if (step == 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary)
                }
                Column(Modifier
                    .background(color = MaterialTheme.colorScheme.primary)
                    .padding(8.dp)
                ) {
                    Text(title)
                    Text(instruction, style = MaterialTheme.typography.bodyLarge.merge(color = MaterialTheme.colorScheme.onPrimary))
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.clickable { action() }
                        .background(color = MaterialTheme.colorScheme.primary)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, Modifier.padding(end = 6.dp).size(32.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    Text(actionText, Modifier.weight(1f))
                }
                if (step == 3) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        Modifier.clickable { finish() }
                            .background(color = MaterialTheme.colorScheme.primary)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_setup_check),
                            null,
                            Modifier.padding(end = 6.dp).size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(stringResource(R.string.setup_finish_action), Modifier.weight(1f))
                    }
                }
            }
    }
    Surface {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.primary,
            LocalTextStyle provides MaterialTheme.typography.titleLarge.merge(color = MaterialTheme.colorScheme.onPrimary),
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                if (useWideLayout)
                    Row {
                        Box(Modifier.weight(0.4f)) {
                            bigText()
                        }
                        Box(Modifier.weight(0.6f)) {
                            steps()
                        }
                    }
                else
                    Column {
                        bigText()
                        steps()
                    }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        Surface {
            WelcomeWizard({}) {  }
        }
    }
}

@Preview(
    // content cut off on real device, but not here... great?
    device = "spec:orientation=landscape,width=400dp,height=780dp"
)
@Composable
private fun WidePreview() {
    Theme(previewDark) {
        Surface {
            WelcomeWizard({}) {  }
        }
    }
}
