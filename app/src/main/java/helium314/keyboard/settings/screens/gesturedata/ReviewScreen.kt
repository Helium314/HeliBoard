package helium314.keyboard.settings.screens.gesturedata

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.previewDark
import kotlinx.serialization.json.Json
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onClickBack: () -> Unit,
) {
//    val scrollState = rememberScrollState() // todo: maybe not scrollable, we'll have the scrollable list in there
    val buttonColors = ButtonColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = MaterialTheme.colorScheme.surfaceVariant
    )
    var showIgnoreListDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<Int>()) } // index? hashCode?
    var filter by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        bottomBar = {
            BottomAppBar( // todo: hope it doesn't block anything
                actions = {
                    // todo: spread evenly, consistent icon size, enable / disable if necessary
                    Button({}, colors = buttonColors) {
                        Column {
                            Icon(painterResource(R.drawable.ic_bin_rounded), "delete", Modifier.align(Alignment.CenterHorizontally))
                            Text("delete")
                        }
                    }
                    // share data (all, filtered, selected, non-exported)
                    //  filter again against blacklist, even though blacklisted words shouldn't make it in the data anyway
                    //  mark entries as exported
                    Button({ showExportDialog = true }, colors = buttonColors) {
                        Column {
                            Icon(painterResource(R.drawable.sym_keyboard_language_switch), "share", Modifier.align(Alignment.CenterHorizontally)) // share icon
                            Text(if (selected.isNotEmpty()) "share selected"
                            else if (filter.text.isNotEmpty()) "share filtered"
                            else "share all")
                        }
                    }
                    // blacklist button (show number of entries)
                    //  dialog, shows all entries, allows adding, changing and removing
                    //  mention it's case- and diacritics insensitive
                    //  update list on ok
                    Button({ showIgnoreListDialog = true }, colors = buttonColors) {
                        Column {
                            Icon(painterResource(R.drawable.ic_autocorrect), "exclude", Modifier.align(Alignment.CenterHorizontally)) // block icon (strike through)
                            Text("exclude")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
//                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp)
                .then(Modifier.padding(innerPadding)),
        ) {
            var includeActive by rememberSaveable { mutableStateOf(true) }
            var includePassive by rememberSaveable { mutableStateOf(true) }
            var includeExported by rememberSaveable { mutableStateOf(false) }
            var gestureData by remember { mutableStateOf(listOf<GestureData>()) }
            val ctx = LocalContext.current
            LaunchedEffect(filter) {
                gestureData = Json.decodeFromString("["+getGestureDataFile(ctx).readText().dropLast(2)+"]")
            }
            val filteredData = if (includeActive && includePassive && !includeExported && filter.text.isBlank()) gestureData
                else gestureData.filter {
                    // todo: active
                    // todo: passive
                    // todo: exported
                    it.targetWord.contains(filter.text, true)
                }
            TopAppBar(
                title = { Text("Review & export gesture data") },
                navigationIcon = {
                    IconButton(onClick = onClickBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.spoken_description_action_previous)
                        )
                    }
                },
                actions = {
                    Box {
                        var showMenu by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showMenu = true }
                        ) { Icon(painterResource(R.drawable.ic_arrow_left), "menu", Modifier.rotate(-90f)) }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("delete all exported") },
                                onClick = { showMenu = false; /* todo, confirmation dialog */ }
                            )
                            DropdownMenuItem(
                                text = { Text("delete all selected") },
                                onClick = { showMenu = false; /* todo, confirmation dialog */ }
                            )
                            DropdownMenuItem(
                                text = { Text("sort chronologically") },
                                onClick = { showMenu = false; /* todo */ }
                            )
                            DropdownMenuItem(
                                text = { Text("sort alphabetically") },
                                onClick = { showMenu = false; /* todo */ }
                            )
                            // and i guess the reverse sort order
                        }
                    }
                }
            )
            // filter (careful, controls should not be too large, also consider landscape orientation)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) { // not spaced evenly due to text length...
                Button({ includeActive = !includeActive }, colors = buttonColors) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = includeActive, onCheckedChange = { includeActive = it })
                        Text("active")
                    }
                }
                Button({ includePassive = !includePassive }, colors = buttonColors) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = includePassive, onCheckedChange = { includePassive = it })
                        Text("passive")
                    }
                }
                Button({ includeExported = !includeExported }, colors = buttonColors) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Switch(checked = includeExported, onCheckedChange = { includeExported = it })
                        Text("previously exported")
                    }
                }
            }
            var showDateRangePicker by remember { mutableStateOf(false) }
            var startDate: Long? by rememberSaveable { mutableStateOf(null) }
            var endDate: Long? by rememberSaveable { mutableStateOf(null) }
            val df = DateFormat.getDateFormat(ctx)
            Row(verticalAlignment = Alignment.CenterVertically) {
                //  word text field (allow regex?)
                // todo: supportingText makes it look misaligned...
                TextField(value = filter, onValueChange = { filter = it}, supportingText = { Text("filter") }, modifier = Modifier.weight(0.7f))
                //  time from-to button (set time range in dialog)
                Column(Modifier.clickable { showDateRangePicker = true }.weight(0.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("date", style = MaterialTheme.typography.bodyLarge)
                    Text(startDate?.let { df.format(Date(it)) }.toString(), style = MaterialTheme.typography.bodyMedium)
                    Text(endDate?.let { df.format(Date(it)) }.toString(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (showDateRangePicker)
                DateRangePickerModal({ startDate = it.first; endDate = it.second }) { showDateRangePicker = false }
            //  user-id text field (todo: get rid of user-id?)
            //  and finally the result list
            //   user-define sorting? -> menu
            //   have select-all thing (... menu?)
            //    yes, we need that menu, also for mass-actions
            // once sth is selected -> show new buttons to delete and export?
            //  though for export the export button text could change to "export selected"
            //  if filter changes -> unselect all

            LazyColumn {
                items(filteredData, { it.hashCode() }) { item -> // todo: need the code, but the sha256(?) one
                    //   each entry consists of word, time, user-id, active/passive, whether it's already exported
                    //    click shows raw data?
                    //     and allows delete or remove user-id
                    //    long click selects
                    GestureDataEntry(item, item.hashCode() in selected, selected.isNotEmpty()) { sel ->
                        selected = if (!sel) selected.filterNot { it == item.hashCode() }
                        else selected + item.hashCode()
                    }
                }
            }
        }
    }
}

@Composable
private fun GestureDataEntry(gestureData: GestureData, selected: Boolean, anythingSelected: Boolean, onSelect: (Boolean) -> Unit) {
    val startModifier = if (!anythingSelected) Modifier.combinedClickable(
        onClick = { },
        onLongClick = { onSelect(true) },
    )
    else Modifier.selectable(
        selected = selected,
        onClick = { onSelect(!selected) },
        // how to use?
        //interactionSource = remember { MutableInteractionSource() },
        //indication = ripple()
    )
    Text(
        text = gestureData.targetWord,
        modifier = startModifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    )
}

// copied from https://developer.android.com/develop/ui/compose/components/datepickers
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerModal(
    onDateRangeSelected: (Pair<Long?, Long?>) -> Unit,
    onDismiss: () -> Unit
) {
    val dateRangePickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateRangeSelected(
                        Pair(
                            dateRangePickerState.selectedStartDateMillis,
                            dateRangePickerState.selectedEndDateMillis
                        )
                    )
                    onDismiss()
                }
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select date range"
                )
            },
            showModeToggle = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(16.dp)
        )
    }
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        ReviewScreen { }
    }
}
