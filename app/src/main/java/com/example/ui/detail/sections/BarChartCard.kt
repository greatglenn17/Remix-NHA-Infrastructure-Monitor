package com.example.ui.detail.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.ui.components.DateField
import com.example.ui.components.LongForm
import com.example.ui.theme.TabularNumbers
import com.example.util.BarChart
import com.example.util.ProjectSchedule
import com.example.utils.CurrencyFormatter
import java.time.LocalDate

/** The approved bar chart: the chart in force, today's planned %, and its S-curve by month; [onEdit] null hides editing. */
@Composable
internal fun BarChartCard(project: Project, documents: List<PendingDocument>, today: LocalDate, onEdit: (() -> Unit)?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val versions = remember(project.barChartJson) { BarChart.parse(project.barChartJson) }
    val plan = remember(project, documents, today) { BarChart.planOn(project, documents, today) }
    SectionCard(
        "Approved bar chart", Icons.Default.StackedLineChart, modifier,
        action = onEdit?.let { edit ->
            {
                IconButton(onClick = edit, modifier = Modifier.testTag("edit_bar_chart_button")) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit bar chart", tint = scheme.onSurfaceVariant)
                }
            }
        }
    ) {
        if (plan == null) {
            Text(
                "No bar chart yet. The target % is typed in each weekly report.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
            )
            return@SectionCard
        }
        val (version, day) = plan
        val (month, cell) = BarChart.cellOf(day.coerceIn(1, version.lastDay.coerceAtLeast(1)))
        Field("In force", "${version.label} · from ${ProjectSchedule.displayDate(version.effective)}")
        Field("Planned as of today", "%.2f%%".format(version.plannedPct(day)) + if (day in 1..version.lastDay) " · Month $month, cell $cell" else "")
        Field("Chart length", "${version.lastDay} calendar days")
        versions.filter { it != version }.forEach { Field("Also on file", "${it.label} · from ${ProjectSchedule.displayDate(it.effective)}") }
        MonthTable(version)
    }
}

@Composable
private fun MonthTable(version: BarChart.Version) {
    val scheme = MaterialTheme.colorScheme
    @Composable
    fun Cells(values: List<String>, bold: Boolean = false) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            values.forEachIndexed { i, v ->
                Text(
                    v, style = MaterialTheme.typography.bodySmall.merge(TabularNumbers),
                    color = if (bold) scheme.onSurfaceVariant else scheme.onSurface,
                    fontWeight = if (bold) FontWeight.SemiBold else null,
                    textAlign = if (i > 0) TextAlign.End else null, modifier = Modifier.weight(1f)
                )
            }
        }
    }
    Text("S-curve", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
    Cells(listOf("Month", "Monthly", "Cumulative"), bold = true)
    HorizontalDivider(color = scheme.outlineVariant)
    BarChart.months(version).forEach { m ->
        Cells(listOf("${m.no}", "%.2f%%".format(m.monthlyPct), "%.2f%%".format(m.cumulativePct)))
    }
}

private class BarDraft(from: Pair<Int, Int>? = null, to: Pair<Int, Int>? = null) {
    var startMonth by mutableStateOf(from?.first?.toString().orEmpty())
    var startCell by mutableStateOf(from?.second?.toString().orEmpty())
    var endMonth by mutableStateOf(to?.first?.toString().orEmpty())
    var endCell by mutableStateOf(to?.second?.toString().orEmpty())

    /** The bar in contract days, or null when a field is not a month (1+) and cell (5, 10 … 30), or it ends before it starts. */
    fun bar(): BarChart.Bar? {
        fun cell(text: String) = text.trim().toIntOrNull()?.takeIf { it in BarChart.CELL_DAYS..BarChart.MONTH_DAYS && it % BarChart.CELL_DAYS == 0 }
        val sm = startMonth.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val em = endMonth.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val bar = BarChart.Bar(BarChart.cellStart(sm, cell(startCell) ?: return null), BarChart.cellEnd(em, cell(endCell) ?: return null))
        return bar.takeIf { it.days > 0 }
    }
}

private class ItemDraft(val name: String, val amount: Double, bars: List<BarChart.Bar>) {
    val bars: SnapshotStateList<BarDraft> = mutableStateListOf<BarDraft>().apply {
        bars.forEach { add(BarDraft(BarChart.cellOf(it.startDay), BarChart.cellOf(it.endDay))) }
        if (isEmpty()) add(BarDraft())
    }
    fun item() = BarChart.Item(name, amount, bars.mapNotNull { it.bar() })
    fun valid() = bars.all { it.bar() != null }
}

private class VersionDraft(label: String, effective: String, val items: List<ItemDraft>) {
    var label by mutableStateOf(label)
    var effective by mutableStateOf(effective)
    fun version() = BarChart.Version(label.trim(), effective, items.map { it.item() })
}

/** Items from the Scope of Works, with the bars [like] already has for the same item. */
private fun scopeDrafts(project: Project, like: BarChart.Version?) = scopeItems(project.scopeOfWork).map { (name, amount, _) ->
    ItemDraft(name, amount, like?.items?.firstOrNull { it.name == name }?.bars.orEmpty())
}

/**
 * Enter the approved bar chart: per Scope of Works item, the bar(s) from month and cell to month and cell,
 * as drawn on the chart. A revised chart is added with the date it takes effect; the earlier ones stay.
 */
@Composable
internal fun BarChartDialog(project: Project, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val drafts = remember {
        mutableStateListOf<VersionDraft>().apply {
            BarChart.parse(project.barChartJson).forEach { v ->
                add(VersionDraft(v.label, v.effective, v.items.map { ItemDraft(it.name, it.amount, it.bars) }))
            }
            if (isEmpty()) add(VersionDraft("Original", project.dateStarted, scopeDrafts(project, null)))
            // The first chart always runs from the start date, even after the date is corrected.
            first().effective = project.dateStarted
        }
    }
    var selected by remember { mutableIntStateOf(drafts.lastIndex) }
    val initial = remember { BarChart.toJson(drafts.map { it.version() }) }
    val json = BarChart.toJson(drafts.map { it.version() })
    val start = runCatching { LocalDate.parse(project.dateStarted.trim()) }.getOrNull()
    val errors = drafts.flatMapIndexed { i, d ->
        val date = runCatching { LocalDate.parse(d.effective) }.getOrNull()
        listOfNotNull(
            "${d.label}: enter a name".takeIf { d.label.isBlank() },
            "${d.label}: pick the date it takes effect".takeIf { i > 0 && date == null },
            "${d.label}: takes effect before the start date".takeIf { i > 0 && date != null && start != null && date.isBefore(start) },
            "${d.label}: takes effect on or before the previous chart".takeIf {
                i > 0 && date != null && drafts[i - 1].effective.let { prev -> prev >= d.effective }
            },
            d.items.filter { !it.valid() }.takeIf { it.isNotEmpty() }
                ?.let { bad -> "${d.label}: check the bars of ${bad.joinToString { it.name }} (month 1+, cell 5, 10 … 30)" }
        )
    }
    if (start == null) {
        AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
            title = { Text("Start date needed") }, text = { Text("Set the project's start date first; the bar chart counts its days from it.") })
        return
    }
    LongForm(
        title = "Approved bar chart",
        dirty = json != initial,
        saveEnabled = errors.isEmpty(),
        saveTag = "save_bar_chart",
        onDismiss = onDismiss,
        onSave = { onSave(if (drafts.isEmpty()) "" else json) },
        topContent = {
            ScrollableTabRow(selectedTabIndex = selected.coerceIn(0, (drafts.size - 1).coerceAtLeast(0)), edgePadding = 16.dp) {
                drafts.forEachIndexed { i, d -> Tab(selected = i == selected, onClick = { selected = i }, text = { Text(d.label.ifBlank { "Chart ${i + 1}" }) }) }
                Tab(selected = false, modifier = Modifier.testTag("add_revised_chart"), onClick = {
                    drafts.add(
                        if (drafts.isEmpty()) VersionDraft("Original", project.dateStarted, scopeDrafts(project, null))
                        else VersionDraft("Revised No. ${drafts.size}", "", scopeDrafts(project, drafts.last().version()))
                    )
                    selected = drafts.lastIndex
                }, text = { Text("+ Revised chart") })
            }
        }
    ) {
        Text(
            "Enter each item's bar as drawn: From month and cell, To month and cell. A month is 30 days; its cells are 5, 10, 15, 20, 25 and 30. " +
                "Amounts come from the Scope of Works. The target % then follows this chart every day.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
        )
        errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
        val draft = drafts.getOrNull(selected) ?: return@LongForm
        OutlinedTextField(draft.label, { draft.label = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (selected == 0) {
            Field("Takes effect", "Start date · ${ProjectSchedule.displayDate(project.dateStarted)}")
        } else {
            DateField("Takes effect on", draft.effective, { draft.effective = it })
            Text(
                "Draw the revised chart from the start date, with suspended months left empty, as approved.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
            )
        }
        if (draft.items.isEmpty()) Text("Add the Scope of Works items to the project first.", style = MaterialTheme.typography.bodySmall, color = scheme.error)
        draft.items.forEach { item -> ItemBars(item, draft.items.sumOf { it.amount }) }
        MonthTable(draft.version())
        TextButton(
            onClick = { drafts.removeAt(selected); selected = (selected - 1).coerceAtLeast(0) },
            colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
            modifier = Modifier.testTag("delete_bar_chart")
        ) { Text(if (drafts.size == 1) "Remove the bar chart" else "Delete this chart") }
    }
}

@Composable
private fun ItemBars(item: ItemDraft, total: Double) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            val days = item.bars.mapNotNull { it.bar() }.sumOf { it.days }
            Field(
                "Amount · weight · duration",
                "${CurrencyFormatter.formatPhp(item.amount)} · ${"%.3f".format(if (total > 0) item.amount / total * 100 else 0.0)}% · $days c.d."
            )
            item.bars.forEachIndexed { i, bar ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    @Composable
                    fun Num(label: String, value: String, tag: String, onChange: (String) -> Unit) = OutlinedTextField(
                        value, onChange, label = { Text(label) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f).testTag("bar_${item.name}_${i}_$tag")
                    )
                    Num("From", bar.startMonth, "sm") { bar.startMonth = it }
                    Num("cell", bar.startCell, "sc") { bar.startCell = it }
                    Num("To", bar.endMonth, "em") { bar.endMonth = it }
                    Num("cell", bar.endCell, "ec") { bar.endCell = it }
                    if (item.bars.size > 1) IconButton(onClick = { item.bars.removeAt(i) }) {
                        Icon(Icons.Default.Close, contentDescription = "Remove bar")
                    }
                }
            }
            TextButton(onClick = { item.bars.add(BarDraft()) }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Another bar")
            }
        }
    }
}
