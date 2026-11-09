package com.example.ui.detail.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import com.example.util.BarChartExcel
import com.example.util.ContractCost
import com.example.util.ProjectSchedule
import com.example.utils.CurrencyFormatter
import java.time.LocalDate
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        val version = plan.version
        val day = plan.day
        val (month, cell) = BarChart.cellOf(day.coerceIn(1, version.lastDay.coerceAtLeast(1)))
        Field("In force", "${version.label} · from ${ProjectSchedule.displayDate(version.effective)}")
        Field("Planned as of today", "%.2f%%".format(plan.plannedPct) + if (day in 1..version.lastDay) " · Month $month, cell $cell" else "")
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

private class VersionDraft(label: String, effective: String, items: List<ItemDraft>) {
    var label by mutableStateOf(label)
    var effective by mutableStateOf(effective)
    var items by mutableStateOf(items)
    fun version() = BarChart.Version(label.trim(), effective, items.map { it.item() })
}

/** Items from the Scope of Works, with no bars yet. */
private fun scopeDrafts(project: Project) = scopeItems(project.scopeOfWork).map { (name, amount, _) -> ItemDraft(name, amount, emptyList()) }

private fun itemDrafts(items: List<BarChart.Item>) = items.map { ItemDraft(it.name, it.amount, it.bars) }

private const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * Enter the approved bar chart: per Scope of Works item, the bar(s) from month and cell to month and cell,
 * as drawn on the chart. A revised chart is added with the date it takes effect; the earlier ones stay.
 */
@Composable
internal fun BarChartDialog(project: Project, documents: List<PendingDocument>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val drafts = remember {
        mutableStateListOf<VersionDraft>().apply {
            BarChart.parse(project.barChartJson).forEach { v -> add(VersionDraft(v.label, v.effective, itemDrafts(v.items))) }
            if (isEmpty()) add(VersionDraft("Original", project.dateStarted, scopeDrafts(project)))
            // The first chart always runs from the start date, even after the date is corrected.
            first().effective = project.dateStarted
        }
    }
    var selected by remember { mutableIntStateOf(drafts.lastIndex) }
    val initial = remember { BarChart.toJson(drafts.map { it.version() }) }
    val json = BarChart.toJson(drafts.map { it.version() })
    val start = runCatching { LocalDate.parse(project.dateStarted.trim()) }.getOrNull()
    val original = ContractCost.original(project.scopeOfWork, project.contractCostOriginal)
    val current = ContractCost.current(project.scopeOfWork, project.contractCostOriginal, documents)
    // The original chart must total the original contract amount; a revised one the original or the current (after Variation Orders).
    // shortcut: any approved VO counts, not only those before the chart's date; match VOs by date if a project needs it.
    fun totalMatches(i: Int, total: Double) = abs(total - original) < 0.015 || i > 0 && abs(total - current) < 0.015
    val errors = drafts.flatMapIndexed { i, d ->
        val date = runCatching { LocalDate.parse(d.effective) }.getOrNull()
        val total = d.items.sumOf { it.amount }
        listOfNotNull(
            "${d.label}: enter a name".takeIf { d.label.isBlank() },
            ("${d.label}: the chart totals ${CurrencyFormatter.formatPhp(total)} but the contract amount is " +
                CurrencyFormatter.formatPhp(if (i > 0) current else original)).takeIf { d.items.isNotEmpty() && !totalMatches(i, total) },
            "${d.label}: pick the date it takes effect".takeIf { i > 0 && date == null },
            "${d.label}: takes effect before the start date".takeIf { i > 0 && date != null && start != null && date.isBefore(start) },
            "${d.label}: takes effect on or before the previous chart".takeIf {
                i > 0 && date != null && drafts[i - 1].effective.let { prev -> prev >= d.effective }
            },
            d.items.filter { !it.valid() }.takeIf { it.isNotEmpty() }
                ?.let { bad -> "${d.label}: check the bars of ${bad.joinToString { it.name }} (month 1+, cell 5, 10 … 30)" }
        )
    }
    // Reading an Excel file: its sheets laid out as a bar chart, to pick from when more than one.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sheets by remember { mutableStateOf<List<BarChartExcel.Sheet>>(emptyList()) }
    var importNotes by remember { mutableStateOf<List<String>>(emptyList()) }
    fun useSheet(sheet: BarChartExcel.Sheet) {
        drafts.getOrNull(selected)?.items = itemDrafts(sheet.items)
        importNotes = listOf("Read ${sheet.items.size} items from the sheet \"${sheet.name}\".") + sheet.items.mapNotNull { item ->
            val days = item.bars.sumOf { it.days }
            sheet.durations[item.name]?.takeIf { it != days }?.let { "${item.name}: the bar covers $days days but its Duration says $it." }
        }
        sheets = emptyList()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { BarChartExcel.read(it) }.orEmpty() }
            }
            val found = read.getOrNull().orEmpty()
            when {
                read.isFailure -> importNotes = listOf("That file could not be read. Pick the bar chart saved as an Excel workbook (.xlsx).")
                found.isEmpty() -> importNotes = listOf("No sheet in that file has the ITEM NO. / DESCRIPTION / Amount header with a Month 1 row.")
                found.size == 1 -> useSheet(found.single())
                else -> sheets = found
            }
        }
    }
    if (sheets.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { sheets = emptyList() },
            confirmButton = { TextButton(onClick = { sheets = emptyList() }) { Text("Cancel") } },
            title = { Text("Which sheet?") },
            text = {
                Column {
                    sheets.forEach { sheet ->
                        val total = sheet.items.sumOf { it.amount }
                        TextButton(onClick = { useSheet(sheet) }, modifier = Modifier.fillMaxWidth().testTag("import_sheet_${sheet.name}")) {
                            Text(
                                "${sheet.name} · ${sheet.items.size} items · ${CurrencyFormatter.formatPhp(total)}" +
                                    if (totalMatches(selected, total)) " · matches the contract" else "",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
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
                drafts.forEachIndexed { i, d -> Tab(selected = i == selected, onClick = { selected = i; importNotes = emptyList() }, text = { Text(d.label.ifBlank { "Chart ${i + 1}" }) }) }
                Tab(selected = false, modifier = Modifier.testTag("add_revised_chart"), onClick = {
                    drafts.add(
                        if (drafts.isEmpty()) VersionDraft("Original", project.dateStarted, scopeDrafts(project))
                        else VersionDraft("Revised No. ${drafts.size}", "", itemDrafts(drafts.last().version().items))
                    )
                    selected = drafts.lastIndex
                    importNotes = emptyList()
                }, text = { Text("+ Revised chart") })
            }
        }
    ) {
        Text(
            "Import the contractor's Excel file, or enter each item's bar as drawn: From month and cell, To month and cell. " +
                "A month is 30 days; its cells are 5, 10, 15, 20, 25 and 30, counted from the start date. " +
                "The chart must total the contract amount. The target % then follows this chart every day.",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
        )
        errors.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error) }
        val draft = drafts.getOrNull(selected) ?: return@LongForm
        OutlinedButton(
            onClick = { picker.launch(arrayOf(XLSX)) },
            modifier = Modifier.fillMaxWidth().testTag("import_bar_chart")
        ) { Text("Import from Excel (.xlsx)") }
        importNotes.forEach { note ->
            Text(note, style = MaterialTheme.typography.bodySmall, color = if (note.startsWith("Read ")) scheme.onSurfaceVariant else scheme.error)
        }
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
        if (draft.items.isEmpty()) Text("Import the Excel file, or add the Scope of Works items to the project first.", style = MaterialTheme.typography.bodySmall, color = scheme.error)
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
