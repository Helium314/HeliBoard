package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

// taken from StreetComplete
@Composable
fun <T: Any> ListPickerDialog(
    onDismissRequest: () -> Unit,
    items: List<T>,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    selectedItem: T? = null,
    getItemName: (@Composable (T) -> String) = { it.toString() },
    width: Dp? = null,
    height: Dp? = null,
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    properties: DialogProperties = DialogProperties()
) {
    var selected by remember { mutableStateOf(selectedItem) }
    val state = rememberLazyListState()

    LaunchedEffect(selectedItem) {
        val index = items.indexOf(selectedItem)
        if (index != -1) state.scrollToItem(index, -state.layoutInfo.viewportSize.height / 3)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = { onDismissRequest(); selected?.let { onItemSelected(it) } },
                enabled = selected != null,
            ) { Text(stringResource(android.R.string.ok)) }
        },
        modifier = modifier,
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(stringResource(android.R.string.cancel)) } },
        title = title,
        text = {
            CompositionLocalProvider(
                LocalTextStyle provides MaterialTheme.typography.bodyLarge
            ) {
                LazyColumn(state = state) {
                    items(items) { item ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { selected = item }
                                .padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = getItemName(item),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            RadioButton(
                                selected = selected == item,
                                onClick = { selected = item }
                            )
                        }
                    }
                }
            }
        },
        shape = shape,
        containerColor = backgroundColor,
        textContentColor = contentColor,
        properties = properties,
    )
}

@Preview
@Composable
private fun PreviewListPickerDialog() {
    val items = remember { (0..<5).toList() }
    ListPickerDialog(
        onDismissRequest = {},
        items = items,
        onItemSelected = {},
        title = { Text("Select something") },
        selectedItem = 2,
        getItemName = { "Item $it" },
        width = 260.dp
    )
}
