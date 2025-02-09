/*
 * Copyright (C) 2021 The Android Open Source Project
 * parts taken from Material3 AlertDialog.kt
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.settings.dialogs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import helium314.keyboard.settings.Theme

// text should be smaller, and background should be darkened
@Composable
fun ThreeButtonAlertDialog(
    onDismissRequest: () -> Unit,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    onNeutral: () -> Unit = { },
    checkOk: () -> Boolean = { true },
    confirmButtonText: String? = stringResource(android.R.string.ok),
    cancelButtonText: String = stringResource(android.R.string.cancel),
    neutralButtonText: String? = null,
    properties: DialogProperties = DialogProperties()
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        Box(
            modifier = modifier.sizeIn(minWidth = 280.dp, maxWidth = 560.dp),
            propagateMinConstraints = true
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                contentColor = contentColorFor(MaterialTheme.colorScheme.surface),
            ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
                title?.let {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.titleLarge) {
                        Box(Modifier.padding(PaddingValues(bottom = 16.dp))) {
                            title()
                        }
                    }
                }
                text?.let {
                    CompositionLocalProvider(LocalTextStyle provides MaterialTheme.typography.bodyMedium) {
                        Box(Modifier.weight(weight = 1f, fill = false).padding(bottom = 8.dp)) {
                            text()
                        }
                    }
                }
                Row {
                    if (neutralButtonText != null)
                        TextButton(
                            onClick = onNeutral
                        ) { Text(neutralButtonText) }
                    Spacer(modifier.weight(1f))
                    TextButton(onClick = onDismissRequest) { Text(cancelButtonText) }
                    if (confirmButtonText != null)
                        TextButton(
                            enabled = checkOk(),
                            onClick = { onDismissRequest(); onConfirmed() },
                        ) { Text(confirmButtonText) }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    Theme {
        ThreeButtonAlertDialog(
            onDismissRequest = {},
            onConfirmed = { },
            text = { Text("hello") },
            title = { Text("title") },
            neutralButtonText = "Default"
        )
    }
}
