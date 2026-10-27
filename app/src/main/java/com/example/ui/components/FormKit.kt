package com.example.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Discard changes?") },
        text = { Text("Your changes will be lost.") },
        confirmButton = { TextButton(onClick = onDiscard) { Text("Discard") } },
        dismissButton = { TextButton(onClick = onKeepEditing) { Text("Keep editing") } }
    )
}

@Composable
fun rememberDiscardGuard(dirty: Boolean, onDismiss: () -> Unit): () -> Unit {
    var asking by remember { mutableStateOf(false) }
    if (asking) DiscardChangesDialog(
        onDiscard = { asking = false; onDismiss() },
        onKeepEditing = { asking = false }
    )
    return { if (dirty) asking = true else onDismiss() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongForm(
    title: String,
    dirty: Boolean,
    saveEnabled: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    saveTag: String,
    bottomStart: @Composable RowScope.() -> Unit = {},
    topContent: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    val close = rememberDiscardGuard(dirty, onDismiss)
    val cfg = LocalConfiguration.current
    val phone = cfg.screenWidthDp < 600 || cfg.screenHeightDp < 480 // landscape phones too
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Content is sized to the display, but the dialog window is fitted between the system bars, clipping the Save bar; let it cover the screen (insets are padded below).
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            if (phone && window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.attributes = window.attributes.apply {
                    setFitInsetsTypes(0)
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }
        }
        BackHandler(onBack = close)
        Surface(
            modifier = if (phone) Modifier.fillMaxSize() else Modifier.width(640.dp).fillMaxHeight(0.9f),
            shape = if (phone) MaterialTheme.shapes.extraSmall else MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface
        ) {
            Scaffold(
                modifier = Modifier.imePadding(),
                containerColor = MaterialTheme.colorScheme.surface,
                topBar = {
                    Column {
                        TopAppBar(
                            title = { Text(title) },
                            navigationIcon = {
                                IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = "Close") }
                            }
                        )
                        topContent()
                    }
                },
                bottomBar = {
                    Column(Modifier.navigationBarsPadding()) {
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            bottomStart()
                            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                                Button(onClick = onSave, enabled = saveEnabled, modifier = Modifier.testTag(saveTag)) { Text("Save") }
                            }
                        }
                    }
                }
            ) { pad ->
                Column(
                    Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}

/** "yyyy-MM-dd" to UTC-midnight millis (what M3 DatePicker uses); null when not a date or outside the picker's 1900-2100 range. */
fun dateToUtcMillis(date: String): Long? =
    runCatching { LocalDate.parse(date) }.getOrNull()
        ?.takeIf { it.year in 1900..2100 }
        ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

fun utcMillisToDate(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    clearable: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    val spoken = listOfNotNull(label, value.ifBlank { "not set" }, error).joinToString(", ")
    Box(modifier.fillMaxWidth().clearAndSetSemantics {
        contentDescription = spoken
        role = Role.Button
        onClick(label = "Pick $label") { open = true; true }
    }) {
        OutlinedTextField(
            value = value, onValueChange = {}, readOnly = true, label = { Text(label) },
            isError = error != null, supportingText = error?.let { { Text(it) } },
            trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )
        // Transparent overlay so a tap anywhere on the field opens the picker.
        Box(Modifier.matchParentSize().clickable { open = true })
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = dateToUtcMillis(value))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onValueChange(utcMillisToDate(it)) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (clearable) TextButton(onClick = { onValueChange(""); open = false }) { Text("Clear") }
                    TextButton(onClick = { open = false }) { Text("Cancel") }
                }
            }
        ) { DatePicker(state = state) }
    }
}
