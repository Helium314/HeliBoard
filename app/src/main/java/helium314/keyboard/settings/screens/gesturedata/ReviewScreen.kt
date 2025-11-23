// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens.gesturedata

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.GestureDataDao
import helium314.keyboard.latin.utils.GestureDataInfo
import helium314.keyboard.latin.utils.getWordIgnoreList
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.isWideScreen
import helium314.keyboard.settings.previewDark
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// not actually on use, unfinished
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val dao = GestureDataDao.getInstance(ctx)!!
    val buttonColors = ButtonColors(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        disabledContainerColor = Color.Transparent,
        disabledContentColor = MaterialTheme.colorScheme.surfaceVariant
    )
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var filter by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var gestureDataInfos by remember { mutableStateOf(listOf<GestureDataInfo>()) }
    val wordcount = if (selected.isNotEmpty()) selected.size else gestureDataInfos.size
    val useWideLayout = isWideScreen()
    var sortByName: Boolean by rememberSaveable { mutableStateOf(false) }
    var reverseSort: Boolean by rememberSaveable { mutableStateOf(false) }
    // todo: show "long-press to select" hint somewhere
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        bottomBar = {
            BottomAppBar(
                actions = {
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            { showDeleteDialog = true},
                            colors = buttonColors,
                            modifier = Modifier.weight(1f),
                            enabled = wordcount > 0
                        ) {
                            Column {
                                Icon(
                                    Icons.Default.Delete,
                                    stringResource(R.string.delete),
                                    Modifier.align(Alignment.CenterHorizontally).size(30.dp)
                                )
                                Text(stringResource(R.string.gesture_data_words_selected, wordcount))
                            }
                        }
                        // todo: extra delete for exported, if there are any?
                        Button(
                            { showExportDialog = true},
                            colors = buttonColors,
                            modifier = Modifier.weight(1f),
                            enabled = wordcount > 0
                        ) {
                            Column {
                                Icon(
                                    Icons.Default.Share,
                                    "share",
                                    Modifier.align(Alignment.CenterHorizontally).size(30.dp)
                                )
                                Text(stringResource(R.string.gesture_data_words_selected, wordcount))
                            }
                        }
                        IconButton(
                            onClick = {
                                if (sortByName) reverseSort = !reverseSort
                                else sortByName = true
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_sort_alphabetically),
                                "sort alphabetically"
                            )
                        }
                        IconButton(
                            onClick = {
                                if (!sortByName) reverseSort = !reverseSort
                                else sortByName = false
                            }
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_sort_chronologically),
                                "sort chronologically"
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        @Composable fun dataColumn() {
            val wordListState = LazyListState()
            val scope = rememberCoroutineScope()
            LaunchedEffect(reverseSort, sortByName) {
                scope.launch {
                    if (gestureDataInfos.isNotEmpty())
                        wordListState.scrollToItem(0)
                }
            }
            LazyColumn(state = wordListState) {
                items(gestureDataInfos, { it.id }) { item ->
                    //   each entry consists of word, time, active/passive, whether it's already exported
                    //    click shows raw data?
                    //    long click selects
                    GestureDataEntry(item, item.id in selected, selected.isNotEmpty()) { sel ->
                        selected = if (!sel) selected.filterNot { it == item.id }
                        else selected + item.id
                    }
                }
            }
        }
        @Composable fun controlColumn() {
            Column(Modifier.padding(horizontal = 12.dp)) {
                var includeActive by rememberSaveable { mutableStateOf(true) }
                var includePassive by rememberSaveable { mutableStateOf(true) }
                var includeExported by rememberSaveable { mutableStateOf(false) }
                var startDate: Long? by rememberSaveable { mutableStateOf(null) }
                var endDate: Long? by rememberSaveable { mutableStateOf(null) }
                fun sortWords() {
                    gestureDataInfos = if (sortByName) {
                        gestureDataInfos.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.targetWord })
                    } else {
                        gestureDataInfos.sortedBy { it.timestamp }
                    }
                    if (reverseSort)
                        gestureDataInfos = gestureDataInfos.reversed()
                }
                LaunchedEffect(filter, startDate, endDate, includeExported, reverseSort, includeActive, includePassive) {
                    // todo: if slow, do in background or try returning a cursor (then sorting needs to be done by db)
                    gestureDataInfos = dao.filterInfos(
                        filter.text.takeIf { it.isNotEmpty() },
                        startDate,
                        endDate,
                        if (includeExported) null else false,
                        if (includeActive && includePassive) null else includeActive
                    )
                    selected = emptyList() // unselect on filter changes
                    sortWords()
                }
                LaunchedEffect(reverseSort, sortByName) {
                    sortWords()
                }
                TopAppBar( // not in the scaffold, thus will not cover data column in wide screen layout
                    title = { Text(stringResource(R.string.gesture_data_review_screen_title)) },
                    navigationIcon = {
                        IconButton(onClick = onClickBack) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                stringResource(R.string.spoken_description_action_previous)
                            )
                        }
                    },
                )
                // todo: this is ugly, rather use checkboxes or some other UI?
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        {
                            includeActive = !includeActive
                            if (!includePassive && !includeActive)
                                includePassive = true
                        },
                        colors = buttonColors,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(checked = includeActive, onCheckedChange = { includeActive = it })
                            Text("active")
                        }
                    }
                    Button(
                        {
                            includePassive = !includePassive
                            if (!includePassive && !includeActive)
                                includeActive = true
                        },
                        colors = buttonColors,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(checked = includePassive, onCheckedChange = { includePassive = it })
                            Text("passive")
                        }
                    }
                    Button({ includeExported = !includeExported }, colors = buttonColors, modifier = Modifier.weight(1f)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Switch(checked = includeExported, onCheckedChange = { includeExported = it })
                            Text("previously exported")
                        }
                    }
                }
                var showDateRangePicker by remember { mutableStateOf(false) }
                val df = DateFormat.getDateInstance(DateFormat.SHORT)
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // allow regex in the filter?
                    TextField(
                        value = filter,
                        onValueChange = { filter = it},
                        modifier = Modifier.weight(0.7f),
                        label = { Text(stringResource(R.string.label_search_key)) }
                    )
                    Column(Modifier
                            .clickable { showDateRangePicker = true }
                            .weight(0.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.gesture_data_date_range), style = MaterialTheme.typography.bodyLarge)
                        if (startDate == null && endDate == null)
                            Text("-", style = MaterialTheme.typography.bodyMedium)
                        else {
                            Text(startDate?.let { df.format(Date(it)) }.toString(), style = MaterialTheme.typography.bodyMedium)
                            Text(endDate?.let { df.format(Date(it)) }.toString(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (showDateRangePicker)
                    DateRangePickerModal({ startDate = it.first; endDate = it.second }) { showDateRangePicker = false }
            }
        }

        if (useWideLayout) {
            Row(Modifier.padding(innerPadding)) {
                Box(Modifier.weight(0.6f)) {
                    controlColumn()
                }
                Box(Modifier.weight(0.4f)) {
                    dataColumn()
                }
            }
        } else {
            Column(Modifier.padding(innerPadding)) {
                controlColumn()
                HorizontalDivider()
                dataColumn()
            }
        }
        if (showExportDialog) {
            ThreeButtonAlertDialog(
                onDismissRequest = { showExportDialog = false },
                content = {
                    val toShare = if (selected.isEmpty()) gestureDataInfos else gestureDataInfos.filter { it.id in selected }
                    val toIgnore = getWordIgnoreList(ctx)
                    Column { ShareGestureData(toShare.filterNot { it.targetWord in toIgnore }.map { it.id }) }
                },
                cancelButtonText = stringResource(R.string.dialog_close),
                onConfirmed = { },
                confirmButtonText = null
            )
        }
        if (showDeleteDialog) {
            ConfirmationDialog(
                onDismissRequest = { showDeleteDialog = false },
                onConfirmed = {
                    val ids = selected.ifEmpty { gestureDataInfos.map { it.id } }
                    dao.delete(ids, false, ctx)
                },
                content = {
                    Text("are you sure? will delete $wordcount words")
                }
            )
        }
    }
}

@Composable
private fun GestureDataEntry(gestureDataInfo: GestureDataInfo, selected: Boolean, anythingSelected: Boolean, onSelect: (Boolean) -> Unit) {
    val modifier = if (!anythingSelected)
        Modifier.combinedClickable(
            onClick = { }, // todo: what should happen? more info in a dialog? option to delete / add to exclusions?
            // todo: swipe? could delete or add to exclusions (with undo bar, don't apply immediately)
            onLongClick = { onSelect(true) },
        )
    else Modifier.selectable(
        selected = selected,
        onClick = { onSelect(!selected) },
    )
    Column(modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp, horizontal = 12.dp)
    ) {
        Text(
            text = gestureDataInfo.targetWord,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        val time = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
            .format(Date(gestureDataInfo.timestamp))
        val mode = if (gestureDataInfo.activeMode) "active" else "passive"
        val exportedExtra = if (gestureDataInfo.exported) ", exported" else ""
        // CompositionLocalProvider vs setting style?
        CompositionLocalProvider(
            LocalTextStyle provides MaterialTheme.typography.bodySmall,
            LocalContentColor provides if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        ) {
            Text(
                text = "$time, $mode$exportedExtra",
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// copied from https://developer.android.com/develop/ui/compose/components/datepickers
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangePickerModal(
    onDateRangeSelected: (Pair<Long?, Long?>) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDateRangePickerState()

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onDateRangeSelected(pickerState.selectedStartDateMillis to pickerState.selectedEndDateMillis)
                    onDismiss()
                }
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.Start, modifier = Modifier.fillMaxWidth(0.7f)) {
                TextButton(onClick = { onDateRangeSelected(null to null); onDismiss() }) {
                    Text(stringResource(R.string.delete))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    ) {
        DateRangePicker(
            state = pickerState,
            title = { Text(stringResource(R.string.gesture_data_date_range)) },
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
