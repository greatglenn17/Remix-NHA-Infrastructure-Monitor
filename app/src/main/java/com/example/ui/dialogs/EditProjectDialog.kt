package com.example.ui.dialogs
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.CalendarToday
import java.util.Calendar
import com.example.data.model.Project
import com.example.data.model.ProjectType
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProjectDialog(
    project: Project,
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
        assignedStaff: String
    ) -> Unit
) {
    var name by remember { mutableStateOf(project.name) }
    var location by remember { mutableStateOf(project.location) }
    var assignedStaff by remember { mutableStateOf(project.assignedStaff) }
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
    var currentScopeItem by remember { mutableStateOf("") }
    var currentScopeAmount by remember { mutableStateOf("") }
    var currentScopeWeight by remember { mutableStateOf("") }
    
    var projectType by remember { mutableStateOf(project.projectType) }
    // Parse land area into value and unit
    var landAreaValue by remember {
        val parts = (project.landArea.ifBlank { "4.5 Hectares" }).split(" ", limit = 2)
        mutableStateOf(parts.firstOrNull() ?: "4.5")
    }
    var landAreaUnit by remember {
        val parts = (project.landArea.ifBlank { "4.5 Hectares" }).split(" ", limit = 2)
        mutableStateOf(if (parts.size > 1 && parts[1].contains("Square", ignoreCase = true)) "Square Meters" else "Hectares")
    }
    var showLandAreaUnitDropdown by remember { mutableStateOf(false) }
    var numberOfUnits by remember {
        val raw = project.numberOfUnits.ifBlank { "120" }
        mutableStateOf(raw.replace(Regex("[^0-9]"), "").ifBlank { "120" })
    }
    var showProjectTypeDropdown by remember { mutableStateOf(false) }
    var isCustomProjectType by remember { mutableStateOf(project.projectType !in listOf("Housing Project", "Community Facility")) }

    // Contract cost is auto-computed from scope items
    var durationText by remember { mutableStateOf(project.contractDurationDays.toString()) }
    var dateStarted by remember { mutableStateOf(project.dateStarted) }

    val completionDateOriginal = remember(dateStarted, durationText) {
        try {
            val start = LocalDate.parse(dateStarted)
            val duration = durationText.toIntOrNull() ?: 0
            if (duration > 0) {
                start.plusDays((duration - 1).toLong()).toString()
            } else {
                start.toString()
            }
        } catch (e: Exception) {
            project.completionDateOriginal
        }
    }

    val context = LocalContext.current

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        try {
            val parsed = LocalDate.parse(dateStarted)
            cal.set(parsed.year, parsed.monthValue - 1, parsed.dayOfMonth)
        } catch (e: Exception) { }

        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val formatted = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)
                dateStarted = formatted
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    val darkTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = Color(0xFF94A3B8),
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = DarkBorder,
        cursorColor = Color(0xFF38BDF8)
    )

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding().systemBarsPadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text("Edit Project", color = Color.White) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project Name") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth().testTag("edit_project_name"),
                    singleLine = true
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = implementingOffice,
                    onValueChange = { implementingOffice = it },
                    label = { Text("Implementing Office") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = contractor,
                    onValueChange = { contractor = it },
                    label = { Text("Contractor Name") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = "Scope of Works",
                        style = MaterialTheme.typography.titleSmall.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                    )
                    Button(
                        onClick = {
                            val amount = currentScopeAmount.replace(",", "").toDoubleOrNull() ?: 0.0
                            val weight = currentScopeWeight.toDoubleOrNull() ?: 0.0
                            if (currentScopeItem.isNotBlank() && weight > 0) {
                                scopeItems.add(com.example.data.model.ScopeItem(currentScopeItem, amount, weight))
                                currentScopeItem = ""
                                currentScopeAmount = ""
                                currentScopeWeight = ""
                            }
                        },
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                    ) {
                        Text("Add Scope of Works", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = currentScopeItem,
                        onValueChange = { currentScopeItem = it },
                        label = { Text("Item", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        colors = darkTextFieldColors,
                        modifier = Modifier.weight(1.8f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = currentScopeAmount,
                        onValueChange = { currentScopeAmount = it },
                        label = { Text("Amt", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = darkTextFieldColors,
                        modifier = Modifier.weight(1.1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = currentScopeWeight,
                        onValueChange = { currentScopeWeight = it },
                        label = { Text("Wt(%)", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = darkTextFieldColors,
                        modifier = Modifier.weight(1.1f),
                        singleLine = true
                    )
                }
                if (scopeItems.isNotEmpty()) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            scopeItems.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(
                                        "- ${item.name}",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1.8f)
                                    )
                                    Text(
                                        com.example.utils.CurrencyFormatter.formatPhp(item.amount),
                                        color = Color(0xFF38BDF8),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                    Text(
                                        "${item.weightPct}%",
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodySmall,
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.weight(1.1f)
                                    )
                                }
                            }
                            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                            val totalWeight = scopeItems.sumOf { it.weightPct }
                            val totalAmount = scopeItems.sumOf { it.amount }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Text(
                                    "Total",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1.8f)
                                )
                                Text(
                                    com.example.utils.CurrencyFormatter.formatPhp(totalAmount),
                                    color = Color(0xFF38BDF8),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1.1f)
                                )
                                Text(
                                    "$totalWeight%",
                                    color = if (totalWeight == 100.0) WeatherFairGreen else StatusRedText,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.End,
                                    modifier = Modifier.weight(1.1f)
                                )
                            }
                            TextButton(onClick = { scopeItems.clear() }) {
                                Text("Clear All", color = StatusRedText)
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { showProjectTypeDropdown = true },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant, contentColor = Color.White)
                    ) {
                        Text("Project Type: ${if (isCustomProjectType) "Custom" else projectType}", color = Color.White)
                    }
                    DropdownMenu(
                        expanded = showProjectTypeDropdown,
                        onDismissRequest = { showProjectTypeDropdown = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        listOf("Housing Project", "Community Facility", "Custom...").forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type, color = Color.White) },
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
                        colors = darkTextFieldColors,
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
                            colors = darkTextFieldColors,
                            modifier = Modifier.weight(1.2f).testTag("edit_land_area"),
                            singleLine = true
                        )
                        Box(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                            OutlinedButton(
                                onClick = { showLandAreaUnitDropdown = true },
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, DarkBorder),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurfaceVariant, contentColor = Color.White),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                ) {
                                    Text(landAreaUnit, color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Unit Dropdown", tint = Color(0xFF38BDF8))
                                }
                            }
                            DropdownMenu(
                                expanded = showLandAreaUnitDropdown,
                                onDismissRequest = { showLandAreaUnitDropdown = false },
                                modifier = Modifier.background(DarkSurface)
                            ) {
                                listOf("Hectares", "Square Meters").forEach { unit ->
                                    DropdownMenuItem(
                                        text = { Text(unit, color = Color.White) },
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
                        colors = darkTextFieldColors,
                        modifier = Modifier.fillMaxWidth().testTag("edit_number_of_units"),
                        singleLine = true
                    )
                }

                // Auto-computed Original Contract Cost from Scope of Work amounts
                val autoComputedCost = scopeItems.sumOf { it.amount }
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Original Contract Cost (Auto-Computed)", style = MaterialTheme.typography.labelSmall.copy(color = Color(0xFF94A3B8)))
                        Text(
                            text = com.example.utils.CurrencyFormatter.formatPhp(autoComputedCost),
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White, fontWeight = FontWeight.Bold)
                        )
                    }
                }
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it },
                    label = { Text("Contract Duration (Days)") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = assignedStaff,
                    onValueChange = { assignedStaff = it },
                    label = { Text("Assigned Field Staff / Manager") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = dateStarted,
                            onValueChange = { },
                            label = { Text("Date Started") },
                            colors = darkTextFieldColors,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { openDatePicker() }) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = "Select Date Started",
                                        tint = Color(0xFF38BDF8)
                                    )
                                }
                            }
                        )
                        Surface(
                            color = Color.Transparent,
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { openDatePicker() }
                        ) {}
                    }
                    
                    OutlinedTextField(
                        value = completionDateOriginal,
                        onValueChange = { },
                        label = { Text("Completion Date") },
                        colors = darkTextFieldColors,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        readOnly = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cost = scopeItems.sumOf { it.amount }
                    val duration = durationText.toIntOrNull() ?: 365
                    val landArea = "$landAreaValue $landAreaUnit"
                    if (name.isNotBlank() && contractor.isNotBlank()) {
                        val scopeOfWorkJson = JSONArray(scopeItems.map { JSONObject().put("name", it.name).put("amount", it.amount).put("weightPct", it.weightPct) }).toString()
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
                            assignedStaff
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.testTag("submit_edit_project")
            ) {
                Text("Save Changes", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DarkTextSecondary) }
        }
    )
}
