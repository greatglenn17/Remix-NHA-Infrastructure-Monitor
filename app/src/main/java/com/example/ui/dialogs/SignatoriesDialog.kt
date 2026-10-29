package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.util.Signatories

private val PREPARED_POSITIONS = listOf(
    "Senior Engineer A", "Senior Architect A", "Supervising Engineer A",
    "Principal Engineer C", "Principal Engineer A", "Principal Architect A"
)
private val CHECKED_POSITIONS = listOf("District Manager", "Officer-in-Charge")
private val NOTED_POSITIONS = listOf("Regional Manager", "Officer-in-Charge")

/** Asks who signs a weekly or monthly Blue Book report before it is printed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatoriesDialog(
    initial: Signatories,
    onDismiss: () -> Unit,
    onPrint: (Signatories) -> Unit
) {
    var preparedName by remember { mutableStateOf(initial.preparedName) }
    var preparedPosition by remember { mutableStateOf(initial.preparedPosition) }
    var checkedName by remember { mutableStateOf(initial.checkedName) }
    var checkedPosition by remember { mutableStateOf(initial.checkedPosition) }
    var notedName by remember { mutableStateOf(initial.notedName) }
    var notedPosition by remember { mutableStateOf(initial.notedPosition) }

    @Composable
    fun Signer(title: String, name: String, onName: (String) -> Unit, position: String, onPosition: (String) -> Unit, positions: List<String>) {
        var expanded by remember { mutableStateOf(false) }
        Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(value = name, onValueChange = onName, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        // Editable, so a position that is not in the list can still be typed.
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = position, onValueChange = onPosition, label = { Text("Position") }, singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                positions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onPosition(option); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Signatories") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Signer("Prepared by", preparedName, { preparedName = it }, preparedPosition, { preparedPosition = it }, PREPARED_POSITIONS)
                Signer("Checked by", checkedName, { checkedName = it }, checkedPosition, { checkedPosition = it }, CHECKED_POSITIONS)
                Signer("Noted by", notedName, { notedName = it }, notedPosition, { notedPosition = it }, NOTED_POSITIONS)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onPrint(Signatories(preparedName.trim(), preparedPosition.trim(), checkedName.trim(), checkedPosition.trim(), notedName.trim(), notedPosition.trim()))
                },
                modifier = Modifier.testTag("signatories_print_button")
            ) { Text("Print") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
