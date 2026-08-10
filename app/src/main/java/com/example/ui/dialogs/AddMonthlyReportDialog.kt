package com.example.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.data.model.PaymentEntry
import com.example.data.model.WeeklyReport
import org.json.JSONArray
import org.json.JSONObject

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
                "SUBMIT MONTHLY REPORT",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = reportingMonth,
                    onValueChange = { reportingMonth = it },
                    label = { Text("Reporting Month") },
                    colors = darkTextFieldColors,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Read-only Weekly Summary Table
                Surface(
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Auto-generated Monthly Summary", fontWeight = FontWeight.Bold, color = Color.White)
                        Text("Values derived from Weekly Reports in this month.", style = MaterialTheme.typography.bodySmall.copy(color = DarkTextSecondary))
                        
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Week", modifier = Modifier.weight(2f), style = MaterialTheme.typography.labelMedium.copy(color = DarkTextSecondary))
                            Text("Target %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(color = DarkTextSecondary))
                            Text("Actual %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(color = DarkTextSecondary))
                            Text("Var %", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium.copy(color = DarkTextSecondary))
                        }
                        
                        monthWeeklyReports.forEach { rep ->
                            val target = rep.targetAccomplishmentPct
                            val actual = rep.actualAccomplishmentPct
                            val varVal = actual - target
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(rep.reportingWeek, modifier = Modifier.weight(2f), style = MaterialTheme.typography.bodySmall.copy(color = Color.White))
                                Text("${"%.2f".format(target)}%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(color = Color.White))
                                Text("${"%.2f".format(actual)}%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(color = Color.White))
                                Text("${"%.2f".format(varVal)}%", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall.copy(color = if (varVal >= 0) StatusGreenText else StatusRedText))
                            }
                        }
                        
                        val varTotal = sumActual - sumTarget
                        HorizontalDivider(color = DarkBorder, modifier = Modifier.padding(vertical = 4.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("GRAND TOTAL", modifier = Modifier.weight(2f), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Text("${"%.2f".format(sumTarget)}%", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Text("${"%.2f".format(sumActual)}%", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                            Text("${"%.2f".format(varTotal)}%", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, color = if (varTotal >= 0) StatusGreenText else StatusRedText, fontSize = 12.sp)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = workableDays, onValueChange = { workableDays = it },
                        label = { Text("Workable Days") }, colors = darkTextFieldColors, modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = unworkableDays, onValueChange = { unworkableDays = it },
                        label = { Text("Unworkable Days") }, colors = darkTextFieldColors, modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
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
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                shape = RoundedCornerShape(50.dp)
            ) {
                Text("Submit Monthly Report", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Color(0xFF94A3B8)) }
        }
    )
}
