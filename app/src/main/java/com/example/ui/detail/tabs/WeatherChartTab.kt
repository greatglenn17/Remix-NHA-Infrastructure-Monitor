package com.example.ui.detail.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DailyHourlyWeather
import com.example.data.model.WeatherCondition
import com.example.data.model.WeeklyReport
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class WeekDayInfo(
    val dayIndex: Int,
    val dayName: String,
    val displayDate: String,
    val dbDateStr: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherChartTab(
    dailyWeatherLogs: List<DailyHourlyWeather>,
    weeklyReports: List<WeeklyReport> = emptyList()
) {
    if (dailyWeatherLogs.isEmpty() && weeklyReports.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.WbSunny,
                    contentDescription = null,
                    tint = DarkTextSecondary,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Weather Logs Recorded Yet",
                    style = MaterialTheme.typography.titleMedium.copy(color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Field staff can submit daily 24h weather logs when submitting weekly reports.",
                    style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary)
                )
            }
        }
        return
    }

    var selectedViewMode by remember { mutableStateOf(if (weeklyReports.isNotEmpty()) 0 else 1) } // 0 = By Weekly Report, 1 = By Month

    // Weekly Report selection state
    var selectedReportIndex by remember(weeklyReports) { mutableIntStateOf(0) }
    var weeklyReportDropdownExpanded by remember { mutableStateOf(false) }

    // Monthly selection state
    val availableMonths = remember(dailyWeatherLogs) {
        val months = dailyWeatherLogs.map { it.date.substring(0, 7) }.distinct().sortedDescending()
        if (months.isEmpty()) listOf("2026-06", "2026-05") else months
    }
    var selectedMonth by remember(availableMonths) {
        mutableStateOf(availableMonths.firstOrNull() ?: "2026-06")
    }
    var monthDropdownExpanded by remember { mutableStateOf(false) }

    val textMeasurer = rememberTextMeasurer()

    fun parseHourlyConditions(log: DailyHourlyWeather?): List<WeatherCondition?> {
        if (log == null) return List(24) { null }
        val parts = log.hourlyConditionsCsv.split(",")
        return List(24) { idx ->
            val name = parts.getOrNull(idx)?.trim() ?: "FAIR"
            runCatching { WeatherCondition.valueOf(name) }.getOrDefault(WeatherCondition.FAIR)
        }
    }

    fun parseWeeklyReportDates(reportingWeek: String): List<WeekDayInfo> {
        val cal = Calendar.getInstance()
        try {
            val cleanWeek = reportingWeek.replace(Regex("Week \\d+: "), "")
            val parts = cleanWeek.split(" - ")
            var startStr = parts.firstOrNull()?.trim() ?: ""
            val endStr = parts.getOrNull(1)?.trim() ?: ""

            val yearRegex = Regex("\\b(20\\d{2})\\b")
            val year = yearRegex.find(startStr)?.value ?: yearRegex.find(endStr)?.value ?: "2026"

            if (!startStr.contains(year)) {
                startStr = "$startStr, $year"
            }

            val formats = listOf(
                SimpleDateFormat("MMM d, yyyy", Locale.US),
                SimpleDateFormat("MMMM d, yyyy", Locale.US),
                SimpleDateFormat("yyyy-MM-dd", Locale.US)
            )

            var parsedDate: Date? = null
            for (f in formats) {
                try {
                    parsedDate = f.parse(startStr)
                    if (parsedDate != null) break
                } catch (_: Exception) {}
            }

            if (parsedDate != null) {
                cal.time = parsedDate
            } else {
                cal.set(2026, Calendar.JUNE, 3)
            }
        } catch (_: Exception) {
            cal.set(2026, Calendar.JUNE, 3)
        }

        val sdfDb = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val sdfDisplay = SimpleDateFormat("MMM dd", Locale.US)
        val sdfDayName = SimpleDateFormat("EEE", Locale.US)

        return (1..7).map { idx ->
            val name = sdfDayName.format(cal.time)
            val displayStr = sdfDisplay.format(cal.time)
            val dbStr = sdfDb.format(cal.time)
            val info = WeekDayInfo(
                dayIndex = idx,
                dayName = name,
                displayDate = displayStr,
                dbDateStr = dbStr
            )
            cal.add(Calendar.DAY_OF_MONTH, 1)
            info
        }
    }

    fun formatMonth(yyyyMM: String): String {
        val parts = yyyyMM.split("-")
        if (parts.size != 2) return yyyyMM
        val year = parts[0]
        val monthStr = parts[1]
        val monthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val m = monthStr.toIntOrNull() ?: return yyyyMM
        if (m in 1..12) {
            return "${monthNames[m - 1]}-$year"
        }
        return yyyyMM
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "24-HOUR WEATHER LOGS",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = DarkTextPrimary,
                letterSpacing = 1.5.sp
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))

        // View Mode Selector (Tabs)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedViewMode == 0,
                onClick = { selectedViewMode = 0 },
                label = { Text("Weekly Report Range") },
                leadingIcon = { Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF38BDF8),
                    selectedLabelColor = Color.Black,
                    containerColor = DarkSurfaceVariant,
                    labelColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedViewMode == 1,
                onClick = { selectedViewMode = 1 },
                label = { Text("Monthly View") },
                leadingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(16.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Color(0xFF38BDF8),
                    selectedLabelColor = Color.Black,
                    containerColor = DarkSurfaceVariant,
                    labelColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedViewMode == 0) {
            // Weekly Report Selector & Range Weather Display
            if (weeklyReports.isEmpty()) {
                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                ) {
                    Text(
                        text = "No weekly reports submitted yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = DarkTextSecondary),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                val currentReport = weeklyReports.getOrNull(selectedReportIndex) ?: weeklyReports.first()

                // Weekly Report Dropdown
                ExposedDropdownMenuBox(
                    expanded = weeklyReportDropdownExpanded,
                    onExpandedChange = { weeklyReportDropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = "Report: ${currentReport.reportingWeek}",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = weeklyReportDropdownExpanded) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF38BDF8),
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = DarkSurfaceVariant,
                            unfocusedContainerColor = DarkSurfaceVariant
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    ExposedDropdownMenu(
                        expanded = weeklyReportDropdownExpanded,
                        onDismissRequest = { weeklyReportDropdownExpanded = false },
                        modifier = Modifier.background(DarkSurfaceVariant)
                    ) {
                        weeklyReports.forEachIndexed { idx, rep ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Week ${weeklyReports.size - idx}: ${rep.reportingWeek}", color = Color.White, fontWeight = FontWeight.Bold)
                                        Text("Target: ${rep.targetAccomplishmentPct}% | Actual: ${rep.actualAccomplishmentPct}%", color = DarkTextSecondary, fontSize = 11.sp)
                                    }
                                },
                                onClick = {
                                    selectedReportIndex = idx
                                    weeklyReportDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Parse 7 days for the selected weekly report
                val weekDays = remember(currentReport.reportingWeek) {
                    parseWeeklyReportDates(currentReport.reportingWeek)
                }

                Surface(
                    color = DarkSurface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "WEEKLY REPORT WEATHER MAPPING",
                                style = MaterialTheme.typography.labelLarge.copy(color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "7 Days",
                                style = MaterialTheme.typography.labelSmall.copy(color = DarkTextSecondary)
                            )
                        }
                        Text(
                            text = "Reporting Period: ${currentReport.reportingWeek}",
                            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextPrimary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 7 Weather Clocks Grid (Mapped specifically to the 7 dates of the weekly report)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceVariant, shape = RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Display 7 days in 2 rows (4 on first row, 3 on second row)
                    val rows = listOf(weekDays.take(4), weekDays.drop(4))
                    for (rowDays in rows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (dayInfo in rowDays) {
                                val log = remember(dailyWeatherLogs, currentReport.id, dayInfo.dbDateStr) {
                                    dailyWeatherLogs.find {
                                        (it.weeklyReportId == currentReport.id && it.date == dayInfo.dbDateStr) || it.date == dayInfo.dbDateStr
                                    }
                                }
                                val conditions = parseHourlyConditions(log)

                                Surface(
                                    color = DarkBackground,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.85f)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "${dayInfo.dayName}, ${dayInfo.displayDate}",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp
                                            ),
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            WeatherClock(
                                                dayText = dayInfo.dbDateStr.split("-").lastOrNull() ?: dayInfo.dayIndex.toString(),
                                                conditions = conditions,
                                                textMeasurer = textMeasurer
                                            )
                                        }
                                    }
                                }
                            }
                            // Fill remaining slots in last row if needed
                            if (rowDays.size < 4) {
                                for (i in 0 until (4 - rowDays.size)) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Monthly View
            ExposedDropdownMenuBox(
                expanded = monthDropdownExpanded,
                onExpandedChange = { monthDropdownExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = formatMonth(selectedMonth),
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthDropdownExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant
                    ),
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                )
                ExposedDropdownMenu(
                    expanded = monthDropdownExpanded,
                    onDismissRequest = { monthDropdownExpanded = false },
                    modifier = Modifier.background(DarkSurfaceVariant)
                ) {
                    availableMonths.forEach { month ->
                        DropdownMenuItem(
                            text = { Text(formatMonth(month), color = Color.White) },
                            onClick = {
                                selectedMonth = month
                                monthDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val daysInMonth = remember(selectedMonth) {
                val parts = selectedMonth.split("-")
                if (parts.size == 2) {
                    val year = parts[0].toIntOrNull() ?: 2026
                    val month = parts[1].toIntOrNull() ?: 1
                    when (month) {
                        2 -> if (year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)) 29 else 28
                        4, 6, 9, 11 -> 30
                        else -> 31
                    }
                } else 31
            }

            val filteredLogs = remember(dailyWeatherLogs, selectedMonth) {
                dailyWeatherLogs.filter { it.date.startsWith(selectedMonth) }
            }

            val itemsPerRow = 4
            val rows = (daysInMonth + itemsPerRow - 1) / itemsPerRow

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurfaceVariant, shape = RoundedCornerShape(8.dp))
                    .padding(1.dp)
            ) {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (c in 0 until itemsPerRow) {
                            val day = r * itemsPerRow + c + 1
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(DarkBackground)
                                    .border(0.5.dp, Color.White.copy(alpha = 0.5f))
                                    .padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (day <= daysInMonth) {
                                    val dateStr = "%s-%02d".format(selectedMonth, day)
                                    val log = filteredLogs.find { it.date == dateStr }
                                    val conditions = parseHourlyConditions(log)

                                    WeatherClock(
                                        dayText = day.toString(),
                                        conditions = conditions,
                                        textMeasurer = textMeasurer
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Legend Items Bar
        Surface(
            color = DarkSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, DarkBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                LegendItem("Fair", WeatherFairGreen)
                LegendItem("Cloudy", WeatherCloudyYellow)
                LegendItem("Showers", WeatherRainShowersCyan)
                LegendItem("Rainy", WeatherRainyBlue)
                LegendItem("Stormy", WeatherStormyRed)
            }
        }
        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
private fun WeatherClock(
    dayText: String,
    conditions: List<WeatherCondition?>,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val canvasBg = DarkBackground
    val canvasBorder = DarkBorder
    val canvasTextSec = DarkTextSecondary
    val canvasTextPri = DarkTextPrimary

    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasSize = size.minDimension
        val center = Offset(size.width / 2f, size.height / 2f)

        val radius = canvasSize / 2f * 0.75f
        val innerRadius = radius * 0.6f
        val centerRadius = radius * 0.25f

        fun getColor(idx: Int): Color {
            val cond = conditions.getOrNull(idx)
            return when (cond) {
                WeatherCondition.FAIR -> WeatherFairGreen
                WeatherCondition.CLOUDY -> WeatherCloudyYellow
                WeatherCondition.RAIN_SHOWERS -> WeatherRainShowersCyan
                WeatherCondition.RAINY -> WeatherRainyBlue
                WeatherCondition.STORMY -> WeatherStormyRed
                null -> Color.Transparent
            }
        }

        // Draw Outer Annulus Slices (Hours 12 to 23 -> PM)
        for (i in 0..11) {
            val startAngle = -90f + (i * 30f)
            drawArc(
                color = getColor(i + 12),
                startAngle = startAngle,
                sweepAngle = 30f,
                useCenter = true,
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
        }

        // Draw Inner Annulus Slices (Hours 0 to 11 -> AM)
        for (i in 0..11) {
            val startAngle = -90f + (i * 30f)
            drawArc(
                color = getColor(i),
                startAngle = startAngle,
                sweepAngle = 30f,
                useCenter = true,
                size = Size(innerRadius * 2, innerRadius * 2),
                topLeft = Offset(center.x - innerRadius, center.y - innerRadius)
            )
        }

        // Center Circle to clear out center and place day number
        drawCircle(color = canvasBg, radius = centerRadius, center = center)

        // Draw outlines
        drawCircle(color = canvasBorder, radius = radius, center = center, style = Stroke(1f))
        drawCircle(color = canvasBorder, radius = innerRadius, center = center, style = Stroke(1f))
        drawCircle(color = canvasBorder, radius = centerRadius, center = center, style = Stroke(1f))

        // Draw 12 dividing lines
        for (i in 0..11) {
            val angleRad = Math.toRadians((-90.0 + i * 30.0))
            val startX = center.x + centerRadius * Math.cos(angleRad).toFloat()
            val startY = center.y + centerRadius * Math.sin(angleRad).toFloat()
            val endX = center.x + radius * Math.cos(angleRad).toFloat()
            val endY = center.y + radius * Math.sin(angleRad).toFloat()
            drawLine(color = canvasBorder, start = Offset(startX, startY), end = Offset(endX, endY), strokeWidth = 1f)
        }

        // Draw numbers outside
        for (i in 0..11) {
            val hour = if (i == 0) 12 else i
            val angleRad = Math.toRadians((-90.0 + i * 30.0))
            val textOffset = radius + 10.dp.toPx()
            val x = center.x + textOffset * Math.cos(angleRad).toFloat()
            val y = center.y + textOffset * Math.sin(angleRad).toFloat()

            val layoutResult = textMeasurer.measure(
                text = hour.toString(),
                style = TextStyle(color = canvasTextSec, fontSize = 8.sp, fontWeight = FontWeight.Normal)
            )
            drawText(
                textLayoutResult = layoutResult,
                topLeft = Offset(x - layoutResult.size.width / 2f, y - layoutResult.size.height / 2f)
            )
        }

        // Draw Day Number in center
        val dayResult = textMeasurer.measure(
            text = dayText,
            style = TextStyle(color = canvasTextPri, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
        drawText(
            textLayoutResult = dayResult,
            topLeft = Offset(center.x - dayResult.size.width / 2f, center.y - dayResult.size.height / 2f)
        )
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, shape = RoundedCornerShape(3.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkTextPrimary)
    }
}
