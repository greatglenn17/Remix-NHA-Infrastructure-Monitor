package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.ui.components.DateField
import com.example.ui.components.rememberDiscardGuard
import com.example.util.FormValidation
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun AddTimeExtensionDialog(
    onDismiss: () -> Unit,
    onSubmit: (extNo: Int, days: Int, period: String, reason: String, revisedDate: String, remarks: String) -> Unit
) {
    var extNo by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var period by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    // Not entered here: the revised completion date is computed from the extension days.
    val revisedDate = ""
    var remarks by remember { mutableStateOf("") }

    val snapshot = { listOf(extNo, days, period, reason, remarks) }
    val initial = remember { snapshot() }
    val close = rememberDiscardGuard(snapshot() != initial, onDismiss)

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("ADD TIME EXTENSION", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = extNo, onValueChange = { extNo = it }, label = { Text("Extension No.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("No. of Days") }, isError = daysError(days) != null, supportingText = daysError(days)?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text("Period Considered") })
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") })
                
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
            }
        },
        confirmButton = {
            Button(enabled = daysError(days) == null, onClick = { onSubmit(extNo.trim().toIntOrNull()?:1, days.trim().toIntOrNull()?:0, period, reason, revisedDate, remarks) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
fun AddWorkSuspensionDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, effectivityDate: String, durationDays: Int, endDate: String, reason: String, remarks: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var effectivityDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var durationStr by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    val endDate = remember(effectivityDate, durationStr) {
        try {
            val duration = durationStr.trim().toIntOrNull() ?: 0
            val date = LocalDate.parse(effectivityDate, DateTimeFormatter.ISO_LOCAL_DATE)
            date.plusDays(duration.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            "Invalid Date"
        }
    }

    val snapshot = { listOf(name, effectivityDate, durationStr, reason, remarks) }
    val initial = remember { snapshot() }
    val close = rememberDiscardGuard(snapshot() != initial, onDismiss)

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("ADD WORK SUSPENSION ORDER", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                DateField(label = "Effectivity Date", value = effectivityDate, onValueChange = { effectivityDate = it })
                OutlinedTextField(value = durationStr, onValueChange = { durationStr = it }, label = { Text("Duration (Days)") }, isError = daysError(durationStr) != null, supportingText = daysError(durationStr)?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(value = endDate, onValueChange = {}, label = { Text("End Date (Auto-computed)") }, readOnly = true)
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") })
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
            }
        },
        confirmButton = {
            Button(enabled = daysError(durationStr) == null, onClick = { onSubmit(name, effectivityDate, durationStr.trim().toIntOrNull()?:0, endDate, reason, remarks) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@Composable
fun AddWorkResumptionDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, dateResumed: String, reason: String, remarks: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var dateResumed by remember { mutableStateOf(LocalDate.now().toString()) }
    var reason by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    val snapshot = { listOf(name, dateResumed, reason, remarks) }
    val initial = remember { snapshot() }
    val close = rememberDiscardGuard(snapshot() != initial, onDismiss)

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("ADD WORK RESUMPTION LOG", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                DateField(label = "Date Resumed", value = dateResumed, onValueChange = { dateResumed = it })
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") })
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(name, dateResumed, reason, remarks) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomDocumentDialog(
    initialType: String = "Custom Document",
    onDismiss: () -> Unit,
    onSubmit: (docName: String, remarks: String) -> Unit
) {
    val docTypes = listOf("Custom Document", "Time Extension", "Variation Order", "Work Suspension Order", "Work Resumption Order")
    var selectedType by remember { mutableStateOf(initialType.takeIf { it in docTypes } ?: docTypes[0]) }
    var expanded by remember { mutableStateOf(false) }

    // Custom
    var docName by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    // Time Extension
    var teNo by remember { mutableStateOf("") }
    var teDays by remember { mutableStateOf("") }
    var teReason by remember { mutableStateOf("") }

    // Variation Order
    var voNo by remember { mutableStateOf("") }
    var voType by remember { mutableStateOf("Additive") }
    var voTypeExpanded by remember { mutableStateOf(false) }
    var voAmount by remember { mutableStateOf("") }
    var voReason by remember { mutableStateOf("") }
    var voDateApproved by remember { mutableStateOf("") }
    // Optional while the order is still pending; when filled it must be a real date.
    val voDateValid = remember(voDateApproved) {
        voDateApproved.isBlank() || try { LocalDate.parse(voDateApproved.trim()); true } catch (_: DateTimeParseException) { false }
    }

    // Work Suspension
    var wsNo by remember { mutableStateOf("") }
    var wsType by remember { mutableStateOf("Full") }
    var wsExpanded by remember { mutableStateOf(false) }
    var wsDays by remember { mutableStateOf("") }
    var wsEffectivity by remember { mutableStateOf("") }

    // Work Resumption
    var wrNo by remember { mutableStateOf("") }
    var wrEffectivity by remember { mutableStateOf("") }
    var wrRemarks by remember { mutableStateOf("") }

    val voAmountError = FormValidation.amountError(voAmount, false)
    val snapshot = { listOf(selectedType, docName, remarks, teNo, teDays, teReason, voNo, voType, voAmount, voReason, voDateApproved, wsNo, wsType, wsDays, wsEffectivity, wrNo, wrEffectivity, wrRemarks) }
    val initial = remember { snapshot() }
    val close = rememberDiscardGuard(snapshot() != initial, onDismiss)

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("ADD DOCUMENT", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        readOnly = true,
                        value = selectedType,
                        onValueChange = {},
                        label = { Text("Document Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        docTypes.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption) },
                                onClick = {
                                    selectedType = selectionOption
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                when (selectedType) {
                    "Custom Document" -> {
                        OutlinedTextField(value = docName, onValueChange = { docName = it }, label = { Text("Document Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
                    }
                    "Time Extension" -> {
                        OutlinedTextField(value = teNo, onValueChange = { teNo = it }, label = { Text("Extension No.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = teDays, onValueChange = { teDays = it }, label = { Text("No. of Days") }, isError = daysError(teDays) != null, supportingText = daysError(teDays)?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = teReason, onValueChange = { teReason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
                    }
                    "Variation Order" -> {
                        OutlinedTextField(value = voNo, onValueChange = { voNo = it }, label = { Text("VO No.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        ExposedDropdownMenuBox(
                            expanded = voTypeExpanded,
                            onExpandedChange = { voTypeExpanded = it },
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                readOnly = true,
                                value = voType,
                                onValueChange = {},
                                label = { Text("Type of Variation") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = voTypeExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            )
                            ExposedDropdownMenu(
                                expanded = voTypeExpanded,
                                onDismissRequest = { voTypeExpanded = false },
                            ) {
                                listOf("Additive", "Deductive").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            voType = option
                                            voTypeExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = voAmount,
                            onValueChange = { voAmount = it },
                            label = { Text("Amount (PHP)") },
                            isError = voAmountError != null,
                            supportingText = { Text(voAmountError ?: if (voType == "Deductive") "Will be subtracted from the contract cost" else "Will be added to the contract cost") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(value = voReason, onValueChange = { voReason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
                        DateField(
                            label = "Date Approved", value = voDateApproved, onValueChange = { voDateApproved = it },
                            clearable = true, error = if (voDateValid) null else "Not a valid date"
                        )
                        if (voDateValid) Text("Leave blank if not yet approved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "Work Suspension Order" -> {
                        OutlinedTextField(value = wsNo, onValueChange = { wsNo = it }, label = { Text("Suspension No.") }, modifier = Modifier.fillMaxWidth())
                        ExposedDropdownMenuBox(
                            expanded = wsExpanded,
                            onExpandedChange = { wsExpanded = it },
                        ) {
                            OutlinedTextField(
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                readOnly = true,
                                value = wsType,
                                onValueChange = {},
                                label = { Text("Type of Suspension") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = wsExpanded) },
                                colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            )
                            ExposedDropdownMenu(
                                expanded = wsExpanded,
                                onDismissRequest = { wsExpanded = false },
                            ) {
                                listOf("Full", "Partial").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = {
                                            wsType = option
                                            wsExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        OutlinedTextField(value = wsDays, onValueChange = { wsDays = it }, label = { Text("No. of Days") }, isError = daysError(wsDays) != null, supportingText = daysError(wsDays)?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                        DateField(label = "Effectivity Date", value = wsEffectivity, onValueChange = { wsEffectivity = it }, clearable = true)
                    }
                    "Work Resumption Order" -> {
                        OutlinedTextField(value = wrNo, onValueChange = { wrNo = it }, label = { Text("Resumption No.") }, modifier = Modifier.fillMaxWidth())
                        DateField(label = "Effectivity Date", value = wrEffectivity, onValueChange = { wrEffectivity = it }, clearable = true)
                        OutlinedTextField(value = wrRemarks, onValueChange = { wrRemarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = selectedType != "Variation Order" || (voDateValid && voAmountError == null), onClick = {
                val finalDocName = when (selectedType) {
                    "Custom Document" -> docName.ifBlank { "New Document" }
                    "Time Extension" -> "Time Extension No. ${teNo.trim()} - ${teDays.trim()} Days"
                    "Variation Order" -> voDocumentName(voNo, voType, voAmount)
                    "Work Suspension Order" -> "Work Suspension No. $wsNo ($wsType)"
                    "Work Resumption Order" -> "Work Resumption No. $wrNo"
                    else -> "New Document"
                }
                val finalRemarks = when (selectedType) {
                    "Custom Document" -> remarks
                    "Time Extension" -> "Reason: $teReason"
                    "Variation Order" -> "Reason: $voReason" + if (voDateApproved.isNotBlank()) " | Date Approved: ${voDateApproved.trim()}" else ""
                    "Work Suspension Order" -> {
                        var computedEndDate = ""
                        try {
                            if (wsEffectivity.isNotBlank() && wsDays.isNotBlank()) {
                                val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                val parsedDate = format.parse(wsEffectivity)
                                if (parsedDate != null) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.time = parsedDate
                                    cal.add(java.util.Calendar.DAY_OF_YEAR, wsDays.trim().toInt())
                                    computedEndDate = format.format(cal.time)
                                }
                            }
                        } catch (e: Exception) { }
                        "Duration: ${wsDays.trim()} Days | Effectivity: $wsEffectivity" + if (computedEndDate.isNotBlank()) " | End: $computedEndDate" else ""
                    }
                    "Work Resumption Order" -> "Effectivity: $wrEffectivity | Remarks: $wrRemarks"
                    else -> remarks
                }
                onSubmit(finalDocName, finalRemarks)
            }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

/** Whole-number day counts: blank is fine, anything else must be a non-negative integer. */
private fun daysError(text: String): String? =
    FormValidation.amountError(text, false) ?: if (text.isNotBlank() && text.trim().toIntOrNull() == null) "Whole days only" else null

/** Name stored for a variation order; the amount has spaces stripped so ContractCost can read it back. */
internal fun voDocumentName(voNo: String, voType: String, voAmount: String): String =
    "Variation Order No. $voNo ($voType) - PHP ${voAmount.filterNot { it.isWhitespace() }}"
