// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import helium314.keyboard.latin.R

@Composable
fun SearchPrefScreen(
    onClickBack: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    SearchScreen(
        onClickBack = onClickBack,
        title = title,
        content = content,
        filteredItems = { SettingsActivity2.allPrefs.filter(it) },
        itemContent = { it.Preference() }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T: Any> SearchScreen(
    onClickBack: () -> Unit,
    title: String,
    filteredItems: (String) -> List<T>,
    itemContent: @Composable (T) -> Unit,
    content: @Composable (ColumnScope.() -> Unit)? = null,
) {
    var searchText by remember { mutableStateOf(TextFieldValue()) } // must be outside the column to work without messing up cursor position
    Column(Modifier.fillMaxSize()) {
        // rememberSaveable would be better, but does not work with TextFieldValue
        //  if we just store the string, the cursor is messed up
        //  hmm... no, sth else is messing up that thing, and I just didn't notice
        var showSearch by remember { mutableStateOf(false) }

        fun setShowSearch(value: Boolean) {
            showSearch = value
            if (!value) searchText = TextFieldValue()
        }
        BackHandler {
            if (showSearch) setShowSearch(false)
            else onClickBack()
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    windowInsets = TopAppBarDefaults.windowInsets,
                    navigationIcon = {
                        IconButton(onClick = {
                            if (showSearch) setShowSearch(false)
                            else onClickBack()
                        }) {
                            Icon(
                                painterResource(R.drawable.baseline_arrow_back_24),
                                stringResource(R.string.spoken_description_action_previous)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { setShowSearch(!showSearch) })
                        { Icon(painterResource(R.drawable.sym_keyboard_search_lxx), stringResource(R.string.label_search_key)) }
                    },
                )
                ExpandableSearchField(
                    expanded = showSearch,
                    onDismiss = { setShowSearch(false) },
                    search = searchText,
                    onSearchChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        }
        CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyLarge) {
            if (searchText.text.isBlank() && content != null) {
                Column(
                    Modifier
                        .verticalScroll(rememberScrollState())
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(
                                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                            )
                        )
                ) {
                    content()
                }
            } else {
                val items = filteredItems(searchText.text)
                LazyColumn {
                    items(items) {
                        itemContent(it)
                    }
                }
            }
        }
    }
}

// from StreetComplete
/** Expandable text field that can be dismissed and requests focus when it is expanded */
@Composable
fun ExpandableSearchField(
    expanded: Boolean,
    onDismiss: () -> Unit,
    search: TextFieldValue,
    onSearchChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    colors: TextFieldColors = TextFieldDefaults.colors(),
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(expanded) {
        if (expanded) focusRequester.requestFocus()
    }
    AnimatedVisibility(visible = expanded, modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = modifier.focusRequester(focusRequester),
            leadingIcon = { Icon(painterResource(R.drawable.sym_keyboard_search_lxx), stringResource(R.string.label_search_key)) },
            trailingIcon = { IconButton(onClick = {
                if (search.text.isBlank()) onDismiss()
                else onSearchChange(TextFieldValue())
            }) { Icon(painterResource(R.drawable.ic_close), stringResource(android.R.string.cancel)) } },
            singleLine = true,
            colors = colors
        )
    }
}
