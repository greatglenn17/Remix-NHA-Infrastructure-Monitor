package com.example.ui.detail.sections

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.StackedLineChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.unitsText
import com.example.ui.components.DateField
import com.example.ui.components.LongForm
import com.example.ui.theme.LocalNHAColors
import com.example.ui.theme.figures
import com.example.util.BarChart
import com.example.util.BarChartExcel
import com.example.util.ContractCost
import com.example.util.FormValidation
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

/**
 * The Bar chart tab: a chip per chart on file, the one in force open first, with each item's bars across the months
 * and the S-curve. [onSave] null hides editing; Edit opens [BarChartDialog], the form the Summary's card opens.
 */
@Composable
internal fun BarChartSection(project: Project, documents: List<PendingDocument>, today: LocalDate, onSave: ((String) -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val versions = remember(project.barChartJson) { BarChart.parse(project.barChartJson) }
    val plan = remember(project, documents, today) { BarChart.planOn(project, documents, today) }
    var selected by remember(versions, plan) { mutableIntStateOf(versions.indexOf(plan?.version).takeIf { it >= 0 } ?: versions.lastIndex) }
    var editing by remember { mutableStateOf(false) }
    val labels = versions.mapIndexed { i, v -> v.label.ifBlank { "Chart ${i + 1}" } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 8.dp).testTag("bar_chart_section")) {
        if (versions.size > 1) ChipRow(labels, labels[selected]) { selected = labels.indexOf(it) }
        SectionCard(
            "Approved bar chart", Icons.Default.StackedLineChart, Modifier.padding(16.dp).fillMaxWidth(),
            action = onSave?.let {
                {
                    IconButton(onClick = { editing = true }, modifier = Modifier.testTag("edit_bar_chart_button")) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit bar chart", tint = scheme.onSurfaceVariant)
                    }
                }
            }
        ) {
            val version = versions.getOrNull(selected)
            if (version == null) {
                Text("No bar chart yet. The target % is typed in each weekly report.", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                return@SectionCard
            }
            val inForce = version == plan?.version
            Field(if (inForce) "In force" else "On file", "${version.label} · from ${ProjectSchedule.displayDate(version.effective)}")
            if (inForce && plan != null) Field("Planned as of today", "%.2f%%".format(plan.plannedPct))
            Field("Chart length", "${version.lastDay} calendar days · ${CurrencyFormatter.formatPhp(version.total)}")
            // Whole months across, as on the sheet; a line marks where each month starts.
            val colours = remember(version) { BarChart.colours(version) }
            val last = maxOf(version.lastDay, version.items.flatMap { BarChart.marks(it) }.maxOfOrNull { it.second } ?: 0)
            val length = ((last + BarChart.MONTH_DAYS - 1) / BarChart.MONTH_DAYS).coerceAtLeast(1) * BarChart.MONTH_DAYS
            Text(
                "Each line is a month of 30 days from the start date.",
                style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)
            )
            version.items.forEach { ItemBarRow(it, version.total, colours, length) }
            if (colours.isNotEmpty()) ChartLegend(colours)
            MonthTable(version)
        }
    }
    if (editing && onSave != null) BarChartDialog(project, documents, onDismiss = { editing = false }, onSave = { onSave(it); editing = false })
}

/** An item's amount, weight and days, and its days drawn across the chart's [length]: work, and the chart's own colours (a suspension), as on the website. */
@Composable
private fun ItemBarRow(item: BarChart.Item, total: Double, colours: List<BarChart.Colour>, length: Int) {
    val scheme = MaterialTheme.colorScheme
    val work = LocalNHAColors.current.onTrack
    val fills = remember(item, colours, length) { BarChart.dayFills(item, colours, length) }
    // Runs of one fill: first day, last day, fill.
    val runs = remember(fills) {
        val out = mutableListOf<Triple<Int, Int, Int>>()
        fills.forEachIndexed { d, f ->
            val prev = out.lastOrNull()
            when {
                f == null -> Unit
                prev != null && prev.third == f && prev.second == d -> out.set(out.lastIndex, prev.copy(second = d + 1))
                else -> out.add(Triple(d + 1, d + 1, f))
            }
        }
        out
    }
    fun at(day: Int) = BarChart.monthDayOf(day).let { (m, d) -> "month $m day $d" }
    val spoken = runs.joinToString { (a, b, f) -> (if (f == BarChart.WORK) "" else "${colourName(colours[f])}: ") + "${at(a)} to ${at(b)}" }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("bar_row_${item.name}")) {
        Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
        val line = "${CurrencyFormatter.formatPhp(item.amount)} · ${"%.3f".format(if (total > 0) item.amount / total * 100 else 0.0)}% · ${item.days} c.d."
        Text(line, style = MaterialTheme.typography.bodySmall.figures(line), color = scheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().padding(top = 2.dp).height(10.dp).semantics { contentDescription = spoken }) {
            val perDay = size.width / length
            drawRect(scheme.surfaceVariant)
            runs.forEach { (a, b, f) ->
                drawRect(if (f == BarChart.WORK) work else Color(colours[f].argb), Offset((a - 1) * perDay, 0f), Size((b - a + 1) * perDay, size.height))
            }
            for (m in 1 until length / BarChart.MONTH_DAYS) {
                val x = m * BarChart.MONTH_DAYS * perDay
                drawLine(scheme.outline, Offset(x, 0f), Offset(x, size.height), 1f)
            }
        }
    }
}

private fun colourName(c: BarChart.Colour) = c.name.trim().ifEmpty { "New colour" }

/** What the work colour and each of the chart's own colours stand for, as under the website's sheet. */
@Composable
private fun ChartLegend(colours: List<BarChart.Colour>) {
    val scheme = MaterialTheme.colorScheme
    @Composable
    fun Key(color: Color, label: String) = Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Box(Modifier.size(12.dp).background(color, MaterialTheme.shapes.extraSmall))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurface)
    }
    Column(Modifier.padding(top = 4.dp).testTag("bar_chart_legend")) {
        Key(LocalNHAColors.current.onTrack, "Work")
        colours.forEach { Key(Color(it.argb), colourName(it) + if (it.work) "" else " (no work)") }
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
                    v, style = MaterialTheme.typography.bodySmall.figures(v),
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
    var startDay by mutableStateOf(from?.second?.toString().orEmpty())
    var endMonth by mutableStateOf(to?.first?.toString().orEmpty())
    var endDay by mutableStateOf(to?.second?.toString().orEmpty())

    /** The bar in contract days, or null when a field is not a month (1+) and day (1 to 30), or it ends before it starts. */
    fun bar(): BarChart.Bar? {
        fun day(text: String) = text.trim().toIntOrNull()?.takeIf { it in 1..BarChart.MONTH_DAYS }
        val sm = startMonth.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val em = endMonth.trim().toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val bar = BarChart.Bar((sm - 1) * BarChart.MONTH_DAYS + (day(startDay) ?: return null), (em - 1) * BarChart.MONTH_DAYS + (day(endDay) ?: return null))
        return bar.takeIf { it.days > 0 }
    }
}

private class ItemDraft(name: String, amount: Double, bars: List<BarChart.Bar>, val marks: String = "") {
    var name by mutableStateOf(name)
    // The Amount box of a revised chart; the text of a Double reads back as the same Double, so an untouched item saves as it was.
    var amountText by mutableStateOf(if (amount > 0) unitsText(amount) else "")
    /** An amount that does not read counts as nothing, which the item's line and the contract total then show. */
    val amount: Double get() = FormValidation.parseAmount(amountText) ?: 0.0
    val bars: SnapshotStateList<BarDraft> = mutableStateListOf<BarDraft>().apply {
        bars.forEach { add(BarDraft(BarChart.monthDayOf(it.startDay), BarChart.monthDayOf(it.endDay))) }
        if (isEmpty()) add(BarDraft())
    }
    fun item() = BarChart.Item(name.trim(), amount, bars.mapNotNull { it.bar() }, marks)
    fun valid() = bars.all { it.bar() != null }
}

private class VersionDraft(label: String, effective: String, items: List<ItemDraft>, val colors: String = "") {
    var label by mutableStateOf(label)
    var effective by mutableStateOf(effective)
    var items by mutableStateOf(items)
    fun version() = BarChart.Version(label.trim(), effective, items.map { it.item() }, colors)
}

/** Items from the Scope of Works, with no bars yet. */
private fun scopeDrafts(project: Project) = scopeItems(project.scopeOfWork).map { (name, amount, _) -> ItemDraft(name, amount, emptyList()) }

private fun itemDrafts(items: List<BarChart.Item>) = items.map { ItemDraft(it.name, it.amount, it.bars, it.marks) }

internal const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/**
 * Enter the approved bar chart: per Scope of Works item, the bar(s) from month and day to month and day,
 * as drawn on the chart (a revised chart's bars start and end between the 5-day cells, where work stopped and
 * resumed). A revised chart is added with the date it takes effect; the earlier ones stay.
 */
@Composable
internal fun BarChartDialog(project: Project, documents: List<PendingDocument>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val drafts = remember {
        mutableStateListOf<VersionDraft>().apply {
            BarChart.parse(project.barChartJson).forEach { v -> add(VersionDraft(v.label, v.effective, itemDrafts(v.items), v.colors)) }
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
    // The original chart must total the original contract amount; a revised one the original or the current (after Variation
    // Orders), and the latest revised one ([last]) the current: with a Variation Order the chart in force totals the revised contract.
    // shortcut: any approved VO counts, not only those before the chart's date; match VOs by date if a project needs it.
    fun totalMatches(i: Int, total: Double, last: Boolean = false) =
        i > 0 && abs(total - current) < 0.015 || (i == 0 || !last) && abs(total - original) < 0.015
    val errors = drafts.flatMapIndexed { i, d ->
        val date = runCatching { LocalDate.parse(d.effective) }.getOrNull()
        val total = d.items.sumOf { it.amount }
        // A revised chart's items can be renamed, added and removed, so each needs a name of its own: a Partial
        // suspension and the item targets go by name. The original is not checked, as its names cannot be typed.
        val twice = d.items.map { it.name.trim() }.filter { it.isNotEmpty() }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        listOfNotNull(
            "${d.label}: enter a name".takeIf { d.label.isBlank() },
            ("${d.label}: " + if (i > 0) "add an item, or import the Excel file" else "import the Excel file, or add the Scope of Works items to the project first")
                .takeIf { d.items.isEmpty() },
            "${d.label}: name every item".takeIf { i > 0 && d.items.any { it.name.isBlank() } },
            "${d.label}: two items are named ${twice.joinToString()}".takeIf { i > 0 && twice.isNotEmpty() },
            ("${d.label}: the chart totals ${CurrencyFormatter.formatPhp(total)} but the contract amount is " +
                CurrencyFormatter.formatPhp(if (i > 0) current else original)).takeIf { d.items.isNotEmpty() && !totalMatches(i, total, i == drafts.lastIndex) },
            "${d.label}: pick the date it takes effect".takeIf { i > 0 && date == null },
            "${d.label}: takes effect before the start date".takeIf { i > 0 && date != null && start != null && date.isBefore(start) },
            "${d.label}: takes effect on or before the previous chart".takeIf {
                i > 0 && date != null && drafts[i - 1].effective.let { prev -> prev >= d.effective }
            },
            d.items.filter { !it.valid() }.takeIf { it.isNotEmpty() }
                ?.let { bad -> "${d.label}: check the bars of ${bad.joinToString { it.name.trim().ifEmpty { "New item" } }} (month 1+, day 1 to 30)" }
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
                        else drafts.last().version().let { VersionDraft("Revised No. ${drafts.size}", "", itemDrafts(it.items), it.colors) }
                    )
                    selected = drafts.lastIndex
                    importNotes = emptyList()
                }, text = { Text("+ Revised chart") })
            }
        }
    ) {
        Text(
            "Import the contractor's Excel file, or enter each item's bar as drawn: From month and day, To month and day. " +
                "A month is 30 days, counted from the start date; the cell labelled 10 is days 6 to 10. " +
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
                "Draw the revised chart from the start date, with suspended months left empty, as approved. " +
                    "Where the approved revision changes the money, change an item's amount, or add and remove items.",
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
            )
        }
        val revised = selected > 0
        draft.items.forEachIndexed { i, item ->
            ItemBars(item, draft.items.sumOf { it.amount }, i, onRemove = { draft.items = draft.items - item }.takeIf { revised })
        }
        if (revised) TextButton(onClick = { draft.items = draft.items + ItemDraft("", 0.0, emptyList()) }, modifier = Modifier.testTag("add_chart_item")) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("Add item")
        }
        MonthTable(draft.version())
        TextButton(
            onClick = { drafts.removeAt(selected); selected = (selected - 1).coerceAtLeast(0) },
            colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
            modifier = Modifier.testTag("delete_bar_chart")
        ) { Text(if (drafts.size == 1) "Remove the bar chart" else "Delete this chart") }
    }
}

@Composable
private fun ItemBars(item: ItemDraft, total: Double, index: Int, onRemove: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Surface(color = scheme.surfaceVariant, shape = MaterialTheme.shapes.small, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // A revised chart's item: its name and amount as approved, and Remove; the original keeps the Scope of Works'.
            if (onRemove == null) Text(item.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
            else {
                OutlinedTextField(
                    item.name, { item.name = it }, label = { Text("Item name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("item_${index}_name")
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        item.amountText, { item.amountText = it }, label = { Text("Amount (₱)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f).testTag("item_${index}_amount")
                    )
                    IconButton(onClick = onRemove, modifier = Modifier.testTag("item_${index}_remove")) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove item")
                    }
                }
            }
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
                    Num("day", bar.startDay, "sd") { bar.startDay = it }
                    Num("To", bar.endMonth, "em") { bar.endMonth = it }
                    Num("day", bar.endDay, "ed") { bar.endDay = it }
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
