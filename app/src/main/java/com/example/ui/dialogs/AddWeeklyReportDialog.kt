package com.example.ui.dialogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.DailyActivity
import com.example.data.model.EquipmentItem
import com.example.data.model.ManpowerItem
import com.example.data.model.WeeklyIssue
import com.example.ui.theme.*
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.atan2

@Composable
fun AddWeeklyReportDialog(
    project: com.example.data.model.Project?,
    projectId: Long,
    existingReport: com.example.data.model.WeeklyReport? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        reportingWeek: String,
        targetAccomplishmentPct: Double,
        actualAccomplishmentPct: Double,
        activitiesJson: String,
        attachedPhotoUrl: String,
        dailyWeatherMap: Map<String, String>,
        dailyWeatherDates: Map<String, String>,
        manpowerJson: String,
        equipmentJson: String,
        issuesJson: String,
        accomplishmentItemsJson: String
    ) -> Unit
) {
    var reportingWeek by remember { mutableStateOf(existingReport?.reportingWeek ?: "August 3 - August 9, 2026") }
    
    val daysOfWeekWithDates = remember(reportingWeek) {
        val cal = java.util.Calendar.getInstance()
        try {
            var startDateStr = reportingWeek.split(" - ").first()
            // Remove "Week XX: " if present
            startDateStr = startDateStr.replace(Regex("Week \\d+: "), "")
            
            val formats = listOf(
                java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault()),
                java.text.SimpleDateFormat("MMMM d", java.util.Locale.getDefault())
            )
            
            var parsedDate: java.util.Date? = null
            for (f in formats) {
                try {
                    parsedDate = f.parse(startDateStr)
                    if (parsedDate != null) break
                } catch (e: Exception) {}
            }
            
            if (parsedDate != null) {
                cal.time = parsedDate
                if (cal.get(java.util.Calendar.YEAR) == 1970) {
                    cal.set(java.util.Calendar.YEAR, java.util.Calendar.getInstance().get(java.util.Calendar.YEAR))
                }
            } else {
                cal.firstDayOfWeek = java.util.Calendar.MONDAY
                cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            }
        } catch (e: Exception) {
            cal.firstDayOfWeek = java.util.Calendar.MONDAY
            cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
        }
        val sdfDisplay = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.US)
        val sdfDb = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val sdfDayName = java.text.SimpleDateFormat("EEEE", java.util.Locale.US)
        
        (0..6).map {
            val name = sdfDayName.format(cal.time)
            val displayStr = sdfDisplay.format(cal.time)
            val dbStr = sdfDb.format(cal.time)
            cal.add(java.util.Calendar.DAY_OF_MONTH, 1)
            Triple(name, displayStr, dbStr)
        }
    }

            
    val scopeItems = remember { mutableStateListOf<com.example.data.model.ScopeItem>().apply {
        if (project != null && project.scopeOfWork.isNotBlank()) {
            try {
                val arr = JSONArray(project.scopeOfWork)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(com.example.data.model.ScopeItem(obj.getString("name"), obj.optDouble("amount", 0.0), obj.getDouble("weightPct")))
                }
            } catch (e: Exception) {
               add(com.example.data.model.ScopeItem(project.scopeOfWork, 0.0, 100.0))
            }
        }
    } }
    
    // Accomps = Map of Scope Name to Pair<Target, Actual>
    val accomplishmentMap = remember { 
        mutableStateMapOf<String, Pair<String, String>>().apply {
            if (existingReport != null) {
                try {
                    val arr = JSONArray(existingReport.accomplishmentItemsJson)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        put(obj.getString("itemDescription"), Pair(obj.getDouble("targetPct").toString(), obj.getDouble("actualPct").toString()))
                    }
                } catch(e: Exception) {}
            } else {
                scopeItems.forEach { put(it.name, Pair("0.0", "0.0")) }
            }
        }
    }
    
    val overallTarget by remember { androidx.compose.runtime.derivedStateOf { scopeItems.sumOf { (accomplishmentMap[it.name]?.first?.toDoubleOrNull() ?: 0.0) * (it.weightPct / 100.0) } } }
    val overallActual by remember { androidx.compose.runtime.derivedStateOf { scopeItems.sumOf { (accomplishmentMap[it.name]?.second?.toDoubleOrNull() ?: 0.0) * (it.weightPct / 100.0) } } }
    val overallVariance by remember { androidx.compose.runtime.derivedStateOf { overallActual - overallTarget } }
    
    var activityItems by remember {
        mutableStateOf(
            listOf(
                DailyActivity("Monday", "", "")
            )
        )
    }
    val photoUrls = remember { mutableStateListOf<String>().apply {
        if (existingReport != null && existingReport.attachedPhotoUrlsJson.length > 4) {
            try {
                val arr = JSONArray(existingReport.attachedPhotoUrlsJson)
                for (i in 0 until arr.length()) { add(arr.getString(i)) }
            } catch(e: Exception) {}
        }
    } }

    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    // Activity Result Launchers for Gallery and Camera
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            photoUrls.addAll(uris.map { it.toString() })
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            // Sample photo url for preview when captured
            photoUrls.add("https://images.unsplash.com/photo-1541888946425-d0fbb186a5b3?auto=format&fit=crop&w=600&q=80")
        }
    }

    // Daily Weather State for 7 Days of the Week (24 Hours per Day)
    val daysOfWeek = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val dailyHourlyWeather = remember {
        mutableStateMapOf(
            "Monday" to mutableStateListOf(*Array(24) { "FAIR" }),
            "Tuesday" to mutableStateListOf(*Array(24) { "FAIR" }),
            "Wednesday" to mutableStateListOf(*Array(24) { if (it in 13..16) "CLOUDY" else "FAIR" }),
            "Thursday" to mutableStateListOf(*Array(24) { if (it in 14..17) "RAINY" else "FAIR" }),
            "Friday" to mutableStateListOf(*Array(24) { if (it in 12..15) "RAIN_SHOWERS" else "FAIR" }),
            "Saturday" to mutableStateListOf(*Array(24) { "FAIR" }),
            "Sunday" to mutableStateListOf(*Array(24) { "FAIR" })
        )
    }

    var selectedWeatherDayName by remember(daysOfWeekWithDates) { mutableStateOf(daysOfWeekWithDates.first().first) }
    var selectedHourIndex by remember { mutableStateOf(12) }

    // Manpower Table State
    var manpowerItems by remember {
        mutableStateOf(
            listOf(
                ManpowerItem("Project Engineer", 1),
                ManpowerItem("Foreman", 2),
                ManpowerItem("Masons / Carpenters", 10),
                ManpowerItem("Laborers", 8)
            )
        )
    }

    // Equipment Summary Table State
    var equipmentItems by remember {
        mutableStateOf(
            listOf(
                EquipmentItem("Backhoe / Excavator", 1, "Operational"),
                EquipmentItem("Concrete Mixer (1-bagger)", 2, "Operational")
            )
        )
    }

    // Issues Encountered / Action Taken Table State
    var issuesItems by remember {
        mutableStateOf(
            listOf(
                WeeklyIssue("Delay in ready-mix concrete delivery", "Coordinated with backup batching plant")
            )
        )
    }

    val grandTotalManpower = remember(manpowerItems) { manpowerItems.sumOf { it.count } }

    val darkTextFieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = DarkSurfaceVariant,
        unfocusedContainerColor = DarkSurfaceVariant,
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = Color(0xFF94A3B8),
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = DarkBorder,
        cursorColor = Color(0xFF38BDF8)
    )

    AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        titleContentColor = DarkTextPrimary,
        textContentColor = DarkTextPrimary,
        title = {
            Text(
                "SUBMIT NHA WEEKLY ENGINEER'S REPORT",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth().clickable { showDateRangePicker = true }) {
                    OutlinedTextField(
                        value = reportingWeek,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reporting Week Date Range") },
                        colors = darkTextFieldColors,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_weekly_reporting_week"),
                        singleLine = true
                    )
                    // Transparent overlay to intercept clicks
                    Box(modifier = Modifier.matchParentSize().clickable { showDateRangePicker = true })
                }

                if (showDateRangePicker) {
                    DateRangePickerDialog(
                        onDismiss = { showDateRangePicker = false },
                        onDateRangeSelected = { rangeStr ->
                            reportingWeek = rangeStr
                            showDateRangePicker = false
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("ACCOMPLISHMENT STATUS (Scope of Works)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextPrimary))
                
                // Header
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Item", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                    Text("Target %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                    Text("Actual %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                    Text("Var", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.End))
                }

                // Scope Items Rows
                scopeItems.forEach { item ->
                    val vals = accomplishmentMap[item.name] ?: Pair("0.0", "0.0")
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(2f)) {
                            Text(item.name, style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("Wt: ${"%.2f".format(item.weightPct)}%", style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary))
                        }
                        OutlinedTextField(
                            value = vals.first,
                            onValueChange = { accomplishmentMap[item.name] = Pair(it, vals.second) },
                            colors = darkTextFieldColors,
                            modifier = Modifier.weight(1f).height(48.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = vals.second,
                            onValueChange = { accomplishmentMap[item.name] = Pair(vals.first, it) },
                            colors = darkTextFieldColors,
                            modifier = Modifier.weight(1f).height(48.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        val targetVal = vals.first.toDoubleOrNull() ?: 0.0
                        val actualVal = vals.second.toDoubleOrNull() ?: 0.0
                        val variance = actualVal - targetVal
                        val varColor = if (variance >= 0) StatusGreenText else StatusRedText
                        Text(
                            text = "${if(variance>0) "+" else ""}${"%.2f".format(variance)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall.copy(color = varColor, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        )
                    }
                }

                // Grand Total Row
                HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GRAND TOTAL:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Target: ${"%.2f".format(overallTarget)}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        Text("Actual: ${"%.2f".format(overallActual)}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                        val ovColor = if (overallVariance >= 0) StatusGreenText else StatusRedText
                        Text("Var: ${if(overallVariance>0) "+" else ""}${"%.2f".format(overallVariance)}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = ovColor))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))



                // ACTIVITIES OF THE WEEK TABLE
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "ACTIVITIES OF THE WEEK TABLE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            TextButton(
                                onClick = {
                                    activityItems = activityItems + DailyActivity("Monday", "", "")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Row", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }
                        }

                        HorizontalDivider(color = DarkBorder)
                        Spacer(modifier = Modifier.height(4.dp))

                        activityItems.forEachIndexed { idx, item ->
                            key(idx) {
                                ActivityInputRow(
                                    activity = item,
                                    textFieldColors = darkTextFieldColors,
                                    onUpdate = { updated ->
                                        activityItems = activityItems.toMutableList().also { it[idx] = updated }
                                    },
                                    onDelete = {
                                        if (activityItems.size > 1) {
                                            activityItems = activityItems.toMutableList().also { it.removeAt(idx) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 1. MANPOWER TABLE
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "MANPOWER SUMMARY TABLE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            TextButton(
                                onClick = {
                                    manpowerItems = manpowerItems + ManpowerItem("New Designation", 1)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }
                        }

                        HorizontalDivider(color = DarkBorder)
                        Spacer(modifier = Modifier.height(4.dp))

                        manpowerItems.forEachIndexed { idx, item ->
                            key(idx) {
                                ManpowerInputRow(
                                    item = item,
                                    textFieldColors = darkTextFieldColors,
                                    onUpdate = { updated ->
                                        manpowerItems = manpowerItems.toMutableList().also { it[idx] = updated }
                                    },
                                    onDelete = {
                                        if (manpowerItems.size > 1) {
                                            manpowerItems = manpowerItems.toMutableList().also { it.removeAt(idx) }
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GRAND TOTAL WEEKLY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = DarkTextSecondary))
                                Text("$grandTotalManpower Personnel", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8)))
                            }
                        }
                    }
                }

                // 2. EQUIPMENT TABLE
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "EQUIPMENT SUMMARY TABLE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                            TextButton(
                                onClick = {
                                    equipmentItems = equipmentItems + EquipmentItem("New Equipment", 1, "Operational")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF38BDF8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontSize = 12.sp, color = Color(0xFF38BDF8))
                            }
                        }

                        HorizontalDivider(color = DarkBorder)
                        Spacer(modifier = Modifier.height(4.dp))

                        equipmentItems.forEachIndexed { idx, item ->
                            key(idx) {
                                EquipmentInputRow(
                                    item = item,
                                    textFieldColors = darkTextFieldColors,
                                    onUpdate = { updated ->
                                        equipmentItems = equipmentItems.toMutableList().also { it[idx] = updated }
                                    },
                                    onDelete = {
                                        if (equipmentItems.size > 1) {
                                            equipmentItems = equipmentItems.toMutableList().also { it.removeAt(idx) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. ISSUES TABLE
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "ISSUES ENCOUNTERED / ACTION TAKEN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Add issue button directly below the title header
                        OutlinedButton(
                            onClick = {
                                issuesItems = issuesItems + WeeklyIssue("New issue encountered", "Action taken")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_issue_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Issue / Action Taken", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(modifier = Modifier.height(4.dp))

                        if (issuesItems.isEmpty()) {
                            Text("No issues added yet.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                        } else {
                            issuesItems.forEachIndexed { idx, issue ->
                                key(idx) {
                                    IssueInputRow(
                                        issue = issue,
                                        textFieldColors = darkTextFieldColors,
                                        onUpdate = { updated ->
                                            issuesItems = issuesItems.toMutableList().also { it[idx] = updated }
                                        },
                                        onDelete = {
                                            issuesItems = issuesItems.toMutableList().also { it.removeAt(idx) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // SITE PROGRESS PHOTO CARD (CAMERA / GALLERY ACCESS)
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "ATTACH SITE PROGRESS PHOTO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Add Photos Button placed directly below header title
                        OutlinedButton(
                            onClick = { showPhotoSourceDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                            border = BorderStroke(1.dp, Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Photos", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        if (photoUrls.isNotEmpty()) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(photoUrls) { url ->
                                    Box(
                                        modifier = Modifier
                                            .size(120.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(DarkSurface)
                                    ) {
                                        AsyncImage(
                                            model = url,
                                            contentDescription = "Attached site photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        IconButton(
                                            onClick = { photoUrls.remove(url) },
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(6.dp)
                                                .size(28.dp)
                                                .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50.dp))
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove Photo", tint = Color.White, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = DarkSurface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showPhotoSourceDialog = true }
                                    .padding(vertical = 16.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddAPhoto,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Tap to attach site photo (Camera or Gallery)",
                                        style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. DAILY WEATHER LOG BY DAY OF WEEK (INTERACTIVE 24-SLICE PIE GRAPH)
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "DAILY WEATHER LOG (24-HR PIE GRAPH)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    "Click pie slice to cycle: Fair (Grn) → Cloudy (Yel) → Showers (Cyan) → Rainy (Blu) → Stormy (Red) → Reset",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = DarkTextSecondary)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = DarkBorder)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Day Selector Tabs
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(daysOfWeekWithDates) { (dayName, dateStr, _) ->
                                val isSelected = dayName == selectedWeatherDayName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedWeatherDayName = dayName },
                                    label = {
                                        Text(
                                            text = "$dayName - $dateStr",
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    shape = RoundedCornerShape(50.dp),
                                    border = BorderStroke(1.dp, if (isSelected) Color(0xFF38BDF8) else DarkBorder),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF38BDF8),
                                        selectedLabelColor = Color.Black,
                                        containerColor = DarkSurface,
                                        labelColor = DarkTextSecondary
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 24-Slice Interactive Pie Chart Canvas
                        val currentDayHours = dailyHourlyWeather[selectedWeatherDayName] ?: remember { mutableStateListOf(*Array(24) { "FAIR" }) }
                        val activeDateStr = daysOfWeekWithDates.find { it.first == selectedWeatherDayName }?.second ?: "July 24, 2026"
                        val holeSurfaceVariant = DarkSurfaceVariant
                        val holeBorder = DarkBorder

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(270.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .size(250.dp)
                                    .pointerInput(selectedWeatherDayName) {
                                        detectTapGestures { tapOffset ->
                                            val centerX = size.width / 2f
                                            val centerY = size.height / 2f
                                            val dx = tapOffset.x - centerX
                                            val dy = tapOffset.y - centerY
                                            val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                            val minDim = minOf(size.width, size.height).toFloat()
                                            val radius = (minDim - 20f) / 2f

                                            if (distance in (radius * 0.45f)..radius) {
                                                var angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
                                                if (angle < 0) angle += 360f
                                                val adjustedAngle = (angle + 90f) % 360f
                                                val tappedHour = ((adjustedAngle / 360f) * 24).toInt().coerceIn(0, 23)
                                                selectedHourIndex = tappedHour

                                                val curCond = currentDayHours[tappedHour]
                                                val nextCond = when (curCond) {
                                                    "FAIR" -> "CLOUDY"          // 1 click -> 2 clicks
                                                    "CLOUDY" -> "RAIN_SHOWERS"  // 2 clicks -> 3 clicks
                                                    "RAIN_SHOWERS" -> "RAINY"   // 3 clicks -> 4 clicks
                                                    "RAINY" -> "STORMY"        // 4 clicks -> 5 clicks
                                                    else -> "FAIR"             // 5 clicks -> reset to Fair
                                                }
                                                currentDayHours[tappedHour] = nextCond
                                            }
                                        }
                                    }
                            ) {
                                val minDim = minOf(size.width, size.height)
                                val radius = (minDim - 20f) / 2f
                                val canvasSize = radius * 2f
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val sliceAngle = 360f / 24f

                                for (i in 0 until 24) {
                                    val startAngle = -90f + (i * sliceAngle)
                                    val conditionStr = currentDayHours.getOrElse(i) { "FAIR" }
                                    val color = when (conditionStr.uppercase()) {
                                        "FAIR" -> WeatherFairGreen
                                        "CLOUDY" -> WeatherCloudyYellow
                                        "RAIN SHOWERS", "RAIN_SHOWERS" -> WeatherRainShowersCyan
                                        "RAINY" -> WeatherRainyBlue
                                        "STORMY" -> WeatherStormyRed
                                        else -> WeatherFairGreen
                                    }

                                    drawArc(
                                        color = color,
                                        startAngle = startAngle,
                                        sweepAngle = sliceAngle - 1f,
                                        useCenter = true,
                                        size = Size(canvasSize, canvasSize),
                                        topLeft = Offset((size.width - canvasSize) / 2f, (size.height - canvasSize) / 2f)
                                    )
                                }

                                for (i in 0 until 24) {
                                    val angleRad = Math.toRadians((-90.0 + (i * sliceAngle).toDouble()))
                                    val endX = center.x + radius * Math.cos(angleRad).toFloat()
                                    val endY = center.y + radius * Math.sin(angleRad).toFloat()
                                    drawLine(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        start = center,
                                        end = Offset(endX, endY),
                                        strokeWidth = 1.5f
                                    )
                                }

                                // Selected Hour Highlight Ring
                                val selStartAngle = -90f + (selectedHourIndex * sliceAngle)
                                drawArc(
                                    color = Color.White,
                                    startAngle = selStartAngle,
                                    sweepAngle = sliceAngle,
                                    useCenter = false,
                                    style = Stroke(width = 4f),
                                    size = Size(canvasSize, canvasSize),
                                    topLeft = Offset((size.width - canvasSize) / 2f, (size.height - canvasSize) / 2f)
                                )

                                // Cutout Center Hole
                                drawCircle(
                                    color = holeSurfaceVariant,
                                    radius = radius * 0.45f,
                                    center = center
                                )
                                drawCircle(
                                    color = holeBorder,
                                    radius = radius * 0.45f,
                                    center = center,
                                    style = Stroke(width = 2f)
                                )
                            }

                            // Center Info Text
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$selectedWeatherDayName",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
                                )
                                Text(
                                    text = "$activeDateStr",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = Color(0xFF38BDF8))
                                )
                                Text(
                                    text = "Tap slice to log",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = DarkTextSecondary)
                                )
                            }
                        }

                        // Selected Hour Status Readout
                        val activeHourCond = currentDayHours.getOrElse(selectedHourIndex) { "FAIR" }
                        val formattedHourText = if (selectedHourIndex == 0) "12:00 AM (00:00)"
                        else if (selectedHourIndex < 12) "$selectedHourIndex:00 AM"
                        else if (selectedHourIndex == 12) "12:00 PM (12:00)"
                        else "${selectedHourIndex - 12}:00 PM (${selectedHourIndex}:00)"

                        Surface(
                            color = DarkSurface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clip(RoundedCornerShape(50.dp))
                                            .background(
                                                when (activeHourCond) {
                                                    "FAIR" -> WeatherFairGreen
                                                    "CLOUDY" -> WeatherCloudyYellow
                                                    "RAIN_SHOWERS" -> WeatherRainShowersCyan
                                                    "RAINY" -> WeatherRainyBlue
                                                    "STORMY" -> WeatherStormyRed
                                                    else -> WeatherFairGreen
                                                }
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Hour $formattedHourText:",
                                        style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, fontSize = 11.sp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (activeHourCond) {
                                            "RAIN_SHOWERS" -> "RAIN SHOWERS"
                                            else -> activeHourCond
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = Color.White, fontSize = 11.sp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "(Tap slice again to cycle)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF38BDF8), fontSize = 10.sp)
                                )
                            }
                        }

                        // Preset Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = { for (i in 0 until 24) currentDayHours[i] = "FAIR" },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("All Fair", fontSize = 10.sp, color = WeatherFairGreen)
                            }
                            OutlinedButton(
                                onClick = { for (i in 8..17) currentDayHours[i] = "RAINY" },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("Rain (8am-5pm)", fontSize = 10.sp, color = WeatherRainyBlue)
                            }
                            OutlinedButton(
                                onClick = { for (i in 0 until 24) currentDayHours[i] = "CLOUDY" },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("All Cloudy", fontSize = 10.sp, color = WeatherCloudyYellow)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val target = overallTarget
                    val actual = overallActual

                    val manpowerJson = encodeManpowerList(manpowerItems)
                    val equipmentJson = encodeEquipmentList(equipmentItems)
                    val issuesJson = encodeIssuesList(issuesItems)
                    val activitiesJson = encodeActivitiesList(activityItems)

                    val weatherCsvMap = daysOfWeek.associateWith { dayName ->
                        dailyHourlyWeather[dayName]?.joinToString(",") ?: List(24) { "FAIR" }.joinToString(",")
                    }
                    
                    val dailyWeatherDates = daysOfWeekWithDates.associate { it.first to it.third }

                    val accItemsJson = JSONArray(scopeItems.map { item ->
                        val vals = accomplishmentMap[item.name] ?: Pair("0.0", "0.0")
                        JSONObject().put("itemDescription", item.name)
                            .put("targetPct", vals.first.toDoubleOrNull() ?: 0.0)
                            .put("actualPct", vals.second.toDoubleOrNull() ?: 0.0)
                            .put("weightPct", item.weightPct)
                    }).toString()

                    onSubmit(
                        reportingWeek,
                        target,
                        actual,
                        activitiesJson,
                        JSONArray(photoUrls).toString(),
                        weatherCsvMap,
                        dailyWeatherDates,
                        manpowerJson,
                        equipmentJson,
                        issuesJson,
                        accItemsJson
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier.testTag("submit_weekly_report_button")
            ) {
                Text("Submit Weekly Report", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = DarkTextSecondary) }
        }
    )

    if (showPhotoSourceDialog) {
        AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            onDismissRequest = { showPhotoSourceDialog = false },
            containerColor = DarkSurface,
            title = {
                Text(
                    "SELECT SITE PHOTO SOURCE",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = Color.White)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                try {
                                    cameraLauncher.launch(null)
                                } catch (e: Exception) {
                                    photoUrls.add("https://images.unsplash.com/photo-1541888946425-d0fbb186a5b3?auto=format&fit=crop&w=600&q=80")
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Take Photo with Camera", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                Text("Capture real-time construction site photo", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, fontSize = 11.sp))
                            }
                        }
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                try {
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } catch (e: Exception) {
                                    photoUrls.add("https://images.unsplash.com/photo-1504307651254-35680f356dfd?auto=format&fit=crop&w=600&q=80")
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = Color(0xFF38BDF8))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Choose from Phone Gallery", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                Text("Select an existing picture from your device gallery", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, fontSize = 11.sp))
                            }
                        }
                    }

                    Surface(
                        color = DarkSurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                photoUrls.add("https://images.unsplash.com/photo-1541888946425-d0fbb186a5b3?auto=format&fit=crop&w=600&q=80")
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Foundation, contentDescription = null, tint = StatusGreenText)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("NHA Site Inspection Photo", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = Color.White))
                                Text("Use standard NHA site inspection picture", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary, fontSize = 11.sp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text("Cancel", color = DarkTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun ManpowerInputRow(
    item: ManpowerItem,
    textFieldColors: TextFieldColors,
    onUpdate: (ManpowerItem) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = item.designation,
            onValueChange = { newDesignation -> onUpdate(item.copy(designation = newDesignation)) },
            label = { Text("Designation") },
            colors = textFieldColors,
            modifier = Modifier.weight(2f),
            singleLine = true
        )

        OutlinedTextField(
            value = item.count.toString(),
            onValueChange = { newCountText ->
                val cnt = newCountText.toIntOrNull() ?: 0
                onUpdate(item.copy(count = cnt))
            },
            label = { Text("No.") },
            colors = textFieldColors,
            modifier = Modifier.weight(1f),
            singleLine = true
        )

        IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRedText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EquipmentInputRow(
    item: EquipmentItem,
    textFieldColors: TextFieldColors,
    onUpdate: (EquipmentItem) -> Unit,
    onDelete: () -> Unit
) {
    var showRemarksDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = item.description,
                onValueChange = { newDesc -> onUpdate(item.copy(description = newDesc)) },
                label = { Text("Name of Equipment") },
                colors = textFieldColors,
                modifier = Modifier.weight(2f),
                singleLine = true
            )

            OutlinedTextField(
                value = item.count.toString(),
                onValueChange = { newCntText ->
                    val cnt = newCntText.toIntOrNull() ?: 0
                    onUpdate(item.copy(count = cnt))
                },
                label = { Text("No.") },
                colors = textFieldColors,
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRedText, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showRemarksDropdown = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, DarkBorder),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = DarkSurface, contentColor = Color.White)
            ) {
                Text("Remarks / Status: ${item.status}", color = Color.White)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
            }

            DropdownMenu(
                expanded = showRemarksDropdown,
                onDismissRequest = { showRemarksDropdown = false },
                modifier = Modifier.background(DarkSurface)
            ) {
                listOf("Operational", "Idle", "For Repair").forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = Color.White) },
                        onClick = {
                            onUpdate(item.copy(status = opt))
                            showRemarksDropdown = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IssueInputRow(
    issue: WeeklyIssue,
    textFieldColors: TextFieldColors,
    onUpdate: (WeeklyIssue) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = issue.description,
                onValueChange = { newDesc -> onUpdate(issue.copy(description = newDesc)) },
                label = { Text("Issue Encountered") },
                colors = textFieldColors,
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRedText, modifier = Modifier.size(18.dp))
            }
        }

        OutlinedTextField(
            value = issue.actionTaken,
            onValueChange = { newAction -> onUpdate(issue.copy(actionTaken = newAction)) },
            label = { Text("Action Taken") },
            colors = textFieldColors,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun encodeManpowerList(list: List<ManpowerItem>): String {
    val array = JSONArray()
    list.forEach { item ->
        val obj = JSONObject()
        obj.put("designation", item.designation)
        obj.put("count", item.count)
        obj.put("remarks", item.remarks)
        array.put(obj)
    }
    return array.toString()
}

private fun encodeEquipmentList(list: List<EquipmentItem>): String {
    val array = JSONArray()
    list.forEach { item ->
        val obj = JSONObject()
        obj.put("description", item.description)
        obj.put("count", item.count)
        obj.put("status", item.status)
        obj.put("remarks", item.remarks)
        array.put(obj)
    }
    return array.toString()
}

private fun encodeIssuesList(list: List<WeeklyIssue>): String {
    val array = JSONArray()
    list.forEach { item ->
        val obj = JSONObject()
        obj.put("description", item.description)
        obj.put("actionTaken", item.actionTaken)
        obj.put("remarks", item.remarks)
        array.put(obj)
    }
    return array.toString()
}

private fun encodeActivitiesList(list: List<DailyActivity>): String {
    val array = JSONArray()
    list.forEach { item ->
        val obj = JSONObject()
        obj.put("day", item.day)
        obj.put("description", item.description)
        obj.put("remarks", item.remarks)
        array.put(obj)
    }
    return array.toString()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityInputRow(
    activity: DailyActivity,
    textFieldColors: TextFieldColors,
    onUpdate: (DailyActivity) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val days = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
            var expanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = activity.day,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Day of Week") },
                    colors = textFieldColors,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    days.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(day) },
                            onClick = {
                                onUpdate(activity.copy(day = day))
                                expanded = false
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = StatusRedText, modifier = Modifier.size(18.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = activity.description,
                onValueChange = { newDesc -> onUpdate(activity.copy(description = newDesc)) },
                label = { Text("Activities") },
                colors = textFieldColors,
                modifier = Modifier.weight(1.8f)
            )

            OutlinedTextField(
                value = activity.remarks,
                onValueChange = { newRem -> onUpdate(activity.copy(remarks = newRem)) },
                label = { Text("Remarks") },
                colors = textFieldColors,
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}

