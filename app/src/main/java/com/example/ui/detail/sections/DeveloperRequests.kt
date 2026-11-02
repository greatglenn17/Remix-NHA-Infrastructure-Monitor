package com.example.ui.detail.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.MoveToInbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.DeveloperRequest
import com.example.ui.components.DateField
import com.example.ui.theme.LocalNHAColors
import com.example.util.ProjectAttention
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Developer requests that lead to orders; the ones not yet packaged stand out. */
@Composable
fun DeveloperRequestsCard(
    requests: List<DeveloperRequest>,
    today: LocalDate,
    onAdd: (() -> Unit)?,
    onOpen: ((DeveloperRequest) -> Unit)?,
    modifier: Modifier
) {
    val openFile = com.example.ui.components.rememberAttachmentOpener()
    SectionCard(
        "Developer requests",
        Icons.Default.MoveToInbox,
        modifier,
        action = onAdd?.let { add ->
            {
                TextButton(onClick = add, modifier = Modifier.testTag("add_developer_request_button")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add request")
                }
            }
        }
    ) {
        if (requests.isEmpty()) EmptyLine("No requests from the developer.")
        requests.forEach { r ->
            val packaged = r.datePackaged.isNotBlank()
            val waiting = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(r.dateReceived), today) }.getOrNull()
            LogEntry(
                r.requestType,
                if (packaged) MaterialTheme.colorScheme.onSurface else LocalNHAColors.current.watch,
                listOf(
                    "Requested: ${r.dateRequested.ifBlank { "-" }}  ·  Received: ${r.dateReceived.ifBlank { "-" }}",
                    if (packaged) "Packaged: ${r.datePackaged}" else "NOT YET PACKAGED" + (waiting?.let { " · $it days since received" } ?: ""),
                    r.details
                ),
                onClick = onOpen?.let { open -> { open(r) } },
                trailing = r.requestFileUrl.takeIf { it.isNotBlank() }?.let { file ->
                    {
                        IconButton(onClick = { openFile(file) }, modifier = Modifier.testTag("developer_request_file_${r.id}")) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Open the request letter", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            )
        }
    }
}

/** Adds or edits a developer request; [existing] null adds one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeveloperRequestDialog(
    projectId: Long,
    existing: DeveloperRequest?,
    onDismiss: () -> Unit,
    onSave: (DeveloperRequest) -> Unit,
    onDelete: ((DeveloperRequest) -> Unit)?
) {
    var type by remember { mutableStateOf(existing?.requestType ?: ProjectAttention.REQUEST_TYPES.first()) }
    var details by remember { mutableStateOf(existing?.details ?: "") }
    var requested by remember { mutableStateOf(existing?.dateRequested ?: "") }
    var received by remember { mutableStateOf(existing?.dateReceived ?: LocalDate.now().toString()) }
    var packaged by remember { mutableStateOf(existing?.datePackaged ?: "") }
    var requestFile by remember { mutableStateOf(existing?.requestFileUrl ?: "") }
    var expanded by remember { mutableStateOf(false) }
    val snapshot = listOf(type, details, requested, received, packaged, requestFile)
    val initial = remember { snapshot }
    val close = com.example.ui.components.rememberDiscardGuard(snapshot != initial, onDismiss)
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (existing == null) "New developer request" else "Developer request") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = type, onValueChange = {}, readOnly = true, label = { Text("Request for") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        ProjectAttention.REQUEST_TYPES.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { type = option; expanded = false }, contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                        }
                    }
                }
                OutlinedTextField(value = details, onValueChange = { details = it }, label = { Text("Details (letter no., reason)") }, modifier = Modifier.fillMaxWidth())
                DateField(label = "Date of developer's request", value = requested, onValueChange = { requested = it }, clearable = true)
                DateField(label = "Date received by NHA", value = received, onValueChange = { received = it })
                DateField(label = "Date packaged (blank: not yet)", value = packaged, onValueChange = { packaged = it }, clearable = true)
                Text("Developer's request letter", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                LetterFileField("request", requestFile, "developer_request_file") { requestFile = it }
                if (existing != null && onDelete != null) {
                    TextButton(onClick = { onDelete(existing) }) { Text("Delete request", color = LocalNHAColors.current.behind) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = received.isNotBlank(),
                onClick = {
                    val base = existing ?: DeveloperRequest(projectId = projectId, requestType = type)
                    onSave(base.copy(requestType = type, details = details.trim(), dateRequested = requested, dateReceived = received, datePackaged = packaged, requestFileUrl = requestFile))
                },
                modifier = Modifier.testTag("save_developer_request_button")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}
