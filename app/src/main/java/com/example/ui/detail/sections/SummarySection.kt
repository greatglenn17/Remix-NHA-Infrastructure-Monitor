package com.example.ui.detail.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PendingDocument
import com.example.data.model.Permission
import com.example.data.model.Project
import com.example.data.model.UserRole
import com.example.data.model.WeeklyReport
import com.example.data.model.hasPermission
import com.example.ui.dashboard.SitePhoto
import com.example.ui.dashboard.levelColor
import com.example.ui.dashboard.levelIcon
import com.example.ui.dashboard.levelName
import com.example.ui.theme.LocalNHAColors
import com.example.utils.CurrencyFormatter
import com.example.util.AttentionFlag
import com.example.util.ContractCost
import com.example.util.ProjectSchedule
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Summary: attention flags, the master profile, key dates with their orders, contract cost, and the latest weekly report with open issues. */
@Composable
fun SummarySection(
    project: Project,
    flags: List<AttentionFlag>,
    documents: List<PendingDocument>,
    latestReport: WeeklyReport?,
    openIssues: Int,
    canEditProject: Boolean,
    onEditProject: () -> Unit,
    currentUserRole: UserRole,
    onAddOrder: () -> Unit,
    onUpdateOrderStatus: (PendingDocument, String, String) -> Unit,
    onDeleteOrder: (Long) -> Unit,
    onPrintProfile: () -> Unit = {},
    onAddContinuance: (name: String, remarks: String) -> Unit = { _, _ -> },
    requests: List<com.example.data.model.DeveloperRequest> = emptyList(),
    onSaveRequest: (com.example.data.model.DeveloperRequest) -> Unit = {},
    onDeleteRequest: (com.example.data.model.DeveloperRequest) -> Unit = {},
    today: LocalDate = LocalDate.now()
) {
    val revisedCompletion = remember(documents, project.dateStarted, project.contractDurationDays) {
        ProjectSchedule.revisedCompletion(project.dateStarted, project.contractDurationDays, documents)
    }
    val addedDays = remember(documents) { ProjectSchedule.addedDays(documents) }
    var openOrder by remember { mutableStateOf<PendingDocument?>(null) }
    val canUpdateOrders = currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)
    var showRequestDialog by remember { mutableStateOf(false) }
    var editRequest by remember { mutableStateOf<com.example.data.model.DeveloperRequest?>(null) }
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> AttentionCard(flags, m) },
        { m -> ProfileCard(project, canEditProject, onEditProject, onPrintProfile, m) },
        { m ->
            KeyDatesCard(
                project, revisedCompletion, addedDays, today, documents,
                onAddOrder.takeIf { canUpdateOrders }, if (canUpdateOrders) { order: PendingDocument -> openOrder = order } else null, m
            )
        },
        { m ->
            DeveloperRequestsCard(
                requests, today, { editRequest = null; showRequestDialog = true }.takeIf { canUpdateOrders },
                if (canUpdateOrders) { r: com.example.data.model.DeveloperRequest -> editRequest = r; showRequestDialog = true } else null, m
            )
        },
        { m -> CostCard(project, documents, m) },
        { m -> LatestReportCard(latestReport, openIssues, m) }
    )
    CardColumns(cards)
    if (showRequestDialog) {
        DeveloperRequestDialog(
            projectId = project.id, existing = editRequest,
            onDismiss = { showRequestDialog = false },
            onSave = { onSaveRequest(it); showRequestDialog = false },
            onDelete = { onDeleteRequest(it); showRequestDialog = false }
        )
    }
    var continueFrom by remember { mutableStateOf<PendingDocument?>(null) }
    openOrder?.let { order ->
        OrderStatusDialog(
            order, currentUserRole, onUpdateOrderStatus, onDeleteOrder,
            onAddContinuance = { continueFrom = order }.takeIf { ProjectSchedule.isSuspension(order) }
        ) { openOrder = null }
    }
    continueFrom?.let { ws ->
        val (name, startsOn) = remember(ws, documents) { ProjectSchedule.nextContinuance(ws, documents) }
        ContinuanceDialog(name, startsOn, onDismiss = { continueFrom = null }) { days, effectivity ->
            onAddContinuance(name, ProjectSchedule.suspensionRemarks(days, effectivity))
            continueFrom = null
        }
    }
}

private val SCHEDULE_ORDER_TYPES = listOf("Time Extension", "Work Suspension", "Work Resumption")

@Composable
private fun AttentionCard(flags: List<AttentionFlag>, modifier: Modifier) {
    val colors = LocalNHAColors.current
    SectionCard("Needs attention", Icons.Default.NotificationImportant, modifier) {
        if (flags.isEmpty()) EmptyLine("Nothing needs attention")
        flags.forEach { flag ->
            val color = levelColor(flag.level, colors)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Icon(levelIcon(flag.level), contentDescription = levelName(flag.level), tint = color, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(flag.message, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
            }
        }
    }
}

@Composable
private fun KeyDatesCard(
    project: Project,
    revisedCompletion: String?,
    addedDays: Int,
    today: LocalDate,
    documents: List<PendingDocument>,
    onAddOrder: (() -> Unit)?,
    onOpenOrder: ((PendingDocument) -> Unit)?,
    modifier: Modifier
) {
    val end = revisedCompletion ?: ProjectSchedule.effectiveCompletion(project.completionDateOriginal, project.completionDateRevised)
    val daysLeft = runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(end.trim())) }.getOrNull()
    val orders = documents.filter { doc -> SCHEDULE_ORDER_TYPES.any { doc.documentName.contains(it, ignoreCase = true) } }
    SectionCard(
        "Key dates",
        Icons.Default.Event,
        modifier,
        action = onAddOrder?.let { add ->
            {
                TextButton(onClick = add, modifier = Modifier.testTag("add_schedule_order_button")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add order")
                }
            }
        }
    ) {
        Field("Date started", project.dateStarted.ifBlank { "-" })
        Field("Original completion", project.completionDateOriginal.ifBlank { "-" })
        // Same date Days left counts down to: approved orders first, else the stored revised date.
        Field("Revised completion", revisedCompletion ?: project.completionDateRevised.takeIf { it.isNotBlank() && it != "-" } ?: "-")
        Field("Contract duration", "${CurrencyFormatter.formatNumber(project.contractDurationDays)} days")
        Field("Revised duration", ProjectSchedule.revisedDurationDays(project.dateStarted, project.contractDurationDays, addedDays, project.completionDateRevised, project.completionDateOriginal)?.let { "${CurrencyFormatter.formatNumber(it)} days" } ?: "-")
        Field(
            "Days left",
            when {
                project.actualAccomplishment >= 100 -> "Completed"
                daysLeft == null -> "-"
                daysLeft < 0 -> "Overdue by ${-daysLeft} days"
                else -> "$daysLeft days"
            }
        )
        // Only Approved orders move the dates above; tap one to change its status.
        Text("Time extensions & suspensions", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        if (orders.isEmpty()) EmptyLine("No time extension or suspension orders.")
        orders.forEach { doc ->
            LogEntry(doc.documentName, MaterialTheme.colorScheme.onSurface, listOf("Status: ${doc.status}", doc.remarks), onClick = onOpenOrder?.let { open -> { open(doc) } })
        }
    }
}

@Composable
private fun CostCard(project: Project, documents: List<PendingDocument>, modifier: Modifier) {
    val original = remember(project.scopeOfWork, project.contractCostOriginal) {
        ContractCost.original(project.scopeOfWork, project.contractCostOriginal)
    }
    val current = remember(project.scopeOfWork, project.contractCostOriginal, documents) {
        ContractCost.current(project.scopeOfWork, project.contractCostOriginal, documents)
    }
    SectionCard("Contract cost", Icons.Default.Payments, modifier) {
        Field("Original", CurrencyFormatter.formatPhp(original))
        Field("Current", CurrencyFormatter.formatPhp(current))
    }
}

@Composable
private fun LatestReportCard(report: WeeklyReport?, openIssues: Int, modifier: Modifier) {
    SectionCard("Latest weekly report", Icons.Default.Assignment, modifier) {
        if (report == null) {
            EmptyLine("No weekly reports yet")
        } else {
            Text(report.reportingWeek, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Field("Target", "%.2f%%".format(report.targetAccomplishmentPct))
            Field("Actual", "%.2f%%".format(report.actualAccomplishmentPct))
            Field("Submitted by", report.submittedByStaff.ifBlank { "-" })
        }
        Field("Open issues", openIssues.toString())
    }
}

@Composable
private fun ProfileCard(project: Project, canEditProject: Boolean, onEditProject: () -> Unit, onPrintProfile: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(
        "Project profile",
        Icons.Default.Info,
        modifier,
        action = {
            Row {
                IconButton(onClick = onPrintProfile, modifier = Modifier.testTag("print_profile_button")) {
                    Icon(Icons.Default.Print, contentDescription = "Print Project Profile", tint = scheme.onSurfaceVariant)
                }
                if (canEditProject) IconButton(onClick = onEditProject, modifier = Modifier.testTag("edit_project_button")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Project", tint = scheme.onSurfaceVariant)
                }
            }
        }
    ) {
        val hasPhoto = project.latestUpdatePhotoUrl.isNotBlank()
        SitePhoto(
            project.latestUpdatePhotoUrl,
            Modifier.fillMaxWidth().then(if (hasPhoto) Modifier.aspectRatio(16f / 9f) else Modifier.height(64.dp))
                .padding(bottom = 8.dp).testTag("profile_site_photo")
        )
        Field("Implementing office", project.implementingOffice.ifBlank { "-" })
        Field("Contractor", project.contractor.ifBlank { "-" })
        Field("Project type", project.projectType.ifBlank { "-" })
        if (project.projectType.contains("Housing", ignoreCase = true)) {
            Field("Land area", project.landArea.ifBlank { "-" })
            Field("No. of units", project.numberOfUnits.ifBlank { "-" })
        }
        val scope = remember(project.scopeOfWork) { scopeItems(project.scopeOfWork) }
        if (scope.isNotEmpty()) {
            Text("Scope of works", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            scope.forEachIndexed { index, (name, amount, weight) ->
                Surface(color = scheme.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("${index + 1}. $name", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Field("Amount · weight", "${CurrencyFormatter.formatPhp(amount)} · $weight%")
                    }
                }
            }
        }
    }
}

/** Scope of works JSON as (name, amount, weight %); plain text becomes one item at 100%. */
private fun scopeItems(json: String): List<Triple<String, Double, Double>> = try {
    val items = org.json.JSONArray(json)
    (0 until items.length()).map { i ->
        val obj = items.getJSONObject(i)
        Triple(obj.optString("name", ""), obj.optDouble("amount", 0.0), obj.optDouble("weightPct", 0.0))
    }
} catch (e: Exception) {
    listOf(Triple(json, 0.0, 100.0))
}.filter { it.first.isNotBlank() }

/** A Continuance of Work Suspension Order: more days on a logged suspension, starting where it ends. */
@Composable
private fun ContinuanceDialog(name: String, startsOn: String, onDismiss: () -> Unit, onSave: (days: Int, effectivity: String) -> Unit) {
    var days by remember { mutableStateOf("") }
    var effectivity by remember { mutableStateOf(startsOn) }
    val dayCount = days.trim().toIntOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Continuance of Work Suspension") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = days, onValueChange = { days = it.filter(Char::isDigit) }, label = { Text("No. of Days") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                com.example.ui.components.DateField(label = "Effectivity Date", value = effectivity, onValueChange = { effectivity = it })
                Text(
                    "Saved as Pending. Like the suspension, it adds no days to the contract; an approved Time Extension does.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = { dayCount?.let { onSave(it, effectivity) } }, enabled = dayCount != null && effectivity.isNotBlank(), modifier = Modifier.testTag("save_continuance_button")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
