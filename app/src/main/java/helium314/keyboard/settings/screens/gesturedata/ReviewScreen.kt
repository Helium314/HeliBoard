package helium314.keyboard.settings.screens.gesturedata

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.preferences.PreferenceCategory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onClickBack: () -> Unit,
) {
    val scrollState = rememberScrollState() // todo: maybe not scrollable, we'll have the scrollable list in there
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .verticalScroll(scrollState)
                .padding(horizontal = 12.dp)
                .then(Modifier.padding(innerPadding)),
        ) {
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
                            val menu = listOf("text" to { /* action */ })
                            menu.forEach {
                                DropdownMenuItem(
                                    text = { Text(it.first) },
                                    onClick = { showMenu = false; it.second() }
                                )
                            }
                        }
                    }
                }
            )
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
                name = "share gesture data",
                onClick = { showExportDialog = true },
                description = "any subtitle / description?"
            )

            PreferenceCategory("available data")
            // delete-all-exported button? or rather menu?
            // filter (careful, controls should not be too large, also consider landscape orientation)
            //  active switch
            //  passive switch
            var includeActive by remember { mutableStateOf(true) }
            var includePassive by remember { mutableStateOf(true) }
            Row {
                Preference(
                    name = "active",
                    description = "show data from active gathering",
                    onClick = { includeActive = !includeActive },
                    value = { Switch(checked = includeActive, onCheckedChange = { includeActive = it }) }
                )
                Preference(
                    name = "passive",
                    description = "show data from passive gathering",
                    onClick = { includePassive = !includePassive },
                    value = { Switch(checked = includePassive, onCheckedChange = { includePassive = it }) }
                )
            }
            //  include-already-exported switch
            // todo: can we have 3 switches in a row, reasonably? maybe with a relatively short text below and a separator...
            var includeExported by remember { mutableStateOf(false) }
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
                description = "from / to"
            )
            if (showDateRangePicker)
                DateRangePickerModal({ startDate = it.first; endDate = it.second }) { showDateRangePicker = false }
            //  word text field (allow regex?)
            //  user-id text field
            //  and finally the result list
            //   user-define sorting?
            //   have select-all thing (... menu?)
            //    yes, we need that menu, also for mass-actions
            val filteredData = listOf<WordData>()
            LazyColumn {
                items(filteredData, { it.hashCode() }) { // todo: need the code, but the sha256(?) one
                    //   each entry consists of word, time, user-id, active/passive, whether it's already exported
                    //    click shows raw data?
                    //     and allows delete or remove user-id
                    //    long click selects
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
