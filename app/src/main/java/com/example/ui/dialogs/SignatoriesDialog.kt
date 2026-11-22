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
private val WEEKLY_CHECKED_POSITIONS = listOf("Principal Engineer A", "Principal Architect A")
private val WEEKLY_NOTED_POSITIONS = CHECKED_POSITIONS
private val OFFICES = listOf("Bulacan District Office", "Region 3 Office")

/** Which print the signatories are for; each keeps its own on the project (Prepared by is shared by weekly and monthly). */
enum class SignatoriesKind { WEEKLY, MONTHLY, BILLING }

/** Asks who signs a weekly or monthly Blue Book report, or the billing abstract, before it is printed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatoriesDialog(
    initial: Signatories,
    kind: SignatoriesKind,
    onDismiss: () -> Unit,
    onPrint: (Signatories) -> Unit
) {
    var preparedName by remember { mutableStateOf(initial.preparedName) }
    var preparedPosition by remember { mutableStateOf(initial.preparedPosition) }
    var checkedName by remember { mutableStateOf(initial.checkedName) }
    var checkedPosition by remember { mutableStateOf(initial.checkedPosition) }
    var notedName by remember { mutableStateOf(initial.notedName) }
    var notedPosition by remember { mutableStateOf(initial.notedPosition) }
    var preparedOffice by remember { mutableStateOf(initial.preparedOffice) }
    var checkedOffice by remember { mutableStateOf(initial.checkedOffice) }
    var notedOffice by remember { mutableStateOf(initial.notedOffice) }
    var reviewedName by remember { mutableStateOf(initial.reviewedName) }
    var reviewedPosition by remember { mutableStateOf(initial.reviewedPosition) }
    var reviewedOffice by remember { mutableStateOf(initial.reviewedOffice) }
    val weekly = kind == SignatoriesKind.WEEKLY

    // Editable, so a value that is not in the list can still be typed; with no list, a plain text field.
    @Composable
    fun Choice(label: String, value: String, onValue: (String) -> Unit, options: List<String>) {
        if (options.isEmpty()) {
            OutlinedTextField(value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            return
        }
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = value, onValueChange = onValue, label = { Text(label) }, singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                // PrimaryEditable keeps the keyboard on the field while the list is open.
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onValue(option); expanded = false },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }

    @Composable
    fun Signer(
        title: String, name: String, onName: (String) -> Unit, position: String, onPosition: (String) -> Unit, positions: List<String>,
        office: String, offices: List<String> = OFFICES, onOffice: (String) -> Unit
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(value = name, onValueChange = onName, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Choice("Position", position, onPosition, positions)
        Choice("Office", office, onOffice, offices)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(when (kind) {
                SignatoriesKind.WEEKLY -> "Weekly report signatories"
                SignatoriesKind.MONTHLY -> "Monthly report signatories"
                SignatoriesKind.BILLING -> "Billing abstract signatories"
            })
        },
        text = {
            // The billing abstract: the contractor prepares (free text), the engineer checks, the district reviews, the region recommends.
            if (kind == SignatoriesKind.BILLING) Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Signer("Prepared by", preparedName, { preparedName = it }, preparedPosition, { preparedPosition = it }, emptyList(), preparedOffice, emptyList()) { preparedOffice = it }
                Signer("Checked by", checkedName, { checkedName = it }, checkedPosition, { checkedPosition = it }, PREPARED_POSITIONS, checkedOffice) { checkedOffice = it }
                Signer("Reviewed by", reviewedName, { reviewedName = it }, reviewedPosition, { reviewedPosition = it }, CHECKED_POSITIONS, reviewedOffice) { reviewedOffice = it }
                Signer("Recommending Approval", notedName, { notedName = it }, notedPosition, { notedPosition = it }, NOTED_POSITIONS, notedOffice) { notedOffice = it }
            } else Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                Signer("Prepared by", preparedName, { preparedName = it }, preparedPosition, { preparedPosition = it }, PREPARED_POSITIONS, preparedOffice) { preparedOffice = it }
                Signer(if (weekly) "Checked and Reviewed by" else "Checked by", checkedName, { checkedName = it }, checkedPosition, { checkedPosition = it }, if (weekly) WEEKLY_CHECKED_POSITIONS else CHECKED_POSITIONS, checkedOffice) { checkedOffice = it }
                Signer("Noted by", notedName, { notedName = it }, notedPosition, { notedPosition = it }, if (weekly) WEEKLY_NOTED_POSITIONS else NOTED_POSITIONS, notedOffice) { notedOffice = it }
            }
        },
        confirmButton = {
            Button(shape = MaterialTheme.shapes.small, 
                onClick = {
                    onPrint(Signatories(
                        preparedName.trim(), preparedPosition.trim(), checkedName.trim(), checkedPosition.trim(), notedName.trim(), notedPosition.trim(),
                        preparedOffice.trim(), checkedOffice.trim(), notedOffice.trim(),
                        reviewedName.trim(), reviewedPosition.trim(), reviewedOffice.trim()
                    ))
                },
                modifier = Modifier.testTag("signatories_print_button")
            ) { Text("Print") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
