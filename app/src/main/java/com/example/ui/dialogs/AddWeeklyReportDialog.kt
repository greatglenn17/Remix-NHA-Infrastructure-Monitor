package com.example.ui.dialogs

import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.files.Attachments
import kotlinx.coroutines.launch
import com.example.data.model.DailyActivity
import com.example.data.model.EquipmentItem
import com.example.data.model.ManpowerItem
import com.example.data.model.WeeklyIssue
import com.example.ui.detail.tabs.parseActivitiesList
import com.example.ui.detail.tabs.parseEquipmentList
import com.example.ui.detail.tabs.parseIssuesList
import com.example.ui.detail.tabs.parseManpowerList
import com.example.ui.components.LongForm
import com.example.ui.theme.LocalNHAColors
import com.example.ui.theme.WeatherCloudyYellow
import com.example.ui.theme.WeatherFairGreen
import com.example.ui.theme.WeatherRainShowersCyan
import com.example.ui.theme.WeatherRainyBlue
import com.example.ui.theme.WeatherStormyRed
import com.example.util.FormValidation
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
    var reportingWeek by remember { mutableStateOf(existingReport?.reportingWeek ?: WeekRange.containing(java.time.LocalDate.now())) }
    
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
    
    // When editing, start from the report's saved rows instead of blank defaults.
    var activityItems by remember {
        mutableStateOf(
            existingReport?.let { parseActivitiesList(it.activitiesJson) }?.takeIf { it.isNotEmpty() }
                ?: listOf(DailyActivity("Monday", "", ""))
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

    // Picked and captured photos are copied into the app (shrunk) and attached by reference.
    val context = androidx.compose.ui.platform.LocalContext.current
    val photoScope = rememberCoroutineScope()
    val projectUuid = com.example.ui.components.LocalProjectUuid.current
    fun attachFailed(e: Exception) {
        android.widget.Toast.makeText(context, "Photo not attached: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        photoScope.launch {
            for (uri in uris) {
                try {
                    photoUrls.add(Attachments.import(context, uri, Attachments.Kind.PHOTO, projectUuid))
                } catch (e: Exception) {
                    attachFailed(e)
                }
            }
        }
    }

    var pendingCameraFile by remember { mutableStateOf<java.io.File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val file = pendingCameraFile ?: return@rememberLauncherForActivityResult
        pendingCameraFile = null
        if (!saved) {
            file.delete()
            return@rememberLauncherForActivityResult
        }
        photoScope.launch {
            try {
                photoUrls.add(Attachments.importCameraPhoto(context, file, projectUuid))
            } catch (e: Exception) {
                attachFailed(e)
            }
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

    // Manpower Table State: the usual designations with zero counts, so no sample
    // headcount is ever submitted as if it were real.
    var manpowerItems by remember {
        mutableStateOf(
            existingReport?.let { parseManpowerList(it.manpowerJson) }?.takeIf { it.isNotEmpty() }
                ?: listOf(
                    ManpowerItem("Project Engineer", 0),
                    ManpowerItem("Foreman", 0),
                    ManpowerItem("Masons / Carpenters", 0),
                    ManpowerItem("Laborers", 0)
                )
        )
    }

    // Equipment Summary Table State
    var equipmentItems by remember {
        mutableStateOf(existingReport?.let { parseEquipmentList(it.equipmentJson) } ?: emptyList())
    }

    // Issues Encountered / Action Taken Table State
    var issuesItems by remember {
        mutableStateOf(existingReport?.let { parseIssuesList(it.issuesJson) } ?: emptyList())
    }

    val grandTotalManpower = remember(manpowerItems) { manpowerItems.sumOf { it.count } }

    var step by remember { mutableStateOf(WeeklyStep.WEEK) }
    val errors = weeklyErrors(accomplishmentMap)
    fun snapshot() = listOf(
        reportingWeek, accomplishmentMap.toMap(), activityItems, photoUrls.toList(),
        dailyHourlyWeather.mapValues { it.value.toList() }, manpowerItems, equipmentItems, issuesItems
    )
    val initial = remember { snapshot() }
    val dirty = snapshot() != initial

    LongForm(
        title = if (existingReport == null) "New weekly report" else "Edit weekly report",
        dirty = dirty,
        saveEnabled = errors.isEmpty(),
        onDismiss = onDismiss,
        saveTag = "submit_weekly_report_button",
        topContent = {
            ScrollableTabRow(selectedTabIndex = step.ordinal, edgePadding = 16.dp) {
                WeeklyStep.entries.forEach { s ->
                    Tab(selected = s == step, onClick = { step = s }, text = { Text(s.label) })
                }
            }
        },
        bottomStart = {
            TextButton(onClick = { step = WeeklyStep.entries[step.ordinal - 1] }, enabled = step.ordinal > 0) { Text("Back") }
            TextButton(onClick = { step = WeeklyStep.entries[step.ordinal + 1] }, enabled = step != WeeklyStep.REVIEW) { Text("Next") }
        },
        onSave = {
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
        }
    ) {
        when (step) {
            WeeklyStep.WEEK -> {
                Box(modifier = Modifier.fillMaxWidth().clickable { showDateRangePicker = true }) {
                    OutlinedTextField(
                        value = reportingWeek,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Reporting Week Date Range") },
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
                Text("ACCOMPLISHMENT STATUS (Scope of Works)", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                
                // Header
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Item", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Text("Target %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Text("Actual %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Text("Var", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.End))
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
                            Text(item.name, style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("Wt: ${"%.2f".format(item.weightPct)}%", style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                        OutlinedTextField(
                            value = vals.first,
                            onValueChange = { accomplishmentMap[item.name] = Pair(it, vals.second) },
                            modifier = Modifier.weight(1f),
                            isError = FormValidation.percentError(vals.first) != null,
                            supportingText = FormValidation.percentError(vals.first)?.let { { Text(it) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = vals.second,
                            onValueChange = { accomplishmentMap[item.name] = Pair(vals.first, it) },
                            modifier = Modifier.weight(1f),
                            isError = FormValidation.percentError(vals.second) != null,
                            supportingText = FormValidation.percentError(vals.second)?.let { { Text(it) } },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        val targetVal = vals.first.toDoubleOrNull() ?: 0.0
                        val actualVal = vals.second.toDoubleOrNull() ?: 0.0
                        val variance = actualVal - targetVal
                        val varColor = if (variance >= 0) LocalNHAColors.current.onTrack else LocalNHAColors.current.behind
                        Text(
                            text = "${if(variance>0) "+" else ""}${"%.2f".format(variance)}",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall.copy(color = varColor, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.End)
                        )
                    }
                }

                // Grand Total Row
                HorizontalDivider(color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GRAND TOTAL:", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Target: ${"%.2f".format(overallTarget)}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                        Text("Actual: ${"%.2f".format(overallActual)}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                        val ovColor = if (overallVariance >= 0) LocalNHAColors.current.onTrack else LocalNHAColors.current.behind
                        Text("Var: ${if(overallVariance>0) "+" else ""}${"%.2f".format(overallVariance)}%", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = ovColor))
                    }
                }
                // 4. DAILY WEATHER LOG BY DAY OF WEEK (INTERACTIVE 24-SLICE PIE GRAPH)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    "Click pie slice to cycle: Fair (Grn) → Cloudy (Yel) → Showers (Cyan) → Rainy (Blu) → Stormy (Red) → Reset",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
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
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 24-Slice Interactive Pie Chart Canvas
                        val currentDayHours = dailyHourlyWeather[selectedWeatherDayName] ?: remember { mutableStateListOf(*Array(24) { "FAIR" }) }
                        val activeDateStr = daysOfWeekWithDates.find { it.first == selectedWeatherDayName }?.second ?: "July 24, 2026"
                        val holeSurfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                        val holeBorder = MaterialTheme.colorScheme.outline
                        val sliceLine = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                        val selectedRing = MaterialTheme.colorScheme.onSurface

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
                                        color = sliceLine,
                                        start = center,
                                        end = Offset(endX, endY),
                                        strokeWidth = 1.5f
                                    )
                                }

                                // Selected Hour Highlight Ring
                                val selStartAngle = -90f + (selectedHourIndex * sliceAngle)
                                drawArc(
                                    color = selectedRing,
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
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                )
                                Text(
                                    text = "$activeDateStr",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                )
                                Text(
                                    text = "Tap slice to log",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = when (activeHourCond) {
                                            "RAIN_SHOWERS" -> "RAIN SHOWERS"
                                            else -> activeHourCond
                                        },
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = "(Tap slice again to cycle)",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.primary, fontSize = 10.sp)
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
            WeeklyStep.CREW -> {
                // 1. MANPOWER TABLE
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            TextButton(
                                onClick = {
                                    manpowerItems = manpowerItems + ManpowerItem("New Designation", 1)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))

                        manpowerItems.forEachIndexed { idx, item ->
                            key(idx) {
                                ManpowerInputRow(
                                    item = item,
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
                            color = MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GRAND TOTAL WEEKLY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant))
                                Text("$grandTotalManpower Personnel", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                            }
                        }
                    }
                }
                // 2. EQUIPMENT TABLE
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            TextButton(
                                onClick = {
                                    equipmentItems = equipmentItems + EquipmentItem("New Equipment", 1, "Operational")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))

                        equipmentItems.forEachIndexed { idx, item ->
                            key(idx) {
                                EquipmentInputRow(
                                    item = item,
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
            }
            WeeklyStep.WORK -> {
                // ACTIVITIES OF THE WEEK TABLE
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                            TextButton(
                                onClick = {
                                    activityItems = activityItems + DailyActivity("Monday", "", "")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Row", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))

                        activityItems.forEachIndexed { idx, item ->
                            key(idx) {
                                ActivityInputRow(
                                    activity = item,
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
                // 3. ISSUES TABLE
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            "ISSUES ENCOUNTERED / ACTION TAKEN",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Add issue button directly below the title header
                        OutlinedButton(
                            onClick = {
                                issuesItems = issuesItems + WeeklyIssue("New issue encountered", "Action taken")
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
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
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Spacer(modifier = Modifier.height(4.dp))

                        if (issuesItems.isEmpty()) {
                            Text("No issues added yet.", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                        } else {
                            issuesItems.forEachIndexed { idx, issue ->
                                key(idx) {
                                    IssueInputRow(
                                        issue = issue,
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
            }
            WeeklyStep.PHOTOS -> {
                // SITE PROGRESS PHOTO CARD (CAMERA / GALLERY ACCESS)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "ATTACH SITE PROGRESS PHOTO",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Add Photos Button placed directly below header title
                        OutlinedButton(
                            onClick = { showPhotoSourceDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
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
                                            .background(MaterialTheme.colorScheme.surface)
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
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove Photo", tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.6f), shape = CircleShape).padding(6.dp).size(16.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            Surface(
                                color = MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
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
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Tap to attach site photo (Camera or Gallery)",
                                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            WeeklyStep.REVIEW -> {
                listOf(
                    "Reporting week" to reportingWeek,
                    "Overall target" to "%.2f%%".format(overallTarget),
                    "Overall actual" to "%.2f%%".format(overallActual),
                    "Variance" to "${if (overallVariance > 0) "+" else ""}${"%.2f".format(overallVariance)}%",
                    "Manpower" to "$grandTotalManpower personnel",
                    "Equipment" to "${equipmentItems.size}",
                    "Activities" to "${activityItems.size}",
                    "Issues" to "${issuesItems.size}",
                    "Photos" to "${photoUrls.size}"
                ).forEach { (label, value) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                    }
                }
                errors.forEach { (errStep, message) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { step = errStep },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        Text(errStep.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
        modifier = Modifier.fillMaxWidth().padding(16.dp).imePadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            onDismissRequest = { showPhotoSourceDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    "SELECT SITE PHOTO SOURCE",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                try {
                                    val (file, uri) = Attachments.newCameraFile(context)
                                    pendingCameraFile = file
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    attachFailed(e)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Take Photo with Camera", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                                Text("Capture real-time construction site photo", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showPhotoSourceDialog = false
                                try {
                                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                } catch (e: Exception) {
                                    attachFailed(e)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Choose from Phone Gallery", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface))
                                Text("Select an existing picture from your device gallery", style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp))
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun ManpowerInputRow(
    item: ManpowerItem,
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
            modifier = Modifier.weight(1f),
            singleLine = true
        )

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LocalNHAColors.current.behind, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EquipmentInputRow(
    item: EquipmentItem,
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
                modifier = Modifier.weight(1f),
                singleLine = true
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LocalNHAColors.current.behind, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showRemarksDropdown = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface)
            ) {
                Text("Remarks / Status: ${item.status}", color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }

            DropdownMenu(
                expanded = showRemarksDropdown,
                onDismissRequest = { showRemarksDropdown = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                listOf("Operational", "Idle", "For Repair").forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, color = MaterialTheme.colorScheme.onSurface) },
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
                modifier = Modifier.weight(1f)
            )

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LocalNHAColors.current.behind, modifier = Modifier.size(18.dp))
            }
        }

        OutlinedTextField(
            value = issue.actionTaken,
            onValueChange = { newAction -> onUpdate(issue.copy(actionTaken = newAction)) },
            label = { Text("Action Taken") },
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

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = LocalNHAColors.current.behind, modifier = Modifier.size(18.dp))
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
                modifier = Modifier.weight(1.8f)
            )

            OutlinedTextField(
                value = activity.remarks,
                onValueChange = { newRem -> onUpdate(activity.copy(remarks = newRem)) },
                label = { Text("Remarks") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
        }
    }
}

