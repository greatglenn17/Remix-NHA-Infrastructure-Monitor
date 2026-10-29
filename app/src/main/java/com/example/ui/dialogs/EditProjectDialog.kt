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
import com.example.data.model.Project
import com.example.ui.components.LongForm
import com.example.ui.components.DateField
import com.example.ui.theme.LocalNHAColors
import com.example.util.FormValidation
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProjectDialog(
    project: Project,
    canAssign: Boolean = false,
    registeredUsers: List<com.example.data.model.RegisteredUser> = emptyList(),
    registeredUsersLoaded: Boolean = false,
    onDismiss: () -> Unit,
    onEdit: (
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
        assignedStaff: String,
        assignedUsers: List<com.example.data.model.RegisteredUser>?,
        projectedBeneficiaries: String,
        boardResolution: String,
        noticeOfAward: String,
        noticeToProceed: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(project.name) }
    var projectedBeneficiaries by remember { mutableStateOf(project.projectedBeneficiaries) }
    var boardResolution by remember { mutableStateOf(project.boardResolution) }
    var noticeOfAward by remember { mutableStateOf(project.noticeOfAward) }
    var noticeToProceed by remember { mutableStateOf(project.noticeToProceed) }
    var location by remember { mutableStateOf(project.location) }
    val assignedStaff = project.assignedStaff
    // A blank list means this phone has never received the assignment: editing it would send "[]" and clear it for everyone.
    val canEditAssignment = canAssign && registeredUsersLoaded && project.assignedUids.isNotBlank()
    var selectedUids by remember { mutableStateOf(com.example.data.model.AssignedUids.parse(project.assignedUids)) }
    var implementingOffice by remember { mutableStateOf(project.implementingOffice) }
    var contractor by remember { mutableStateOf(project.contractor) }
    val scopeItems = remember { mutableStateListOf<com.example.data.model.ScopeItem>().apply { 
        try {
            val arr = JSONArray(project.scopeOfWork)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(com.example.data.model.ScopeItem(obj.getString("name"), obj.optDouble("amount", 0.0), obj.getDouble("weightPct")))
            }
        } catch (e: Exception) {
            // Fallback for old string format
            if (project.scopeOfWork.isNotBlank()) {
               add(com.example.data.model.ScopeItem(project.scopeOfWork, 0.0, 100.0))
            }
        }
    } }
    
    var projectType by remember { mutableStateOf(project.projectType) }
    // Parse land area into value and unit
    var landAreaValue by remember {
        val parts = project.landArea.trim().split(" ", limit = 2)
        mutableStateOf(parts.firstOrNull().orEmpty())
    }
    var landAreaUnit by remember {
        val parts = project.landArea.trim().split(" ", limit = 2)
        mutableStateOf(if (parts.size > 1 && parts[1].contains("Square", ignoreCase = true)) "Square Meters" else "Hectares")
    }
    var showLandAreaUnitDropdown by remember { mutableStateOf(false) }
    var numberOfUnits by remember {
        mutableStateOf(project.numberOfUnits.replace(Regex("[^0-9]"), ""))
    }
    var showProjectTypeDropdown by remember { mutableStateOf(false) }
    var isCustomProjectType by remember { mutableStateOf(project.projectType !in listOf("Housing Project", "Community Facility")) }

    // Contract cost is auto-computed from scope items
    var durationText by remember { mutableStateOf(project.contractDurationDays.toString()) }
    var dateStarted by remember { mutableStateOf(project.dateStarted) }

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
            project.completionDateOriginal
        }
    }

    val cs = MaterialTheme.colorScheme
    val nha = LocalNHAColors.current
    val nameError = FormValidation.requiredError(name)
    val contractorError = FormValidation.requiredError(contractor)
    val durationError = daysError(durationText)
    fun snapshot() = listOf(
        name, location, selectedUids.toList(), implementingOffice, contractor, scopeItems.toList(),
        projectType, landAreaValue,
        landAreaUnit, numberOfUnits, durationText, dateStarted,
        projectedBeneficiaries, boardResolution, noticeOfAward, noticeToProceed
    )
    val initial = remember { snapshot() }
    val dirty = snapshot() != initial

    LongForm(
        title = "Edit project",
        dirty = dirty,
        saveEnabled = nameError == null && contractorError == null && durationError == null,
        saveTag = "submit_edit_project",
        onDismiss = onDismiss,
        onSave = {
            val cost = scopeItems.sumOf { it.amount }
            val duration = durationText.trim().toIntOrNull() ?: 365
            val landArea = if (landAreaValue.isBlank()) "" else "${landAreaValue.trim()} $landAreaUnit"
            val scopeOfWorkJson = JSONArray(withAutoWeights(scopeItems).map { JSONObject().put("name", it.name).put("amount", it.amount).put("weightPct", it.weightPct) }).toString()
            onEdit(
                name,
                location,
                implementingOffice,
                contractor,
                scopeOfWorkJson,
                projectType,
                landArea,
                numberOfUnits,
                cost,
                duration,
                dateStarted,
                completionDateOriginal,
                assignedStaff,
                if (canEditAssignment) {
                    selectedUids.map { uid -> registeredUsers.firstOrNull { it.uid == uid } ?: com.example.data.model.RegisteredUser(uid, uid) }
                } else null,
                projectedBeneficiaries.trim(),
                boardResolution.trim(),
                noticeOfAward.trim(),
                noticeToProceed.trim()
            )
        }
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Project Name") },
            modifier = Modifier.fillMaxWidth().testTag("edit_project_name"),
            isError = nameError != null,
            supportingText = nameError?.let { { Text(it) } },
            singleLine = true
        )
        OutlinedTextField(
            value = location,
            onValueChange = { location = it },
            label = { Text("Location") },
            modifier = Modifier.fillMaxWidth(),
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
            modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.weight(1.2f).testTag("edit_land_area"),
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
                modifier = Modifier.fillMaxWidth().testTag("edit_number_of_units"),
                singleLine = true
            )
        }

        // Printed on the Project Profile sheet; left blank when unknown.
        OutlinedTextField(
            value = projectedBeneficiaries,
            onValueChange = { projectedBeneficiaries = it },
            label = { Text("Projected Beneficiaries") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = boardResolution,
            onValueChange = { boardResolution = it },
            label = { Text("Board Resolution / Date") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = noticeOfAward,
            onValueChange = { noticeOfAward = it },
            label = { Text("Notice of Award") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        OutlinedTextField(
            value = noticeToProceed,
            onValueChange = { noticeToProceed = it },
            label = { Text("Notice to Proceed") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

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
        AssignStaffField(
            editable = canAssign,
            usersLoaded = canEditAssignment,
            users = registeredUsers,
            selectedUids = selectedUids,
            assignedStaff = assignedStaff,
            onSelectedChange = { selectedUids = it }
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
