package helium314.keyboard.settings.dialogs

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.latin.R
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun <T: Any> ReorderDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: (List<T>) -> Unit,
    items: List<T>,
    getKey: (T) -> Any, // actually it's not "Any", but "anything that can be stored in a bundle"
    displayItem: @Composable (T) -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    confirmButtonText: String = stringResource(android.R.string.ok),
    cancelButtonText: String = stringResource(android.R.string.cancel),
    shape: Shape = MaterialTheme.shapes.medium,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = contentColorFor(backgroundColor),
    properties: DialogProperties = DialogProperties(),
) {
    var reorderableItems by remember(items) { mutableStateOf(items) }
    val listState = rememberLazyListState()

    val dragDropState = rememberReorderableLazyListState(listState) { from, to ->
        reorderableItems = reorderableItems.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(onClick = { onConfirmed(reorderableItems); onDismissRequest() }) { Text(confirmButtonText) }
        },
        modifier = modifier,
        dismissButton = { TextButton(onClick = onDismissRequest) { Text(cancelButtonText) } },
        title = title,
        text = {
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(reorderableItems, key = getKey) { item ->
                    ReorderableItem(
                        state = dragDropState,
                        key = getKey(item)
                    ) { dragging ->
                        val elevation by animateDpAsState(if (dragging) 4.dp else 0.dp)
                        Surface(shadowElevation = elevation) {
                            Row(modifier = Modifier.longPressDraggableHandle(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painterResource(R.drawable.ic_drag_indicator),
                                    "Reorder",
                                    Modifier.padding(end = 8.dp),
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                )
                               displayItem(item)
                            }
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
private fun Preview() {
    ReorderDialog(
        onConfirmed = {},
        onDismissRequest = {},
        items = listOf(1, 2, 3),
        displayItem = { Text(it.toString(), Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        getKey = { it.toString() }
    )
}
