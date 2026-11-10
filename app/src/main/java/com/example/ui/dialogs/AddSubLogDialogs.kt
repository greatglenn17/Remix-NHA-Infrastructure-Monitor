package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
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
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("ADD TIME EXTENSION", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) },
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
            Button(shape = MaterialTheme.shapes.small, enabled = daysError(days) == null, onClick = { onSubmit(extNo.trim().toIntOrNull()?:1, days.trim().toIntOrNull()?:0, period, reason, revisedDate, remarks) }) {
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

    val endDate = com.example.util.ProjectSchedule.endText(effectivityDate, durationStr)

    val snapshot = { listOf(name, effectivityDate, durationStr, reason, remarks) }
    val initial = remember { snapshot() }
    val close = rememberDiscardGuard(snapshot() != initial, onDismiss)

    AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("ADD WORK SUSPENSION ORDER", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) },
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
            Button(shape = MaterialTheme.shapes.small, enabled = daysError(durationStr) == null, onClick = { onSubmit(name, effectivityDate, durationStr.trim().toIntOrNull()?:0, endDate, reason, remarks) }) {
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
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("ADD WORK RESUMPTION LOG", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) },
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
            Button(shape = MaterialTheme.shapes.small, onClick = { onSubmit(name, dateResumed, reason, remarks) }) {
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
    workItems: List<String> = emptyList(),
    // The project's, for a time extension's days from its suspensions.
    documents: List<com.example.data.model.PendingDocument> = emptyList(),
    onSubmit: (docName: String, remarks: String) -> Unit
) {
    val docTypes = listOf("Custom Document", "Time Extension", "Variation Order", "Work Suspension Order", "Work Resumption Order")
    var selectedType by remember { mutableStateOf(initialType.takeIf { it in docTypes } ?: docTypes[0]) }
    var expanded by remember { mutableStateOf(false) }

    // Custom
    var docName by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }
    val order = remember { OrderFields() }

    val snapshot = { listOf(selectedType, docName, remarks) + order.snapshot() }
    val initial = remember { snapshot() }
    val close = rememberDiscardGuard(snapshot() != initial, onDismiss)

    AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("ADD DOCUMENT", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)) },
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

                if (selectedType == "Custom Document") {
                    OutlinedTextField(value = docName, onValueChange = { docName = it }, label = { Text("Document Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = remarks, onValueChange = { remarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
                } else {
                    OrderFieldInputs(selectedType, order, documents, workItems)
                }
            }
        },
        confirmButton = {
            Button(shape = MaterialTheme.shapes.small, enabled = selectedType == "Custom Document" || order.valid(selectedType), onClick = {
                if (selectedType == "Custom Document") onSubmit(docName.ifBlank { "New Document" }, remarks)
                else onSubmit(order.name(selectedType), order.remarks(selectedType))
            }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

/**
 * What an order form holds, for each kind of order ([ORDER_TYPES]); [name] and [remarks] are what the order is saved as.
 * Used by Add Document and by a developer request, which logs the order it asks for.
 */
class OrderFields {
    // Time Extension
    var teNo by mutableStateOf("")
    var teDays by mutableStateOf("")
    var teReason by mutableStateOf("")

    // Variation Order
    var voNo by mutableStateOf("")
    var voType by mutableStateOf("Additive")
    var voAmount by mutableStateOf("")
    var voReason by mutableStateOf("")
    var voDateApproved by mutableStateOf("")

    // Work Suspension
    var wsNo by mutableStateOf("")
    var wsType by mutableStateOf("Full")
    var wsDays by mutableStateOf("")
    var wsEffectivity by mutableStateOf("")
    var wsReason by mutableStateOf("")
    // The bar chart items a Partial suspension pauses.
    val wsItems = androidx.compose.runtime.mutableStateListOf<String>()

    // Continuance of the latest work suspension (see [continued])
    var coType by mutableStateOf("Full")
    var coDays by mutableStateOf("")
    var coEffectivity by mutableStateOf("")

    // Work Resumption
    var wrNo by mutableStateOf("")
    var wrType by mutableStateOf("Full")
    var wrEffectivity by mutableStateOf("")
    var wrRemarks by mutableStateOf("")

    fun snapshot() = listOf(
        teNo, teDays, teReason, voNo, voType, voAmount, voReason, voDateApproved, wsNo, wsType, wsDays, wsEffectivity, wsReason,
        wsItems.toList(), coType, coDays, coEffectivity, wrNo, wrType, wrEffectivity, wrRemarks
    )

    // Optional while the order is still pending; when filled it must be a real date.
    val voDateValid get() = voDateApproved.isBlank() || try { LocalDate.parse(voDateApproved.trim()); true } catch (_: DateTimeParseException) { false }

    fun valid(type: String, documents: List<com.example.data.model.PendingDocument> = emptyList()) = when (type) {
        "Variation Order" -> voDateValid && FormValidation.amountError(voAmount, false) == null
        "Time Extension" -> teDays.isNotBlank() && daysError(teDays) == null
        "Work Suspension Order" -> wsDays.isNotBlank() && daysError(wsDays) == null
        CONTINUANCE -> continued(documents) != null && coDays.isNotBlank() && daysError(coDays) == null && coEffectivity.isNotBlank()
        else -> true
    }

    fun name(type: String, documents: List<com.example.data.model.PendingDocument> = emptyList()) = when (type) {
        "Time Extension" -> "Time Extension No. ${teNo.trim()} - ${teDays.trim()} Days"
        "Variation Order" -> voDocumentName(voNo, voType, voAmount)
        "Work Suspension Order" -> com.example.util.ProjectSchedule.orderName("Suspension", wsNo, wsType)
        "Work Resumption Order" -> com.example.util.ProjectSchedule.orderName("Resumption", wrNo, wrType)
        CONTINUANCE -> continued(documents)?.let { com.example.util.ProjectSchedule.nextContinuance(it, documents, coType).first }.orEmpty()
        else -> "New Document"
    }

    /** Only after [valid]: the day counts must be whole numbers. */
    fun remarks(type: String) = when (type) {
        "Time Extension" -> "Reason: $teReason"
        "Variation Order" -> "Reason: $voReason" + if (voDateApproved.isNotBlank()) " | Date Approved: ${voDateApproved.trim()}" else ""
        "Work Suspension Order" ->
            com.example.util.ProjectSchedule.suspensionRemarks(wsDays.trim().toInt(), wsEffectivity) + (if (wsReason.isNotBlank()) " | Reason: ${wsReason.trim()}" else "") +
                (if (wsType == "Partial") com.example.util.BarChart.itemsRemark(wsItems) else "")
        CONTINUANCE -> com.example.util.ProjectSchedule.suspensionRemarks(coDays.trim().toInt(), coEffectivity)
        "Work Resumption Order" -> "Effectivity: $wrEffectivity | Remarks: $wrRemarks"
        else -> ""
    }

    companion object {
        const val CONTINUANCE = "Continuance of Work Suspension Order"
        val ORDER_TYPES = listOf("Work Suspension Order", CONTINUANCE, "Work Resumption Order", "Variation Order", "Time Extension")

        /** The suspension a new continuance continues: the latest one logged, or null when there is none. */
        fun continued(documents: List<com.example.data.model.PendingDocument>) =
            documents.filter(com.example.util.ProjectSchedule::isSuspension).maxByOrNull { it.id }
    }
}

/** The inputs for one kind of order; [documents] are the project's, for a continuance. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderFieldInputs(
    type: String, f: OrderFields, documents: List<com.example.data.model.PendingDocument> = emptyList(),
    // The bar chart's work items; a Partial suspension picks the ones it pauses.
    workItems: List<String> = emptyList()
) {
    when (type) {
        "Time Extension" -> {
            // For a suspension, the days start as the ones its approved suspensions stopped work; still editable.
            LaunchedEffect(f.teReason) {
                if (f.teDays.isBlank() && com.example.util.BarChart.isForSuspension(f.teReason)) {
                    com.example.util.BarChart.suspensionExtensionDays(documents).takeIf { it > 0 }?.let { f.teDays = it.toString() }
                }
            }
            OutlinedTextField(value = f.teNo, onValueChange = { f.teNo = it }, label = { Text("Extension No.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = f.teDays, onValueChange = { f.teDays = it }, label = { Text("No. of Days") }, isError = daysError(f.teDays) != null, supportingText = daysError(f.teDays)?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = f.teReason, onValueChange = { f.teReason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
        }
        "Variation Order" -> {
            var voTypeExpanded by remember { mutableStateOf(false) }
            val voAmountError = FormValidation.amountError(f.voAmount, false)
            OutlinedTextField(value = f.voNo, onValueChange = { f.voNo = it }, label = { Text("VO No.") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(
                expanded = voTypeExpanded,
                onExpandedChange = { voTypeExpanded = it },
            ) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = f.voType,
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
                                f.voType = option
                                voTypeExpanded = false
                            }
                        )
                    }
                }
            }
            OutlinedTextField(
                value = f.voAmount,
                onValueChange = { f.voAmount = it },
                label = { Text("Amount (PHP)") },
                isError = voAmountError != null,
                supportingText = { Text(voAmountError ?: if (f.voType == "Deductive") "Will be subtracted from the contract cost" else "Will be added to the contract cost") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(value = f.voReason, onValueChange = { f.voReason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
            DateField(
                label = "Date Approved", value = f.voDateApproved, onValueChange = { f.voDateApproved = it },
                clearable = true, error = if (f.voDateValid) null else "Not a valid date"
            )
            if (f.voDateValid) Text("Leave blank if not yet approved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        "Work Suspension Order" -> {
            OutlinedTextField(value = f.wsNo, onValueChange = { f.wsNo = it }, label = { Text("Suspension No.") }, modifier = Modifier.fillMaxWidth())
            FullOrPartialPicker("Type of Suspension", f.wsType) { f.wsType = it }
            OutlinedTextField(value = f.wsDays, onValueChange = { f.wsDays = it }, label = { Text("No. of Days") }, isError = daysError(f.wsDays) != null, supportingText = daysError(f.wsDays)?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            DateField(label = "Effectivity Date", value = f.wsEffectivity, onValueChange = { f.wsEffectivity = it }, clearable = true)
            SuspensionEndField(f.wsEffectivity, f.wsDays) { f.wsDays = it }
            OutlinedTextField(value = f.wsReason, onValueChange = { f.wsReason = it }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth())
            if (f.wsType == "Partial" && workItems.isNotEmpty()) {
                Text("Work items suspended (their target pauses; the rest go on)", style = MaterialTheme.typography.labelMedium)
                workItems.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().testTag("suspended_item_$item")) {
                        Checkbox(checked = item in f.wsItems, onCheckedChange = { if (it) f.wsItems.add(item) else f.wsItems.remove(item) })
                        Text(item, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        OrderFields.CONTINUANCE -> {
            val suspension = OrderFields.continued(documents)
            if (suspension == null) {
                Text(
                    "No work suspension order is logged yet, so there is nothing to continue. Log the suspension first.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                )
            } else {
                // Starts where the suspension and its continuances so far end.
                LaunchedEffect(suspension.id) {
                    if (f.coEffectivity.isBlank()) f.coEffectivity = com.example.util.ProjectSchedule.nextContinuance(suspension, documents).second
                }
                Text(f.name(type, documents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                FullOrPartialPicker("Type of Suspension", f.coType) { f.coType = it }
                OutlinedTextField(value = f.coDays, onValueChange = { f.coDays = it }, label = { Text("No. of Days") }, isError = daysError(f.coDays) != null, supportingText = daysError(f.coDays)?.let { { Text(it) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                DateField(label = "Effectivity Date", value = f.coEffectivity, onValueChange = { f.coEffectivity = it })
                SuspensionEndField(f.coEffectivity, f.coDays) { f.coDays = it }
            }
        }
        "Work Resumption Order" -> {
            OutlinedTextField(value = f.wrNo, onValueChange = { f.wrNo = it }, label = { Text("Resumption No.") }, modifier = Modifier.fillMaxWidth())
            FullOrPartialPicker("Type of Resumption", f.wrType) { f.wrType = it }
            DateField(label = "Effectivity Date", value = f.wrEffectivity, onValueChange = { f.wrEffectivity = it }, clearable = true)
            OutlinedTextField(value = f.wrRemarks, onValueChange = { f.wrRemarks = it }, label = { Text("Remarks") }, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** A suspension's or continuance's last day: follows the days typed (the effectivity date is day 1); picking it sets the days. */
@Composable
internal fun SuspensionEndField(effectivity: String, days: String, onDays: (String) -> Unit) = DateField(
    label = "End Date", value = com.example.util.ProjectSchedule.endText(effectivity, days),
    onValueChange = { end -> com.example.util.ProjectSchedule.daysText(effectivity, end)?.let(onDays) },
    modifier = Modifier.testTag("suspension_end_date")
)

/** Full or Partial, for work suspension, continuance and resumption orders; saved in the order's name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullOrPartialPicker(label: String, value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            readOnly = true,
            value = value,
            onValueChange = {},
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Full", "Partial").forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false })
            }
        }
    }
}

/** Whole-number day counts: blank is fine, anything else must be a non-negative integer. */
internal fun daysError(text: String): String? =
    FormValidation.amountError(text, false) ?: if (text.isNotBlank() && text.trim().toIntOrNull() == null) "Whole days only" else null

/** Name stored for a variation order; the amount has spaces stripped so ContractCost can read it back. */
internal fun voDocumentName(voNo: String, voType: String, voAmount: String): String =
    "Variation Order No. $voNo ($voType) - PHP ${voAmount.filterNot { it.isWhitespace() }}"
