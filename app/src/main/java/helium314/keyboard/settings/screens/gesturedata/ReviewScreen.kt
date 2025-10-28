package helium314.keyboard.settings.screens.gesturedata

import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory
import helium314.keyboard.settings.previewDark
import kotlinx.serialization.json.Json
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class)
@Composable
fun ReviewScreen(
    onClickBack: () -> Unit,
) {
//    val scrollState = rememberScrollState() // todo: maybe not scrollable, we'll have the scrollable list in there
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
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
            var selected by rememberSaveable { mutableStateOf(listOf<Int>()) } // index? hashCode?
            var filter by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
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
                                text = { Text("sort alphabltically") },
                                onClick = { showMenu = false; /* todo */ }
                            )
                            // and i guess the reverse sort order
                        }
                    }
                }
            )
            // excluded words and share could be in a bottom bar, with icons and a 1-2 word description

            // blacklist button (show number of entries)
            //  dialog, shows all entries, allows adding, changing and removing
            //  mention it's case- and diacritics insensitive
            //  update list on ok
            var showIgnoreListDialog by remember { mutableStateOf(false) }
            Preference(
                name = "excluded words",
                onClick = { showIgnoreListDialog = true },
                description = "these words will be ignored"
            )

            // share data (all, filtered, selected, non-exported)
            //  filter again against blacklist, even though blacklisted words shouldn't make it in the data anyway
            //  mark entries as exported
            var showExportDialog by remember { mutableStateOf(false) }
            Preference(
                name = if (selected.isNotEmpty()) "share selected data"
                    else if (filter.text.isNotEmpty()) "share filtered data"
                    else "share data",
                onClick = { showExportDialog = true },
                description = "any subtitle / description?"
            )

            PreferenceCategory("available data")
            // filter (careful, controls should not be too large, also consider landscape orientation)
            Row(Modifier.fillMaxWidth()) { // try 2 switches in a row, for saving vertical space
                Preference(
                    name = "active",
                    description = "show data from active gathering",
                    modifier = Modifier.weight(0.5f),
                    onClick = { includeActive = !includeActive },
                    value = { Switch(checked = includeActive, onCheckedChange = { includeActive = it }) }
                )
                Preference(
                    name = "passive",
                    description = "show data from passive gathering",
                    modifier = Modifier.weight(0.5f),
                    onClick = { includePassive = !includePassive },
                    value = { Switch(checked = includePassive, onCheckedChange = { includePassive = it }) }
                )
            }
            // todo: can we have 3 switches in a row, reasonably? maybe with a relatively short text below and a separator...
            Preference(
                name = "show already exported data",
                onClick = { includeExported = !includeExported },
                value = { Switch(checked = includeExported, onCheckedChange = { includeExported = it }) }
            )
            //  time from-to button (set time range in dialog)
            var showDateRangePicker by remember { mutableStateOf(false) }
            var startDate: Long? by rememberSaveable { mutableStateOf(null) }
            var endDate: Long? by rememberSaveable { mutableStateOf(null) }
            Preference(
                name = "filter by date",
                onClick = { showDateRangePicker = true },
                description = "from ${startDate?.let { Instant.fromEpochMilliseconds(it)} } / to ${endDate?.let { Instant.fromEpochMilliseconds(it)} }"
            )
            if (showDateRangePicker)
                DateRangePickerModal({ startDate = it.first; endDate = it.second }) { showDateRangePicker = false }
            //  word text field (allow regex?)
            TextField(value = filter, onValueChange = { filter = it}, supportingText = { Text("filter") })
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
                    // todo: does this work at all?
                    val startModifier = if (selected.isEmpty()) Modifier.combinedClickable(
                        onClick = { Log.i("test", "click") },
                        onLongClick = { Log.i("test", "long click"); selected = selected + item.hashCode() },
                    )
                    else Modifier.selectable(
                        selected = item.hashCode() in selected,
                        onClick = {
                            Log.i("test", "select")
                            // todo: this is inefficient, will be horrible for long lists
                            if (item.hashCode() in selected) selected = selected.filterNot { it == item.hashCode() }
                            else selected = selected + item.hashCode()
                        },
                        // how to use?
//                        interactionSource = remember { MutableInteractionSource() },
//                        indication = ripple()
                    )
                    Text(
                        text = item.targetWord,
                        modifier = startModifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 12.dp),
                    )
                }
            }
        }
    }
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
