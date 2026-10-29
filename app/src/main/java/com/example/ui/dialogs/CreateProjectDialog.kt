package com.example.ui.dialogs
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import java.time.LocalDate
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LongForm
import com.example.ui.components.DateField
import com.example.ui.theme.LocalNHAColors
import com.example.util.FormValidation
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    onDismiss: () -> Unit,
    /** The signed-in user; a new project is assigned to them unless changed, so it shows on their phone. */
    creatorName: String,
    onCreate: (
        name: String,
        location: String,
        implementingOffice: String,
        contractor: String,
        scopeOfWork: String,
        projectType: String,
        landArea: String,
        numberOfUnits: String,
        contractCostOriginal: Double,
        contractDurationDays: Int,
        dateStarted: String,
        completionDateOriginal: String,
        assignedStaff: String
    ) -> Unit,
    today: LocalDate = LocalDate.now()
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var assignedStaff by remember { mutableStateOf(creatorName) }
    var implementingOffice by remember { mutableStateOf("Bulacan District Office") }
    var contractor by remember { mutableStateOf("") }
    val scopeItems = remember { mutableStateListOf<com.example.data.model.ScopeItem>() }
    var projectType by remember { mutableStateOf("Housing Project") }
    var landAreaValue by remember { mutableStateOf("") }
    var landAreaUnit by remember { mutableStateOf("Hectares") }
    var showLandAreaUnitDropdown by remember { mutableStateOf(false) }
    var numberOfUnits by remember { mutableStateOf("") }
    var isCustomProjectType by remember { mutableStateOf(false) }
    var showProjectTypeDropdown by remember { mutableStateOf(false) }
    // Contract cost is auto-computed from scope items
    
    var durationText by remember { mutableStateOf("") }
    var dateStarted by remember { mutableStateOf(today.toString()) }
    
    val completionDateOriginal = remember(dateStarted, durationText) {
        try {
            val start = LocalDate.parse(dateStarted)
            val duration = durationText.trim().toIntOrNull() ?: 0
            if (duration > 0) {
                start.plusDays((duration - 1).toLong()).toString()
            } else {
                start.toString()
            }
        } catch (e: Exception) {
            "Invalid Date"
        }
    }

    val cs = MaterialTheme.colorScheme
    val nha = LocalNHAColors.current
    val nameError = FormValidation.requiredError(name)
    val contractorError = FormValidation.requiredError(contractor)
    val durationError = daysError(durationText)
    fun snapshot() = listOf(
        name, location, assignedStaff, implementingOffice, contractor, scopeItems.toList(),
        projectType, landAreaValue,
        landAreaUnit, numberOfUnits, durationText, dateStarted
    )
    val initial = remember { snapshot() }
    val dirty = snapshot() != initial

    LongForm(
        title = "New project",
        dirty = dirty,
        saveEnabled = nameError == null && contractorError == null && durationError == null,
        saveTag = "submit_create_project",
        onDismiss = onDismiss,
        onSave = {
            val cost = scopeItems.sumOf { it.amount }
            val duration = durationText.trim().toIntOrNull() ?: 365
            val landArea = if (landAreaValue.isBlank()) "" else "${landAreaValue.trim()} $landAreaUnit"
            onCreate(
                name,
                location,
                implementingOffice,
                contractor,
                JSONArray(withAutoWeights(scopeItems).map { JSONObject().put("name", it.name).put("amount", it.amount).put("weightPct", it.weightPct) }).toString(),
                projectType,
                landArea,
                numberOfUnits,
                cost,
                duration,
                dateStarted,
                completionDateOriginal,
                assignedStaff
            )
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Project Name") },
            modifier = Modifier.fillMaxWidth().testTag("input_project_name"),
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            singleLine = true
        )

        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location") },
            modifier = Modifier.fillMaxWidth().testTag("input_project_location"),
            singleLine = true
        )

        OutlinedTextField(
            value = implementingOffice,
            onValueChange = { implementingOffice = it },
            label = { Text("Implementing Office") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = contractor,
            onValueChange = { contractor = it },
            label = { Text("Contractor Name") },
            modifier = Modifier.fillMaxWidth().testTag("input_project_contractor"),
            isError = contractorError != null,
            supportingText = contractorError?.let { { Text(it) } },
            singleLine = true
        )

        ScopeOfWorksEditor(scopeItems)
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showProjectTypeDropdown = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, cs.outlineVariant),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = cs.surfaceVariant, contentColor = cs.onSurface)
            ) {
                Text("Project Type: ${if (isCustomProjectType) "Custom" else projectType}", color = cs.onSurface)
            }
            DropdownMenu(
                expanded = showProjectTypeDropdown,
                onDismissRequest = { showProjectTypeDropdown = false },
                modifier = Modifier.background(cs.surface)
            ) {
                listOf("Housing Project", "Community Facility", "Custom...").forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type, color = cs.onSurface) },
                        onClick = {
                            if (type == "Custom...") {
                                isCustomProjectType = true
                                projectType = ""
                            } else {
                                isCustomProjectType = false
                                projectType = type
                            }
                            showProjectTypeDropdown = false
                        }
                    )
                }
            }
        }
        if (isCustomProjectType) {
            OutlinedTextField(
                value = projectType,
                onValueChange = { projectType = it },
                label = { Text("Custom Project Type") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }

        if (projectType.contains("Housing", ignoreCase = true)) {
            // Land Area: number input + unit dropdown
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedTextField(
                    value = landAreaValue,
                    onValueChange = { landAreaValue = it },
                    label = { Text("Land Area", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1.2f).testTag("input_land_area"),
                    singleLine = true
                )
                Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                    OutlinedButton(
                        onClick = { showLandAreaUnitDropdown = true },
                        modifier = Modifier.fillMaxWidth().height(54.dp),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, cs.outlineVariant),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = cs.surfaceVariant, contentColor = cs.onSurface),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Text(landAreaUnit, color = cs.onSurface, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Unit Dropdown", tint = cs.primary)
                        }
                    }
                    DropdownMenu(
                        expanded = showLandAreaUnitDropdown,
                        onDismissRequest = { showLandAreaUnitDropdown = false },
                        modifier = Modifier.background(cs.surface)
                    ) {
                        listOf("Hectares", "Square Meters").forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit, color = cs.onSurface) },
                                onClick = {
                                    landAreaUnit = unit
                                    showLandAreaUnitDropdown = false
                                }
                            )
                        }
                    }
                }
            }
            // No. of Units: number-only input
            OutlinedTextField(
                value = numberOfUnits,
                onValueChange = { numberOfUnits = it },
                label = { Text("No. of Units") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().testTag("input_number_of_units"),
                singleLine = true
            )
        }

        // Auto-computed Original Contract Cost from Scope of Work amounts
        val autoComputedCost = scopeItems.sumOf { it.amount }
        Surface(
            color = cs.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Original Contract Cost (Auto-Computed)", style = MaterialTheme.typography.labelSmall.copy(color = cs.onSurfaceVariant))
                Text(
                    text = com.example.utils.CurrencyFormatter.formatPhp(autoComputedCost),
                    style = MaterialTheme.typography.titleMedium.copy(color = cs.onSurface, fontWeight = FontWeight.Bold)
                )
            }
        }

        OutlinedTextField(
            value = durationText,
            onValueChange = { durationText = it },
            label = { Text("Contract Duration (Days)") },
            isError = durationError != null, supportingText = durationError?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        OutlinedTextField(
            value = assignedStaff,
            onValueChange = { assignedStaff = it },
            label = { Text("Assigned Field Staff / Manager") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(label = "Date Started", value = dateStarted, onValueChange = { dateStarted = it }, modifier = Modifier.weight(1f))
            
            OutlinedTextField(
                value = completionDateOriginal,
                onValueChange = { },
                label = { Text("Completion Date") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                readOnly = true
            )
        }
    }
}
