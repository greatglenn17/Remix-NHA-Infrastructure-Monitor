package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.data.model.ProjectIssue
import com.example.ui.components.DateField
import com.example.util.FormValidation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddIssueDialog(
    existingIssue: ProjectIssue? = null,
    onDismiss: () -> Unit,
    onSubmit: (date: String, description: String, actionTaken: String, status: String, isCritical: Boolean) -> Unit
) {
    var date by remember { mutableStateOf(existingIssue?.date ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var description by remember { mutableStateOf(existingIssue?.description ?: "") }
    var actionTaken by remember { mutableStateOf(existingIssue?.actionTaken ?: "") }
    var isCritical by remember { mutableStateOf(existingIssue?.isCritical ?: false) }
    var status by remember { mutableStateOf(existingIssue?.status ?: "Pending") }
    var showStatusDropdown by remember { mutableStateOf(false) }

    var tried by remember { mutableStateOf(false) }
    val snapshot = { listOf(date, description, actionTaken, isCritical, status) }
    val initial = remember { snapshot() }
    val close = com.example.ui.components.rememberDiscardGuard(snapshot() != initial, onDismiss)
    val descError = if (tried) FormValidation.requiredError(description) else null
    val actionError = if (tried) FormValidation.requiredError(actionTaken) else null
    AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp).imePadding().systemBarsPadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text(if (existingIssue != null) "Edit Issue" else "Log Issue") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DateField(label = "Date", value = date, onValueChange = { date = it })
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Issue / Concern") },
                    isError = descError != null, supportingText = descError?.let { { Text(it) } },
                    
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = actionTaken,
                    onValueChange = { actionTaken = it },
                    label = { Text("Action Taken") },
                    isError = actionError != null, supportingText = actionError?.let { { Text(it) } },
                    
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isCritical,
                        onCheckedChange = { isCritical = it }
                    )
                    Text("Critical Issue", style = MaterialTheme.typography.bodyMedium)
                }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showStatusDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(status)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Status")
                        }
                    }
                    DropdownMenu(
                        expanded = showStatusDropdown,
                        onDismissRequest = { showStatusDropdown = false }
                    ) {
                        listOf("Pending", "Approved", "No Action").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { status = option; showStatusDropdown = false }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    tried = true
                    if (date.isNotBlank() && description.isNotBlank() && actionTaken.isNotBlank()) {
                        onSubmit(date, description, actionTaken, status, isCritical)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = close) { Text("Cancel") }
        },
    )
}
