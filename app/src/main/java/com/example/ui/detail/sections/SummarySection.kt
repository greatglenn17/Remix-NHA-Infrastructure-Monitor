package com.example.ui.detail.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.HOUSING_BILLING_TYPES
import com.example.data.model.PendingDocument
import com.example.data.model.Permission
import com.example.data.model.Project
import com.example.data.model.ProjectPayment
import com.example.data.model.ProjectStatus
import com.example.ui.dialogs.WeekRange
import com.example.data.model.UserRole
import com.example.data.model.WeeklyReport
import com.example.data.model.netAmount
import com.example.data.model.hasPermission
import com.example.ui.dashboard.SitePhoto
import com.example.ui.detail.tabs.lotProgressFill
import com.example.ui.dashboard.levelColor
import com.example.ui.dashboard.levelIcon
import com.example.ui.dashboard.levelName
import com.example.ui.theme.LocalNHAColors
import com.example.ui.theme.TabularNumbers
import com.example.utils.CurrencyFormatter
import com.example.util.AttentionFlag
import com.example.util.ContractCost
import com.example.util.ProjectSchedule
import com.example.util.SdpSummary
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Summary: attention flags (only when there are any), the master profile, key dates with their orders, contract cost, and the latest weekly report with open issues. */
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
    // Adds a Pending order to Documents: a continuance, or the order a new developer request asks for.
    onAddContinuance: (name: String, remarks: String) -> Unit = { _, _ -> },
    requests: List<com.example.data.model.DeveloperRequest> = emptyList(),
    onSaveRequest: (com.example.data.model.DeveloperRequest) -> Unit = {},
    onDeleteRequest: (com.example.data.model.DeveloperRequest) -> Unit = {},
    letters: List<com.example.data.model.ContractorLetter> = emptyList(),
    onSaveLetter: (com.example.data.model.ContractorLetter) -> Unit = {},
    onDeleteLetter: (com.example.data.model.ContractorLetter) -> Unit = {},
    payments: List<ProjectPayment> = emptyList(),
    today: LocalDate = LocalDate.now(),
    weeklyReports: List<WeeklyReport> = emptyList(),
    onAddWeeklyReport: (() -> Unit)? = null,
    // Housing units by stage from the subdivision plan; null hides the card.
    construction: SdpSummary.Summary? = null,
    // Saves the approved bar chart; null when this account may not change it.
    onSaveBarChart: ((String) -> Unit)? = null
) {
    val missingWeek = WeekRange.containing(today).takeIf { onAddWeeklyReport != null && needsThisWeeksReport(project, weeklyReports, today) }
    val revisedCompletion = remember(documents, project.dateStarted, project.contractDurationDays) {
        ProjectSchedule.revisedCompletion(project.dateStarted, project.contractDurationDays, documents)
    }
    val addedDays = remember(documents) { ProjectSchedule.addedDays(documents) }
    var openOrder by remember { mutableStateOf<PendingDocument?>(null) }
    val canUpdateOrders = currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)
    var showRequestDialog by remember { mutableStateOf(false) }
    var editRequest by remember { mutableStateOf<com.example.data.model.DeveloperRequest?>(null) }
    var showLetterDialog by remember { mutableStateOf(false) }
    var editLetter by remember { mutableStateOf<com.example.data.model.ContractorLetter?>(null) }
    var showBarChart by remember { mutableStateOf(false) }
    // What the engineer does next comes first; the profile is reference, below.
    val cards = listOfNotNull<@Composable (Modifier) -> Unit>(
        { m -> LatestReportCard(latestReport, openIssues, missingWeek, onAddWeeklyReport, m) },
        construction?.let { c -> { m -> ConstructionCard(c, payments, today, m) } },
        { m -> ProfileCard(project, canEditProject, onEditProject, onPrintProfile, m) },
        { m -> BarChartCard(project, documents, today, onSaveBarChart?.let { { showBarChart = true } }, m) },
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
        { m ->
            ContractorLettersCard(
                letters, { editLetter = null; showLetterDialog = true }.takeIf { canUpdateOrders },
                if (canUpdateOrders) { l: com.example.data.model.ContractorLetter -> editLetter = l; showLetterDialog = true } else null, m
            )
        },
        { m -> CostCard(project, documents, m) }
    )
    // Attention shows only when something needs action, across the full width above the cards;
    // the payment table takes the full width below them so its amounts fit.
    CardColumns(
        cards,
        header = if (flags.isEmpty()) null else { { AttentionCard(flags, Modifier.fillMaxWidth()) } },
        footer = { PaymentStatusCard(payments, ContractCost.current(project.scopeOfWork, project.contractCostOriginal, documents), Modifier.fillMaxWidth()) }
    )
    if (showRequestDialog) {
        DeveloperRequestDialog(
            projectId = project.id, existing = editRequest,
            onDismiss = { showRequestDialog = false },
            onSave = { request, order ->
                onSaveRequest(request)
                // The order the developer asks for waits in Documents as Pending.
                order?.let { (name, remarks) -> onAddContinuance(name, remarks) }
                showRequestDialog = false
            },
            onDelete = { onDeleteRequest(it); showRequestDialog = false },
            documents = documents,
            workItems = remember(project.barChartJson) { com.example.util.BarChart.itemNames(project) }
        )
    }
    if (showLetterDialog) {
        ContractorLetterDialog(
            projectId = project.id, existing = editLetter,
            onDismiss = { showLetterDialog = false },
            onSave = { onSaveLetter(it); showLetterDialog = false },
            onDelete = { onDeleteLetter(it); showLetterDialog = false },
            today = today
        )
    }
    OrderDialogs(openOrder, documents, currentUserRole, onUpdateOrderStatus, onDeleteOrder, onAddContinuance) { openOrder = null }
    if (showBarChart && onSaveBarChart != null) {
        BarChartDialog(project, documents, onDismiss = { showBarChart = false }, onSave = { onSaveBarChart(it); showBarChart = false })
    }
}

private val SCHEDULE_ORDER_TYPES = listOf("Time Extension", "Work Suspension", "Work Resumption")

@Composable
private fun AttentionCard(flags: List<AttentionFlag>, modifier: Modifier) {
    val colors = LocalNHAColors.current
    SectionCard("Needs attention", Icons.Default.NotificationImportant, modifier) {
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
    val orders = ProjectSchedule.chained(documents.filter { doc -> SCHEDULE_ORDER_TYPES.any { doc.documentName.contains(it, ignoreCase = true) } })
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
        Field("Date started", ProjectSchedule.displayDate(project.dateStarted.ifBlank { "-" }))
        Field("Original completion", ProjectSchedule.displayDate(project.completionDateOriginal.ifBlank { "-" }))
        // Same date Days left counts down to: approved orders first, else the stored revised date.
        Field("Revised completion", ProjectSchedule.displayDate(revisedCompletion ?: project.completionDateRevised.takeIf { it.isNotBlank() && it != "-" } ?: "-"))
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
            LogEntry(ProjectSchedule.orderLabel(doc.documentName), MaterialTheme.colorScheme.onSurface, listOf("Status: ${doc.status}", ProjectSchedule.withEnd(doc.remarks)), onClick = onOpenOrder?.let { open -> { open(doc) } })
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

/** The monthly report's III. Status of Payment as a table: each billing, then the totals against [contractCost]. */
@Composable
private fun PaymentStatusCard(payments: List<ProjectPayment>, contractCost: Double, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    SectionCard("Status of payment", Icons.Default.ReceiptLong, modifier.testTag("summary_payment_status")) {
        if (payments.isEmpty()) {
            EmptyLine("No billings yet")
            return@SectionCard
        }
        BoxWithConstraints {
            // On a phone the date moves under the billing name so the amounts fit on one line.
            val wide = maxWidth >= 480.dp
            val weights = if (wide) listOf(1.8f, 1.1f, 1.4f, 0.7f, 1.5f) else listOf(1.6f, 1.5f, 0.8f, 1.6f)
            @Composable
            fun Cells(values: List<String>, bold: Boolean = false, color: androidx.compose.ui.graphics.Color = scheme.onSurface) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    values.forEachIndexed { i, v ->
                        Text(
                            v, style = MaterialTheme.typography.bodySmall, color = color,
                            fontWeight = if (bold) FontWeight.SemiBold else null,
                            // Amounts and percentages line up on the right.
                            textAlign = if (i >= values.size - 3) androidx.compose.ui.text.style.TextAlign.End else null,
                            modifier = Modifier.weight(weights[i])
                        )
                    }
                }
            }
            fun row(first: String, date: String, vararg amounts: String) =
                if (wide) listOf(first, date) + amounts else listOf(listOf(first, date).filter { it.isNotBlank() }.joinToString("\n")) + amounts
            Column {
                Cells(row("Billing / period covered", "Date", "Net amount (PHP)", "%", "Balance (PHP)"), bold = true, color = scheme.onSurfaceVariant)
                HorizontalDivider(color = scheme.outlineVariant)
                payments.forEach { p ->
                    Cells(
                        row(
                            listOf(p.name, p.periodCovered).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "-" },
                            ProjectSchedule.displayDate(p.date.ifBlank { "-" }),
                            CurrencyFormatter.formatDecimal(p.netAmount),
                            "%.2f%%".format(p.percentage),
                            CurrencyFormatter.formatDecimal(p.balanceAmount)
                        )
                    )
                    HorizontalDivider(color = scheme.outlineVariant)
                }
                // Share and balance stay on the gross; only the paid amount drops the retention.
                val total = payments.sumOf { it.grossAmount }
                Cells(
                    row(
                        "Total", "",
                        CurrencyFormatter.formatDecimal(payments.sumOf { it.netAmount }),
                        if (contractCost > 0) "%.2f%%".format(total / contractCost * 100) else "-",
                        CurrencyFormatter.formatDecimal(contractCost - total)
                    ),
                    bold = true
                )
            }
        }
    }
}

@Composable
private fun LatestReportCard(report: WeeklyReport?, openIssues: Int, missingWeek: String?, onAddWeeklyReport: (() -> Unit)?, modifier: Modifier) {
    SectionCard("Latest weekly report", Icons.Default.Assignment, modifier) {
        if (missingWeek != null && onAddWeeklyReport != null) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)) {
                    Text("No report yet for this week", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text(missingWeek, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    TextButton(onClick = onAddWeeklyReport, contentPadding = PaddingValues(horizontal = 0.dp), modifier = Modifier.testTag("summary_add_weekly_report")) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add weekly report")
                    }
                }
            }
        }
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

/**
 * Saleable units of the subdivision plan by progress band, counted and coloured as the Status of Construction print,
 * then the Status of Billing: the lots billed so far for each housing billing type, from the billings' unit counts.
 */
@Composable
private fun ConstructionCard(summary: SdpSummary.Summary, payments: List<ProjectPayment>, today: LocalDate, modifier: Modifier) {
    val nha = LocalNHAColors.current
    // The first % of each band picks its colour on the lot map.
    val colors = listOf(0, 1, 26, 51, 76, 100).map { lotProgressFill(it, nha) }
    SectionCard("Status of construction", Icons.Default.HomeWork, modifier.testTag("summary_construction")) {
        Text("As of ${ProjectSchedule.displayDate(today)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp).height(10.dp).clip(RoundedCornerShape(5.dp))) {
            summary.saleableBands.forEachIndexed { i, units ->
                if (units > 0) Box(Modifier.weight(units.toFloat()).fillMaxHeight().background(colors[i]))
            }
        }
        SdpSummary.BANDS.forEachIndexed { i, band ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(colors[i]))
                Spacer(Modifier.width(8.dp))
                Field(band, "${summary.saleableBands[i]}", Modifier.weight(1f))
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(vertical = 4.dp))
        // Lined up with the band names and counts above.
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, top = 2.dp).testTag("construction_grand_total")) {
            Text("Grand total", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text("${summary.saleable.count} units", style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        BillingStatus(payments, summary.saleable.count)
    }
}

/** Lots billed per housing billing type, and their share of the [total] saleable units. */
@Composable
private fun BillingStatus(payments: List<ProjectPayment>, total: Int) {
    val scheme = MaterialTheme.colorScheme
    @Composable
    fun Cells(values: List<String>, bold: Boolean = false, color: androidx.compose.ui.graphics.Color = scheme.onSurface) {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEachIndexed { i, v ->
                Text(
                    v, style = MaterialTheme.typography.bodySmall.merge(TabularNumbers), color = color,
                    fontWeight = if (bold) FontWeight.SemiBold else null,
                    textAlign = if (i > 0) androidx.compose.ui.text.style.TextAlign.End else null,
                    modifier = Modifier.weight(if (i == 0) 2f else 1f)
                )
            }
        }
    }
    Text(
        "Status of billing", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).testTag("summary_billing_status")
    )
    Cells(listOf("Billed for", "Lots billed", "% of $total"), bold = true, color = scheme.onSurfaceVariant)
    HorizontalDivider(color = scheme.outlineVariant)
    HOUSING_BILLING_TYPES.forEach { type ->
        val units = payments.filter { it.billingType == type }.sumOf { it.units }
        // M1 bills 80% of a housing unit and M2 the last 20%.
        val label = type + when { type.startsWith("M1") -> " (80%)"; type.startsWith("M2") -> " (20%)"; else -> "" }
        Cells(listOf(label, "$units", if (total > 0) "%.2f%%".format(units * 100.0 / total) else "-"))
        HorizontalDivider(color = scheme.outlineVariant)
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
                Surface(color = scheme.surfaceVariant, shape = RoundedCornerShape(6.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("${index + 1}. $name", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                        Field("Amount · weight", "${CurrencyFormatter.formatPhp(amount)} · ${"%.2f".format(weight)}%")
                    }
                }
            }
        }
    }
}

/**
 * True when a started, unfinished, not suspended project has no report for the Saturday-to-Friday week of [today].
 * A report counts if its range overlaps that week; older labels that don't parse never count.
 */
internal fun needsThisWeeksReport(project: Project, reports: List<WeeklyReport>, today: LocalDate): Boolean {
    if (project.actualAccomplishment >= 100 || project.status == ProjectStatus.SUSPENDED.label) return false
    if (runCatching { LocalDate.parse(project.dateStarted.trim()) }.getOrNull()?.isAfter(today) == true) return false
    val (start, end) = WeekRange.parse(WeekRange.containing(today)) ?: return false
    return reports.none { r -> WeekRange.parse(r.reportingWeek)?.let { (s, e) -> !s.isAfter(end) && !e.isBefore(start) } == true }
}

/** Scope of works JSON as (name, amount, weight %); plain text becomes one item at 100%. */
internal fun scopeItems(json: String): List<Triple<String, Double, Double>> = try {
    val items = org.json.JSONArray(json)
    (0 until items.length()).map { i ->
        val obj = items.getJSONObject(i)
        Triple(obj.optString("name", ""), obj.optDouble("amount", 0.0), obj.optDouble("weightPct", 0.0))
    }
} catch (e: Exception) {
    listOf(Triple(json, 0.0, 100.0))
}.filter { it.first.isNotBlank() }
