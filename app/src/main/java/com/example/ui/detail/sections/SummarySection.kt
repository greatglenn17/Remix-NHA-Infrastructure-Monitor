package com.example.ui.detail.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.WeeklyReport
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

/** Summary: attention flags, key dates, contract cost, latest weekly report, open issues and the master profile. */
@Composable
fun SummarySection(
    project: Project,
    flags: List<AttentionFlag>,
    documents: List<PendingDocument>,
    latestReport: WeeklyReport?,
    openIssues: Int,
    canEditProject: Boolean,
    onEditProject: () -> Unit,
    today: LocalDate = LocalDate.now()
) {
    val revisedCompletion = remember(documents, project.dateStarted, project.contractDurationDays) {
        ProjectSchedule.revisedCompletion(project.dateStarted, project.contractDurationDays, documents)
    }
    val addedDays = remember(documents) { ProjectSchedule.addedDays(documents) }
    val cards: List<@Composable (Modifier) -> Unit> = listOf(
        { m -> AttentionCard(flags, m) },
        { m -> KeyDatesCard(project, revisedCompletion, addedDays, today, m) },
        { m -> CostCard(project, documents, m) },
        { m -> LatestReportCard(latestReport, openIssues, m) },
        { m -> ProfileCard(project, canEditProject, onEditProject, m) }
    )
    CardColumns(cards)
}

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
private fun KeyDatesCard(project: Project, revisedCompletion: String?, addedDays: Int, today: LocalDate, modifier: Modifier) {
    val end = revisedCompletion ?: ProjectSchedule.effectiveCompletion(project.completionDateOriginal, project.completionDateRevised)
    val daysLeft = runCatching { ChronoUnit.DAYS.between(today, LocalDate.parse(end.trim())) }.getOrNull()
    SectionCard("Key dates", Icons.Default.Event, modifier) {
        Field("Date started", project.dateStarted.ifBlank { "-" })
        Field("Original completion", project.completionDateOriginal.ifBlank { "-" })
        // Same date Days left counts down to: approved orders first, else the stored revised date.
        Field("Revised completion", revisedCompletion ?: project.completionDateRevised.takeIf { it.isNotBlank() && it != "-" } ?: "-")
        Field("Contract duration", "${CurrencyFormatter.formatNumber(project.contractDurationDays)} days")
        Field("Revised duration", ProjectSchedule.revisedDurationDays(project.dateStarted, project.contractDurationDays, addedDays, project.completionDateRevised)?.let { "${CurrencyFormatter.formatNumber(it)} days" } ?: "-")
        Field(
            "Days left",
            when {
                project.actualAccomplishment >= 100 -> "Completed"
                daysLeft == null -> "-"
                daysLeft < 0 -> "Overdue by ${-daysLeft} days"
                else -> "$daysLeft days"
            }
        )
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
            Field("Target", "%.1f%%".format(report.targetAccomplishmentPct))
            Field("Actual", "%.1f%%".format(report.actualAccomplishmentPct))
            Field("Submitted by", report.submittedByStaff.ifBlank { "-" })
        }
        Field("Open issues", openIssues.toString())
    }
}

@Composable
private fun ProfileCard(project: Project, canEditProject: Boolean, onEditProject: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    SectionCard(
        "Project profile",
        Icons.Default.Info,
        modifier,
        action = if (canEditProject) {
            {
                IconButton(onClick = onEditProject, modifier = Modifier.testTag("edit_project_button")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit Project", tint = scheme.onSurfaceVariant)
                }
            }
        } else null
    ) {
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
