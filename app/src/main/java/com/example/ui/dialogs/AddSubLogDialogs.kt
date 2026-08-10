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
import com.example.ui.theme.GoldAccent
import com.example.ui.theme.NavyPrimary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@Composable
fun AddTimeExtensionDialog(
    onDismiss: () -> Unit,
    onSubmit: (extNo: Int, days: Int, period: String, reason: String, revisedDate: String, remarks: String) -> Unit
) {
    var extNo by remember { mutableStateOf("1") }
    var days by remember { mutableStateOf("30") }
    var period by remember { mutableStateOf("Aug 1 - Aug 30") }
    var reason by remember { mutableStateOf("Weather") }
    var revisedDate by remember { mutableStateOf("2026-09-01") }
    var remarks by remember { mutableStateOf("Approved") }

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text("ADD TIME EXTENSION", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyPrimary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = extNo, onValueChange = { extNo = it }, label = { Text("Extension No.") })
                OutlinedTextField(value = days, onValueChange = { days = it }, label = { Text("No. of Days") })
                OutlinedTextField(value = period, onValueChange = { period = it }, label = { Text("Period Considered") })
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") })
                
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(extNo.toIntOrNull()?:1, days.toIntOrNull()?:0, period, reason, revisedDate, remarks) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddVariationOrderDialog(
    onDismiss: () -> Unit,
    onSubmit: (voNo: Int, desc: String, costDiff: Double, remarks: String) -> Unit
) {
    var voNo by remember { mutableStateOf("1") }
    var desc by remember { mutableStateOf("Additional works") }
    var costDiff by remember { mutableStateOf("100000.0") }
    var remarks by remember { mutableStateOf("Approved") }

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text("ADD VARIATION ORDER", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyPrimary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = voNo, onValueChange = { voNo = it }, label = { Text("VO No.") })
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("Description") })
                OutlinedTextField(value = costDiff, onValueChange = { costDiff = it }, label = { Text("Cost Difference") })
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(voNo.toIntOrNull()?:1, desc, costDiff.toDoubleOrNull()?:0.0, remarks) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddWorkSuspensionDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, effectivityDate: String, durationDays: Int, endDate: String, reason: String, remarks: String) -> Unit
) {
    var name by remember { mutableStateOf("Order No. 2") }
    var effectivityDate by remember { mutableStateOf("2026-08-01") }
    var durationStr by remember { mutableStateOf("7") }
    var reason by remember { mutableStateOf("Typhoon Signal No. 2 torrential rainfall") }
    var remarks by remember { mutableStateOf("Work suspended until further notice") }

    val endDate = remember(effectivityDate, durationStr) {
        try {
            val duration = durationStr.toIntOrNull() ?: 0
            val date = LocalDate.parse(effectivityDate, DateTimeFormatter.ISO_LOCAL_DATE)
            date.plusDays(duration.toLong()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            "Invalid Date"
        }
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text("ADD WORK SUSPENSION ORDER", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyPrimary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = effectivityDate, onValueChange = { effectivityDate = it }, label = { Text("Effectivity Date (YYYY-MM-DD)") })
                OutlinedTextField(value = durationStr, onValueChange = { durationStr = it }, label = { Text("Duration (Days)") })
                OutlinedTextField(value = endDate, onValueChange = {}, label = { Text("End Date (Auto-computed)") }, readOnly = true)
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") })
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(name, effectivityDate, durationStr.toIntOrNull()?:0, endDate, reason, remarks) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddWorkResumptionDialog(
    onDismiss: () -> Unit,
    onSubmit: (name: String, dateResumed: String, reason: String, remarks: String) -> Unit
) {
    var name by remember { mutableStateOf("Resumption Order No. 2") }
    var dateResumed by remember { mutableStateOf("2026-08-05") }
    var reason by remember { mutableStateOf("Weather condition cleared") }
    var remarks by remember { mutableStateOf("Work resumed") }

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text("ADD WORK RESUMPTION LOG", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyPrimary)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(value = dateResumed, onValueChange = { dateResumed = it }, label = { Text("Date Resumed (YYYY-MM-DD)") })
                OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("Reason") })
                OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") })
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(name, dateResumed, reason, remarks) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomDocumentDialog(
    onDismiss: () -> Unit,
    onSubmit: (docName: String, remarks: String) -> Unit
) {
    val docTypes = listOf("Custom Document", "Time Extension", "Variation Order", "Work Suspension Order", "Work Resumption Order")
    var selectedType by remember { mutableStateOf(docTypes[0]) }
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
    var voAmount by remember { mutableStateOf("") }
    var voReason by remember { mutableStateOf("") }

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

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text("ADD DOCUMENT", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NavyPrimary)) },
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
                        OutlinedTextField(value = teNo, onValueChange = { teNo = it }, label = { Text("Extension No.") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = teDays, onValueChange = { teDays = it }, label = { Text("No. of Days") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = teReason, onValueChange = { teReason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
                    }
                    "Variation Order" -> {
                        OutlinedTextField(value = voNo, onValueChange = { voNo = it }, label = { Text("VO No.") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = voAmount, onValueChange = { voAmount = it }, label = { Text("Amount (PHP)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = voReason, onValueChange = { voReason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
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
                        OutlinedTextField(value = wsDays, onValueChange = { wsDays = it }, label = { Text("No. of Days") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = wsEffectivity, onValueChange = { wsEffectivity = it }, label = { Text("Effectivity Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    }
                    "Work Resumption Order" -> {
                        OutlinedTextField(value = wrNo, onValueChange = { wrNo = it }, label = { Text("Resumption No.") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = wrEffectivity, onValueChange = { wrEffectivity = it }, label = { Text("Effectivity Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = wrRemarks, onValueChange = { wrRemarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val finalDocName = when (selectedType) {
                    "Custom Document" -> docName.ifBlank { "New Document" }
                    "Time Extension" -> "Time Extension No. $teNo - $teDays Days"
                    "Variation Order" -> "Variation Order No. $voNo - PHP $voAmount"
                    "Work Suspension Order" -> "Work Suspension No. $wsNo ($wsType)"
                    "Work Resumption Order" -> "Work Resumption No. $wrNo"
                    else -> "New Document"
                }
                val finalRemarks = when (selectedType) {
                    "Custom Document" -> remarks
                    "Time Extension" -> "Reason: $teReason"
                    "Variation Order" -> "Reason: $voReason"
                    "Work Suspension Order" -> {
                        var computedEndDate = ""
                        try {
                            if (wsEffectivity.isNotBlank() && wsDays.isNotBlank()) {
                                val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                val parsedDate = format.parse(wsEffectivity)
                                if (parsedDate != null) {
                                    val cal = java.util.Calendar.getInstance()
                                    cal.time = parsedDate
                                    cal.add(java.util.Calendar.DAY_OF_YEAR, wsDays.toInt())
                                    computedEndDate = format.format(cal.time)
                                }
                            }
                        } catch (e: Exception) { }
                        "Duration: $wsDays Days | Effectivity: $wsEffectivity" + if (computedEndDate.isNotBlank()) " | End: $computedEndDate" else ""
                    }
                    "Work Resumption Order" -> "Effectivity: $wrEffectivity | Remarks: $wrRemarks"
                    else -> remarks
                }
                onSubmit(finalDocName, finalRemarks)
            }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
