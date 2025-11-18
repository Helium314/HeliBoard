package helium314.keyboard.settings.screens.gesturedata

import android.annotation.SuppressLint
import android.content.Context
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.DeleteButton
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.ThreeButtonAlertDialog
import helium314.keyboard.settings.previewDark
import kotlinx.serialization.json.Json
import java.util.Date
import java.util.SortedSet

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
    var showIgnoreListDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var filter by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val width = LocalConfiguration.current.screenWidthDp
    val height = LocalConfiguration.current.screenHeightDp
    val useWideLayout = height < 500 && width > height
    // show "long-press to select" hint somewhere
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom),
        bottomBar = {
            BottomAppBar( // todo: should it rather be in the controlColumn? for more space in landscape mode
                // todo: spread evenly, consistent icon size, enable / disable if necessary
                actions = {
                    Button({ showDeleteDialog = true}, colors = buttonColors) {
                        Column {
                            Icon(painterResource(R.drawable.ic_bin_rounded), "delete", Modifier.align(Alignment.CenterHorizontally))
                            Text(if (selected.isNotEmpty()) "delete selected"
                            else if (filter.text.isNotEmpty()) "delete filtered"
                            else "delete all") // actually it's all displayed, right?
                        }
                    }
                    // todo: extra delete for exported, if there are any?

                    Button({ showExportDialog = true }, colors = buttonColors) {
                        Column {
                            Icon(painterResource(R.drawable.sym_keyboard_language_switch), "share", Modifier.align(Alignment.CenterHorizontally)) // share icon
                            Text(if (selected.isNotEmpty()) "share selected"
                            else if (filter.text.isNotEmpty()) "share filtered"
                            else "share all") // actually it's all displayed, right?
                        }
                    }
                    // todo: only show it once passive gathering is implemented
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
        var gestureDataInfos by remember { mutableStateOf(listOf<GestureDataInfo>()) }
        @Composable fun dataColumn() {
            LazyColumn {
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
                var sortByName: Boolean by rememberSaveable { mutableStateOf(false) }
                var reverseSort: Boolean by rememberSaveable { mutableStateOf(false) }
                val ctx = LocalContext.current
                LaunchedEffect(filter, startDate, endDate, includeExported, sortByName, reverseSort, showIgnoreListDialog) {
                    // todo: should be some background stuff, this could be slow
                    // also we could somehow return a cursor?
                    // and show how many results are currently displayed?
                    gestureDataInfos = dao.filterInfos(
                        filter.text.takeIf { it.isNotEmpty() },
                        startDate,
                        endDate,
                        if (includeExported) null else false,
                        sortByName
                    )
                    if (reverseSort)
                        gestureDataInfos = gestureDataInfos.reversed()
                    selected = emptyList() // unselect on filter changes
                }
                TopAppBar( // todo: should it rather be in the scaffold? for full width
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
                                    text = { Text(if (sortByName) "sort chronologically" else "sort alphabetically") },
                                    onClick = { sortByName = !sortByName; showMenu = false; }
                                )
                                DropdownMenuItem(
                                    text = { Text("reverse order") },
                                    onClick = { reverseSort = !reverseSort; showMenu = false; }
                                )
                            }
                        }
                    }
                )
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
                val df = DateFormat.getDateFormat(ctx)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    //  word text field (allow regex?)
                    // todo: supportingText makes it look misaligned...
                    TextField(value = filter, onValueChange = { filter = it}, supportingText = { Text("filter") }, modifier = Modifier.weight(0.7f))
                    //  time from-to button (set time range in dialog)
                    Column(Modifier
                        .clickable { showDateRangePicker = true }
                        .weight(0.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("date", style = MaterialTheme.typography.bodyLarge)
                        Text(startDate?.let { df.format(Date(it)) }.toString(), style = MaterialTheme.typography.bodyMedium)
                        Text(endDate?.let { df.format(Date(it)) }.toString(), style = MaterialTheme.typography.bodyMedium)
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
                    dataColumn() // todo: first word is under the status bar, should stop earlier than that
                }
            }
        } else {
            Column(Modifier.padding(innerPadding)) {
                controlColumn()
                dataColumn()
            }
        }
        if (showExportDialog) {
            ThreeButtonAlertDialog(
                // todo: we need to forward the data to share to ShareGestureData, the text already says it...
                onDismissRequest = { showExportDialog = false },
                content = {
                    val toShare = if (selected.isEmpty()) gestureDataInfos else gestureDataInfos.filter { it.id in selected }
                    val toIgnore = getIgnoreList(ctx).mapTo(hashSetOf()) { it.lowercase() }
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
                    dao.delete(ids)
                },
                content = {
                    val count = if (selected.isNotEmpty()) selected.size else gestureDataInfos.size
                    Text("are you sure? will delete $count words")
                }
            )
        }
        if (showIgnoreListDialog) {
            @SuppressLint("MutableCollectionMutableState") // if they had an immutable sorted set...
            var ignoreWords by remember { mutableStateOf(getIgnoreList(ctx)) }
            var newWord by remember { mutableStateOf(TextFieldValue()) }
            val scroll = rememberScrollState()
            fun addWord() {
                if (newWord.text.isNotBlank())
                    ignoreWords += newWord.text.trim()
                newWord = TextFieldValue()
            }
            ThreeButtonAlertDialog(
                onDismissRequest = { showIgnoreListDialog = false },
                content = { Column(Modifier.verticalScroll(scroll)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextField(
                            value = newWord,
                            onValueChange = { newWord = it},
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardActions = KeyboardActions { addWord() }
                        )
                        IconButton(
                            { addWord() },
                            Modifier.weight(0.2f)) {
                            Icon(painterResource(R.drawable.ic_plus), stringResource(R.string.add))
                        }
                    }
                    ignoreWords.map { word ->
                        CompositionLocalProvider(
                            LocalTextStyle provides MaterialTheme.typography.bodyLarge
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(word)
                                DeleteButton { ignoreWords = ignoreWords.filterNot { word == it }.toSortedSet() }
                            }
                        }
                    }
                } },
                onConfirmed = {
                    setIgnoreList(ctx, ignoreWords)
                    dao.deleteWords(ignoreWords)
                },
                confirmButtonText = stringResource(android.R.string.ok),
                properties = DialogProperties(dismissOnClickOutside = false)
            )
        }
    }
}

@Composable
private fun GestureDataEntry(gestureDataInfo: GestureDataInfo, selected: Boolean, anythingSelected: Boolean, onSelect: (Boolean) -> Unit) {
    val startModifier = if (!anythingSelected) Modifier.combinedClickable(
        onClick = { },
        onLongClick = { onSelect(true) },
    )
    else Modifier.selectable(
        selected = selected,
        onClick = { onSelect(!selected) },
        // how to use? -> looks like the color change is enough, without those 2 lines
        //interactionSource = remember { MutableInteractionSource() },
        //indication = ripple()
    )
    Text(
        text = gestureDataInfo.targetWord,
        modifier = startModifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 12.dp),
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
                    Text("reset")
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    ) {
        DateRangePicker(
            state = pickerState,
            title = { Text("Select date range") },
            showModeToggle = false,
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp)
                .padding(16.dp)
        )
    }
}

fun setIgnoreList(context: Context, list: Collection<String>) {
    val json = Json.encodeToString(list)
    context.prefs().edit { putString("gesture_data_exclusions", json) }
}

fun getIgnoreList(context: Context): SortedSet<String> {
    val json = context.prefs().getString("gesture_data_exclusions", "[]") ?: "[]"
    if (json.isEmpty()) return sortedSetOf()
    return Json.decodeFromString<List<String>>(json).toSortedSet()
}

@Preview
@Composable
private fun Preview() {
    Theme(previewDark) {
        ReviewScreen { }
    }
}
