package helium314.keyboard.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R


@Composable
fun WithSmallTitle(
    description: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(description, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
fun <T>DropDownField(
    items: List<T>,
    selectedItem: T,
    onSelected: (T) -> Unit,
    extraButton: @Composable (() -> Unit)? = null,
    itemContent: @Composable (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        Modifier.clickable { expanded = !expanded }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
        ) {
            Box(Modifier.weight(1f)) {
                itemContent(selectedItem)
            }
            IconButton(
                onClick = { expanded = !expanded },
                enabled = items.size > 1
            ) {
                Icon(
                    painterResource(R.drawable.ic_arrow_left),
                    "show dropdown",
                    Modifier.rotate(-90f)
                )
            }
            if (extraButton != null)
                extraButton()
        }
    }
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        items.forEach {
            DropdownMenuItem(
                text = { itemContent(it) },
                onClick = { expanded = false; onSelected(it) }
            )
        }
    }
}
