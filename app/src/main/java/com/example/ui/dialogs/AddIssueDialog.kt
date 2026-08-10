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
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.example.data.model.ProjectIssue
import com.example.ui.theme.*
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

    val darkTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NavyPrimary,
        unfocusedBorderColor = DarkBorder,
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        cursorColor = NavyPrimary,
        focusedLabelColor = NavyPrimary,
        unfocusedLabelColor = DarkTextSecondary
    )

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding().systemBarsPadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        title = { Text(if (existingIssue != null) "Edit Issue" else "Log Issue", color = Color.White) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Issue / Concern") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                OutlinedTextField(
                    value = actionTaken,
                    onValueChange = { actionTaken = it },
                    label = { Text("Action Taken") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isCritical,
                        onCheckedChange = { isCritical = it },
                        colors = CheckboxDefaults.colors(checkedColor = NavyPrimary, uncheckedColor = DarkBorder)
                    )
                    Text("Critical Issue", color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
                
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showStatusDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, DarkBorder),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = Color.White)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(status)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Status")
                        }
                    }
                    DropdownMenu(
                        expanded = showStatusDropdown,
                        onDismissRequest = { showStatusDropdown = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        listOf("Pending", "Approved", "No Action").forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option, color = Color.White) },
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
                    if (date.isNotBlank() && description.isNotBlank() && actionTaken.isNotBlank()) {
                        onSubmit(date, description, actionTaken, status, isCritical)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NavyPrimary)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DarkTextSecondary) }
        },
        containerColor = DarkSurface
    )
}
