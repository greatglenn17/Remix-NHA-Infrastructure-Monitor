package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.LongForm
import com.example.ui.theme.LocalNHAColors
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.model.WeeklyReport

@Composable
fun AddMonthlyReportDialog(
    project: com.example.data.model.Project?,
    weeklyReports: List<WeeklyReport>,
    scopeOfWork: String,
    baseCost: Double,
    existingReport: com.example.data.model.MonthlyReport? = null,
    onDismiss: () -> Unit,
    onSubmit: (
        reportingMonth: String,
        scopeWeightPct: Double,
        scopeTargetPct: Double,
        scopeActualPct: Double,
        unworkableDaysCount: Int,
        workableDaysCount: Int,
        recommendations: String,
        paymentsJson: String,
        accomplishmentItemsJson: String
    ) -> Unit
) {
    var reportingMonth by remember { mutableStateOf(existingReport?.reportingMonth ?: "August 2026") }
    
    // Auto-calculate from Weekly Reports
    val monthStr = reportingMonth.split(" ").firstOrNull() ?: ""
    val yearStr = reportingMonth.split(" ").lastOrNull() ?: ""

    val monthWeeklyReports = remember(reportingMonth, weeklyReports) {
        val shortMonth = if (monthStr.length >= 3) monthStr.take(3) else monthStr
        weeklyReports.filter { 
            (it.reportingWeek.contains(monthStr, ignoreCase = true) || it.reportingWeek.contains(shortMonth, ignoreCase = true)) && 
            it.reportingWeek.contains(yearStr, ignoreCase = true)
        }.sortedBy { it.id }
    }

    val sumTarget = remember(monthWeeklyReports) { monthWeeklyReports.sumOf { it.targetAccomplishmentPct } }
    val sumActual = remember(monthWeeklyReports) { monthWeeklyReports.sumOf { it.actualAccomplishmentPct } }

    var unworkableDays by remember { mutableStateOf(existingReport?.unworkableDaysCount?.toString() ?: "2") }
    var workableDays by remember { mutableStateOf(existingReport?.workableDaysCount?.toString() ?: "29") }
    var recommendations by remember { mutableStateOf(existingReport?.recommendations ?: "Field inspection indicates steady progress.") }
    

    fun snapshot() = listOf(reportingMonth, unworkableDays, workableDays, recommendations)
    val initial = remember { snapshot() }
    val dirty = snapshot() != initial
    val cs = MaterialTheme.colorScheme
    val nha = LocalNHAColors.current

    LongForm(
        title = if (existingReport == null) "New monthly report" else "Edit monthly report",
        dirty = dirty,
        saveEnabled = true,
        saveTag = "submit_monthly_report",
        onDismiss = onDismiss,
        onSave = {
            onSubmit(
                reportingMonth,
                100.0, // overallWeight (not used anymore but required by submit)
                sumTarget,
                sumActual,
                unworkableDays.toIntOrNull() ?: 0,
                workableDays.toIntOrNull() ?: 0,
                recommendations,
                "[]", // paymentsJson
                "[]" // accomplishmentItemsJson is replaced by the auto-generated table
            )
        }
    ) {
        OutlinedTextField(
            value = reportingMonth,
            onValueChange = { reportingMonth = it },
            label = { Text("Reporting Month") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Read-only Weekly Summary Table
        Surface(
            color = cs.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, cs.outline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Auto-generated Monthly Summary", fontWeight = FontWeight.Bold, color = cs.onSurface)
                Text("Values derived from Weekly Reports in this month.", style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurfaceVariant))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Week", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant))
                    Text("Target %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant))
                    Text("Actual %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant))
                    Text("Var %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(color = cs.onSurfaceVariant))
                }
                
                monthWeeklyReports.forEach { rep ->
                    val target = rep.targetAccomplishmentPct
                    val actual = rep.actualAccomplishmentPct
                    val varVal = actual - target
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(rep.reportingWeek, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurface))
                        Text("${"%.2f".format(target)}%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurface))
                        Text("${"%.2f".format(actual)}%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(color = cs.onSurface))
                        Text("${"%.2f".format(varVal)}%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(color = if (varVal >= 0) nha.onTrack else nha.behind))
                    }
                }
                
                val varTotal = sumActual - sumTarget
                HorizontalDivider(color = cs.outline, modifier = Modifier.padding(vertical = 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GRAND TOTAL", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = cs.onSurface, fontSize = 12.sp)
                    Text("${"%.2f".format(sumTarget)}%", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = cs.onSurface, fontSize = 12.sp)
                    Text("${"%.2f".format(sumActual)}%", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = cs.onSurface, fontSize = 12.sp)
                    Text("${"%.2f".format(varTotal)}%", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = if (varTotal >= 0) nha.onTrack else nha.behind, fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = workableDays, onValueChange = { workableDays = it },
                label = { Text("Workable Days") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = unworkableDays, onValueChange = { unworkableDays = it },
                label = { Text("Unworkable Days") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f)
            )
        }
    }
}
