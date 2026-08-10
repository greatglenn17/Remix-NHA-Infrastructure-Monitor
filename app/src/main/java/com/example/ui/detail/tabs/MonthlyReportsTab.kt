package com.example.ui.detail.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyReportsTab(
    project: Project,
    monthlyReports: List<MonthlyReport>,
    weeklyReports: List<WeeklyReport>,
    currentUser: UserAccount,
    onAddMonthlyReportClick: () -> Unit,
    onEditMonthlyReportClick: (MonthlyReport) -> Unit,
    onDeleteMonthlyReportClick: (MonthlyReport) -> Unit,
    onSignOffClick: (MonthlyReport, String, String, String) -> Unit
) {
    val availableMonths = remember(monthlyReports, weeklyReports) {
        val monthsFromMonthly = monthlyReports.map { it.reportingMonth }
        val monthsFromWeekly = weeklyReports.mapNotNull { rep ->
            val clean = rep.reportingWeek.replace(Regex("Week \\d+: "), "")
            val yearMatch = Regex("\\b(20\\d{2})\\b").find(clean)?.value ?: "2026"
            val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
            val shortMonthNames = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
            var foundMonth: String? = null
            for (i in monthNames.indices) {
                if (clean.contains(monthNames[i], ignoreCase = true) || clean.contains(shortMonthNames[i], ignoreCase = true)) {
                    foundMonth = "${monthNames[i]} $yearMatch"
                    break
                }
            }
            foundMonth
        }
        (monthsFromMonthly + monthsFromWeekly).distinct().filter { it.isNotBlank() }.ifEmpty { listOf("June 2026") }
    }

    var selectedMonth by remember(availableMonths) {
        mutableStateOf(availableMonths.firstOrNull() ?: "June 2026")
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val monthWeeklyReports = remember(selectedMonth, weeklyReports) {
        val monthStr = selectedMonth.split(" ").firstOrNull() ?: ""
        val yearStr = selectedMonth.split(" ").lastOrNull() ?: ""
        val shortMonth = if (monthStr.length >= 3) monthStr.take(3) else monthStr
        weeklyReports.filter {
            (it.reportingWeek.contains(monthStr, ignoreCase = true) || it.reportingWeek.contains(shortMonth, ignoreCase = true)) &&
            it.reportingWeek.contains(yearStr, ignoreCase = true)
        }.sortedBy { it.id }
    }

    val sumTarget = remember(monthWeeklyReports) { monthWeeklyReports.sumOf { it.targetAccomplishmentPct } }
    val sumActual = remember(monthWeeklyReports) { monthWeeklyReports.sumOf { it.actualAccomplishmentPct } }
    val sumVariance = sumActual - sumTarget

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(bottom = 80.dp)
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NHA MONTHLY PROJECT ENGINEER'S REPORTS",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = it },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedMonth,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
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
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(DarkSurfaceVariant)
                    ) {
                        availableMonths.forEach { month ->
                            DropdownMenuItem(
                                text = { Text(month, color = Color.White) },
                                onClick = {
                                    selectedMonth = month
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        item {
            MonthlyReportSingleTableCard(
                selectedMonth = selectedMonth,
                monthWeeklyReports = monthWeeklyReports,
                sumTarget = sumTarget,
                sumActual = sumActual,
                sumVariance = sumVariance
            )
        }
    }
}

@Composable
private fun MonthlyReportSingleTableCard(
    selectedMonth: String,
    monthWeeklyReports: List<WeeklyReport>,
    sumTarget: Double,
    sumActual: Double,
    sumVariance: Double
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth().testTag("monthly_report_single_table_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with Slippage / Advance right below title
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Monthly Accomplishment Report - $selectedMonth",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = DarkTextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))

                val isAhead = sumVariance >= 0
                Surface(
                    color = if (isAhead) StatusGreenBg else StatusRedBg,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isAhead) "AHEAD (+${"%.2f".format(sumVariance)}%)" else "SLIPPAGE (${"%.2f".format(sumVariance)}%)",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isAhead) StatusGreenText else StatusRedText,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Single Table synced with weekly reports
            Surface(
                color = DarkSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reporting Week", modifier = Modifier.weight(2.2f).padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium.copy(color = DarkTextSecondary, fontWeight = FontWeight.Bold))
                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                        Text("Target %", modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium.copy(color = DarkTextSecondary, fontWeight = FontWeight.Bold))
                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                        Text("Actual %", modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium.copy(color = DarkTextSecondary, fontWeight = FontWeight.Bold))
                        VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                        Text("Variance %", modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium.copy(color = DarkTextSecondary, fontWeight = FontWeight.Bold))
                    }

                    HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))

                    if (monthWeeklyReports.isEmpty()) {
                        Text(
                            text = "No weekly reports found for $selectedMonth.",
                            style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary),
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                        )
                    } else {
                        monthWeeklyReports.forEachIndexed { idx, rep ->
                            val target = rep.targetAccomplishmentPct
                            val actual = rep.actualAccomplishmentPct
                            val varVal = actual - target
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min)
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rep.reportingWeek,
                                    modifier = Modifier.weight(2.2f).padding(horizontal = 6.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Medium)
                                )
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text(
                                    text = "${"%.2f".format(target)}%",
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                                )
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text(
                                    text = "${"%.2f".format(actual)}%",
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                                )
                                VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                                Text(
                                    text = "${if (varVal >= 0) "+" else ""}${"%.2f".format(varVal)}%",
                                    modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (varVal >= 0) StatusGreenText else StatusRedText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 2.dp))
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MONTH TOTAL",
                                modifier = Modifier.weight(2.2f).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                            Text(
                                text = "${"%.2f".format(sumTarget)}%",
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                            Text(
                                text = "${"%.2f".format(sumActual)}%",
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            VerticalDivider(color = DarkBorder, modifier = Modifier.fillMaxHeight())
                            Text(
                                text = "${if (sumVariance >= 0) "+" else ""}${"%.2f".format(sumVariance)}%",
                                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (sumVariance >= 0) StatusGreenText else StatusRedText,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
