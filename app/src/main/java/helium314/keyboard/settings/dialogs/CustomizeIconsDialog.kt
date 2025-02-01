package helium314.keyboard.settings.dialogs

import android.graphics.drawable.VectorDrawable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.util.TypedValueCompat
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.customIconNames
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.screens.GetIcon
import kotlinx.serialization.json.Json

@Composable
fun CustomizeIconsDialog(
    prefKey: String,
    onDismissRequest: () -> Unit,
) {
    val state = rememberLazyListState()
    val ctx = LocalContext.current
    val iconsAndNames = KeyboardIconsSet.getAllIcons(ctx).keys.map { iconName ->
        val name = iconName.getStringResourceOrName("", ctx)
        if (name == iconName) iconName to iconName.getStringResourceOrName("label_", ctx)
        else iconName to name
    }.sortedBy { it.second }
    var showIconDialog: Pair<String, String>? by remember { mutableStateOf(null) }
    val prefs = ctx.prefs()
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = { }, // no confirm button
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(R.string.dialog_close)) } },
        title = { Text(stringResource(R.string.customize_icons)) },
        text = {
            LazyColumn(state = state) {
                items(iconsAndNames, key = { it.first }) { (iconName, displayName) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { showIconDialog = iconName to displayName }
                    ) {
                        KeyboardIconsSet.instance.GetIcon(iconName)
                        Text(displayName, Modifier.weight(1f))
                    }
                }
            }
        },
    )
    if (showIconDialog != null) {
        val iconName = showIconDialog!!.first
        val allIcons = KeyboardIconsSet.getAllIcons(ctx)
        val iconsForName = allIcons[iconName].orEmpty()
        val iconsSet = mutableSetOf<Int>()
        iconsSet.addAll(iconsForName)
        KeyboardIconsSet.getAllIcons(ctx).forEach { iconsSet.addAll(it.value) } // is this called again on UI interaction?
        val icons = iconsSet.toList()
        var selectedIcon by remember { mutableStateOf(KeyboardIconsSet.instance.iconIds[iconName]) }
        AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = { TextButton(
                onClick = {
                    onDismissRequest()
                    runCatching {
                        val newIcons = customIconNames(prefs).toMutableMap()
                        newIcons[iconName] = selectedIcon?.let { ctx.resources.getResourceEntryName(it) } ?: return@runCatching
                        prefs.edit().putString(prefKey, Json.encodeToString(newIcons)).apply()
                        KeyboardIconsSet.instance.loadIcons(ctx)
                    }
                    // todo: show outer dialog again and reload icons?
                }
            ) { Text(stringResource(android.R.string.ok)) } },
            dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) } },
            title = { Text(showIconDialog!!.second) },
            text = {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp)
                ) {
                    items(icons, key = { it }) { resId ->
                        val drawable = ContextCompat.getDrawable(ctx, resId)?.mutate() ?: return@items
                        val color = if (resId == selectedIcon) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        CompositionLocalProvider(
                            LocalContentColor provides color
                        ) {
                            Box(
                                Modifier.size(40.dp).clickable { selectedIcon = resId },
                                contentAlignment = Alignment.Center
                            ) {
                                if (drawable is VectorDrawable)
                                    Icon(painterResource(resId), null, Modifier.fillMaxSize(0.8f))
                                else {
                                    val px = TypedValueCompat.dpToPx(40f, ctx.resources.displayMetrics).toInt()
                                    Icon(drawable.toBitmap(px, px).asImageBitmap(), null, Modifier.fillMaxSize(0.8f))
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Preview
@Composable
private fun Preview() {
    KeyboardIconsSet.instance.loadIcons(LocalContext.current)
    CustomizeIconsDialog(
        prefKey = "",
        onDismissRequest = { },
    )
}
