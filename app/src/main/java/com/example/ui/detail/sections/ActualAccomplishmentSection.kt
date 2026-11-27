package com.example.ui.detail.sections

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.ProjectPayment
import com.example.ui.components.DateField
import com.example.ui.components.rememberDiscardGuard
import com.example.ui.theme.LocalNHAColors
import com.example.ui.theme.figures
import com.example.util.AbstractExcel
import com.example.util.BillingAbstract
import com.example.util.FormValidation
import com.example.util.ProjectAbstracts
import com.example.util.ProjectSchedule
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun money(value: Double) = "%,.2f".format(Locale.US, value)

/** A row of the summary card's table, ruled as the website's: the name, then Target, Actual and +/-. */
@Composable
private fun StandingRow(name: String, cells: List<String>, header: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val cell = Modifier.fillMaxHeight().border(0.5.dp, scheme.outlineVariant).padding(4.dp)
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(if (header) scheme.surfaceVariant else scheme.surface)
            .semantics(mergeDescendants = true) {}.testTag("standing_$name")
    ) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.weight(1.7f).then(cell))
        cells.forEach { text ->
            Text(
                text, style = MaterialTheme.typography.bodySmall.figures(text), fontWeight = FontWeight.SemiBold,
                color = if (header) scheme.onSurfaceVariant else scheme.onSurface, textAlign = TextAlign.End, modifier = Modifier.weight(1f).then(cell)
            )
        }
    }
}

/**
 * The widths of the pay items' 15 columns, as the website's table has them: item no., description, quantity, unit,
 * unit cost, amount; quantity, amount and % for previous, this period and to date. The table ends at TO DATE, as the
 * printed abstracts do: a row's balance is kept on the record but not shown.
 * At the phone's font size a figure keeps to one line up to 9,999,999,999.99 (a quantity to 1,234,567.8901).
 */
private val ITEM_WIDTHS: List<Int> = listOf(76, 200, 116, 56, 132, 132) + List(3) { listOf(116, 132, 68) }.flatten()

/**
 * [ITEM_WIDTHS] at the phone's font size: the figures are set in sp, so the columns grow with them. Each is a whole
 * number of pixels, so a cell across several columns (a group's name, PREVIOUS) ends where they end.
 */
@Composable
private fun itemWidths(): List<Dp> = with(LocalDensity.current) { ITEM_WIDTHS.map { (it * fontScale).dp.roundToPx().toDp() } }

/**
 * A row of the pay items' table, moving sideways with the rest ([scroll] is the table's). Without the stretch at the
 * table's ends: each row would have its own, so only the row under the finger would move out of line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableRow(scroll: ScrollState, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        Row(Modifier.horizontalScroll(scroll).height(IntrinsicSize.Min).then(modifier), content = content)
    }

/** One cell of the pay items' table; [fill] takes the row's height, so the rules of a row line up. */
@Composable
private fun ItemCell(text: String, width: Dp, align: TextAlign = TextAlign.End, bold: Boolean = false, fill: Boolean = true) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text, style = MaterialTheme.typography.bodySmall.figures(text), fontWeight = if (bold) FontWeight.SemiBold else null, color = scheme.onSurface, textAlign = align,
        modifier = Modifier.width(width).then(if (fill) Modifier.fillMaxHeight() else Modifier).border(0.5.dp, scheme.outlineVariant).padding(horizontal = 6.dp, vertical = 4.dp)
    )
}

/** The table's column headings, in two rows as the website's table has them. */
@Composable
private fun ItemsHeader(scroll: ScrollState) {
    val w = itemWidths()
    TableRow(scroll, Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        listOf("ITEM NO.", "ITEM DESCRIPTION", "QTY", "UNIT", "UNIT COST", "AMOUNT").forEachIndexed { k, label -> ItemCell(label, w[k], TextAlign.Center, bold = true) }
        // Each with the place of its first column.
        listOf("PREVIOUS" to 6, "THIS PERIOD" to 9, "TO DATE" to 12).forEach { (label, first) ->
            val under = listOf("QTY", "AMOUNT", "% WT")
            Column {
                ItemCell(label, under.indices.fold(0.dp) { sum, k -> sum + w[first + k] }, TextAlign.Center, bold = true, fill = false)
                Row { under.forEachIndexed { k, sub -> ItemCell(sub, w[first + k], TextAlign.Center, bold = true, fill = false) } }
            }
        }
    }
}

/**
 * Row [index] of the abstract in the table, the sheet's columns up to TO DATE and an empty cell left blank; a group is its
 * number, then its name across the rest. Groups, subtotals and the grand total are bold.
 */
@Composable
private fun ItemRow(row: AbstractExcel.Row, index: Int, scroll: ScrollState) {
    val w = itemWidths()
    val bold = row.kind == "group" || row.kind == "subtotal" || row.kind == "total"
    fun qty(v: Double?) = v?.let(ProjectAbstracts::quantityText).orEmpty()
    fun amount(v: Double?) = v?.let(::money).orEmpty()
    TableRow(scroll, Modifier.background(MaterialTheme.colorScheme.surface).semantics(mergeDescendants = true) {}.testTag("abstract_row_$index")) {
        ItemCell(row.no, w[0], TextAlign.Start, bold)
        if (row.kind == "group") ItemCell(row.name, w.drop(1).fold(0.dp) { sum, x -> sum + x }, TextAlign.Start, bold)
        else {
            ItemCell(row.name, w[1], TextAlign.Start, bold)
            val figures = listOf(qty(row.qty), row.unit, amount(row.unitCost), amount(row.amount)) +
                listOf(row.prev, row.period, row.toDate).flatMap { f -> listOf(qty(f.getOrNull(0)), amount(f.getOrNull(1)), f.getOrNull(2)?.let(ProjectAbstracts::pct).orEmpty()) }
            figures.forEachIndexed { k, text -> ItemCell(text, w[k + 2], if (k == 1) TextAlign.Center else TextAlign.End, bold) }
        }
    }
}

/**
 * "This billing": the figures typed for the billing abstract of [abstract] (the one the contractor attaches before a
 * billing), then Print. Nothing typed here is saved. A line the abstract has no amount for has no box.
 */
@Composable
private fun BillingFiguresDialog(
    abstract: ProjectAbstract,
    onDismiss: () -> Unit,
    // Where billed to date would pass the actual (BillingAbstract.printWarnings): shown, but the sheet still prints.
    warnings: (BillingAbstract.Period) -> List<String> = { emptyList() },
    onPrint: (BillingAbstract.Period) -> Unit
) {
    val amounts = remember(abstract.rowsJson) { ProjectAbstracts.lineAmounts(abstract) }
    var covered by remember { mutableStateOf("") }
    var lots by remember { mutableStateOf("") }
    var development by remember { mutableStateOf("") }
    var m1 by remember { mutableStateOf("") }
    var m2 by remember { mutableStateOf("") }
    // A blank or unreadable number is 0, and so is a negative.
    fun number(text: String) = FormValidation.parseAmount(text) ?: 0.0
    val period = BillingAbstract.Period(covered, number(lots), number(development), number(m1), number(m2))
    val warned = remember(period) { warnings(period) }
    @Composable fun FigureBox(label: String, value: String, tag: String, number: Boolean = true, onChange: (String) -> Unit) = OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = Modifier.fillMaxWidth().testTag(tag)
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This billing") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FigureBox("Period covered", covered, "billing_covered", number = false) { covered = it }
                if (amounts[0] > 0) FigureBox("Land acquisition (lots)", lots, "billing_lots") { lots = it }
                if (amounts[1] > 0) FigureBox("Land development (lots)", development, "billing_development") { development = it }
                if (amounts[2] > 0) {
                    FigureBox("Milestone 1 (units)", m1, "billing_m1") { m1 = it }
                    FigureBox("Milestone 2 (units)", m2, "billing_m2") { m2 = it }
                }
                Text(
                    "Previous is what the billings saved up to ${ProjectSchedule.displayDate(abstract.asOf)} came to. Nothing typed here is saved.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                warned.forEach {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = LocalNHAColors.current.watch, modifier = Modifier.testTag("billing_print_warning"))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPrint(period) },
                modifier = Modifier.testTag("confirm_print_billing_abstract")
            ) { Text(if (warned.isEmpty()) "Print" else "Print anyway") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * "New abstract": the next abstract, made here from the one before it ([ProjectAbstracts.nextSource]). The engineer
 * picks the "As of" date and types each pay item's quantity for this period; previous, to date, the amounts and the
 * percentages are computed ([ProjectAbstracts.nextRows]). A housing construction item takes the number of houses
 * instead, each house its QTY ÷ [units] ([ProjectAbstracts.houses]). [onSave] gets the record and
 * whether it replaces the abstract already filed for its date.
 */
@Composable
private fun NewAbstractDialog(abstracts: List<ProjectAbstract>, madeBy: String, units: Int, today: LocalDate, onDismiss: () -> Unit, onSave: (ProjectAbstract, Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // Saved: a turn of the phone must not lose the quantities typed so far.
    var asOf by rememberSaveable { mutableStateOf(today.toString()) }
    val found = remember(abstracts, asOf) { ProjectAbstracts.nextSource(abstracts, asOf) }
    // The abstract this one continues; none while the date cannot be used.
    val source = found.source.takeIf { found.error.isEmpty() }
    val rows = remember(source?.rowsJson) { source?.let { ProjectAbstracts.rows(it.rowsJson) }.orEmpty() }
    val houses = remember(rows, units) { ProjectAbstracts.houseLines(rows, units).toSet() }
    // What is left of each item's QTY: a box takes no more, so the to date never passes it.
    val most = remember(rows, units) { ProjectAbstracts.nextMost(rows, units) }
    // This period's quantities of the abstract already filed for the date, which the boxes start from.
    val saved = remember(rows, found.same) { ProjectAbstracts.nextTyped(rows, found.same) }
    // What is typed, as text by row index, and what the boxes started from.
    var text by rememberSaveable { mutableStateOf(emptyMap<Int, String>()) }
    var started by rememberSaveable { mutableStateOf(emptyMap<Int, String>()) }
    var from by rememberSaveable { mutableStateOf("") }
    // Another abstract to continue, a date that already has one, or other housing units start from what that date has; any other date keeps what was typed.
    val key = "${source?.uuid}|${found.same?.uuid}|$units"
    if (rows.isNotEmpty() && from != key) {
        from = key
        started = ProjectAbstracts.houses(rows, saved, units, back = true).mapValues { java.math.BigDecimal.valueOf(it.value).stripTrailingZeros().toPlainString() }
        text = started
    }
    // A blank box is not typed; text that is not a number is NaN, which nextErrors names.
    val filled = text.filterValues { it.isNotBlank() }
    // A plain decimal with commas allowed, as on the website (its typedQuantities).
    val typed = filled.mapValues { (_, t) -> t.replace(",", "").trim().takeIf { Regex("""-?(\d+(\.\d*)?|\.\d+)""").matches(it) }?.toDouble() ?: Double.NaN }
    // The houses typed for the housing construction items as their quantities; a house box left as it started keeps its saved quantity exactly.
    val quantities = ProjectAbstracts.houses(rows, typed, units) + saved.filterKeys { it in houses && text[it] == started[it] }
    val computed = ProjectAbstracts.nextRows(rows, quantities, source?.equivUnits ?: 0.0)
    var problems by remember { mutableStateOf("") }
    var asking by remember { mutableStateOf<ProjectAbstract?>(null) }
    val close = rememberDiscardGuard(filled != started, onDismiss)
    asking?.let { record ->
        AlertDialog(
            onDismissRequest = { asking = null },
            text = { Text("An abstract as of ${ProjectSchedule.displayDate(record.asOf)} is already filed. Replace it with this one?") },
            confirmButton = { TextButton(onClick = { asking = null; onSave(record, true) }, modifier = Modifier.testTag("confirm_replace_abstract")) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("Cancel") } }
        )
    }
    AlertDialog(
        modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(16.dp).imePadding().systemBarsPadding(),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        onDismissRequest = close,
        title = { Text("New abstract") },
        text = {
            Column {
                // Everything scrolls with the rows, so the open keyboard leaves room for them.
                LazyColumn(Modifier.weight(1f, fill = false).testTag("new_abstract_rows"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        Text(
                            "Type only this period's quantity of each item. Previous is the to date of the abstract before it; to date, the amounts and the percentages are computed." +
                                if (houses.isEmpty()) "" else " Housing construction items take the number of houses this period instead: each house is the item's QTY ÷ ${ProjectAbstracts.quantityText(units.toDouble())}, the project's housing units.",
                            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                        )
                    }
                    item { DateField("As of", asOf, { asOf = it; problems = "" }) }
                    item {
                        if (source == null) Text(found.error, style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.testTag("new_abstract_error"))
                        else Text(ProjectAbstracts.nextNote(source, found.same, computed), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface, modifier = Modifier.testTag("new_abstract_note"))
                    }
                    rows.forEachIndexed { i, row ->
                        val name = "${row.no}  ${row.name}".trim()
                        when {
                            ProjectAbstracts.isPayLine(row) -> item(key = i) {
                                Column(Modifier.fillMaxWidth()) {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurface)
                                    Text(
                                        "${ProjectAbstracts.quantityText(row.qty ?: 0.0)} ${row.unit} · Previous ${ProjectAbstracts.quantityText(computed[i].prev[0] ?: 0.0)}",
                                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = text[i].orEmpty(), onValueChange = { text = text + (i to ProjectAbstracts.capped(it, most[i])) }, label = { Text(if (i in houses) "Houses this period" else "This period") }, singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f).testTag("next_period_$i")
                                        )
                                        Text(
                                            "To date ${ProjectAbstracts.quantityText(computed[i].toDate[0] ?: 0.0)}", style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold, color = scheme.onSurface, modifier = Modifier.weight(1f).testTag("next_done_$i")
                                        )
                                    }
                                    // The houses typed as the item's own quantity, as on the website: "houses × 28.4 = 3,408 sq.m".
                                    if (i in houses) Text(
                                        "houses × ${ProjectAbstracts.quantityText((row.qty ?: 0.0) / units)} = ${quantities[i]?.let { if (it.isFinite()) ProjectAbstracts.quantityText(it) else "–" } ?: "0"} ${row.unit}",
                                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.testTag("next_houses_$i")
                                    )
                                }
                            }
                            row.kind == "part" || row.kind == "group" -> item(key = i) {
                                Text(name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                if (problems.isNotEmpty()) Text(problems, style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.padding(top = 8.dp).testTag("new_abstract_problems"))
            }
        },
        confirmButton = {
            Button(
                shape = MaterialTheme.shapes.small, enabled = source != null,
                onClick = {
                    val errors = ProjectAbstracts.nextErrors(rows, quantities)
                    if (errors.isNotEmpty()) problems = errors.take(3).joinToString(" ") + if (errors.size > 3) " And ${errors.size - 3} more." else ""
                    else source?.let { ProjectAbstracts.nextRecord(it, computed, asOf, madeBy) }?.let { if (found.same != null) asking = it else onSave(it, false) }
                },
                modifier = Modifier.testTag("save_new_abstract")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}

/**
 * Actual Accomplishment: the uploaded Abstracts of Physical Accomplishment of the actual work, each kept under its
 * "As of" date; the latest is the project's actual. Shows the chosen one's target, actual and advance/slippage by
 * line ([ProjectAbstracts.standing], from [project]'s bar chart and [documents]) and, under it, its work in lots and
 * units ([BillingAbstract.inUnits], from the saved billings [payments] and the project's [units]; on the screen only,
 * never on a printed sheet, and not in Monthly), then every pay item in a table of
 * the sheet's columns, as the website's: it scrolls sideways, and each part folds. Two sheets print from it: "Print abstract" ([onPrintAbstract]) is the detailed one of the actual work;
 * "Print billing abstract" takes this billing's figures, then [onPrintBilling] prints the billing format.
 * "New abstract" makes the next one here from the latest ([NewAbstractDialog]), its date starting at [today].
 * "Monthly" shows the abstract of a month as of a date (starting at [today]), computed from the filed ones and never
 * saved ([ProjectAbstracts.monthly]): only "Print abstract" is there.
 * [onSave] and [onDelete] are null for roles that cannot upload.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActualAccomplishmentSection(
    project: Project,
    documents: List<PendingDocument>,
    abstracts: List<ProjectAbstract>,
    uploadedBy: String,
    onSave: ((ProjectAbstract) -> Unit)?,
    onDelete: ((ProjectAbstract) -> Unit)?,
    onPrintAbstract: (ProjectAbstract) -> Unit,
    onPrintBilling: (ProjectAbstract, BillingAbstract.Period) -> Unit,
    today: LocalDate = LocalDate.now(),
    payments: List<ProjectPayment> = emptyList(),
    units: Int = 1
) {
    val projectId = project.id
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Newest first; of two filed under one date on two devices, the one changed last (ProjectAbstracts.latest).
    val dated = abstracts.sortedWith(compareByDescending<ProjectAbstract> { it.asOf }.thenByDescending { it.updatedAt }).distinctBy { it.asOf }
    var chosen by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    val shown = dated.firstOrNull { it.asOf == chosen } ?: dated.firstOrNull()
    var note by remember(projectId) { mutableStateOf("") }
    var noteIsError by remember(projectId) { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<ProjectAbstract?>(null) }
    var deleting by remember { mutableStateOf<ProjectAbstract?>(null) }
    var billing by remember { mutableStateOf<ProjectAbstract?>(null) }
    var making by rememberSaveable { mutableStateOf(false) }
    billing?.let { record ->
        BillingFiguresDialog(
            record, onDismiss = { billing = null },
            warnings = { period -> BillingAbstract.printWarnings(record, BillingAbstract.of(project, documents, payments, record, period, units)) }
        ) { period -> billing = null; onPrintBilling(record, period) }
    }

    fun save(record: ProjectAbstract, replaced: Boolean) {
        onSave?.invoke(record)
        chosen = record.asOf
        noteIsError = false
        note = "${if (replaced) "Replaced" else "Saved"} the abstract as of ${ProjectAbstracts.summary(record)}."
    }
    if (making) NewAbstractDialog(abstracts, uploadedBy, units, today, onDismiss = { making = false }) { record, replaced -> making = false; save(record, replaced) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (read, fileName) = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { AbstractExcel.read(it) } } to runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull().orEmpty()
            }
            val found = read.getOrNull()
            val record = found?.let { ProjectAbstracts.record(projectId, it, fileName, uploadedBy) }
            val error = when {
                read.isFailure -> "That file could not be read. Pick the abstract saved as an Excel workbook (.xlsx)."
                found == null -> "No Abstract of Physical Accomplishment was found in that file."
                found.billing -> "That is the billing abstract. Pick the abstract of the actual accomplishment."
                record == null -> "That abstract has no \"As of\" date, so it cannot be filed. Check the line under its title."
                else -> null
            }
            when {
                error != null || record == null -> { noteIsError = true; note = error.orEmpty() }
                dated.any { it.asOf == record.asOf } -> replacing = record
                else -> save(record, replaced = false)
            }
        }
    }
    replacing?.let { record ->
        AlertDialog(
            onDismissRequest = { replacing = null },
            title = { Text("Replace the abstract as of ${ProjectSchedule.displayDate(record.asOf)}?") },
            text = { Text("An abstract with that date is already uploaded. The file you picked (overall ${ProjectAbstracts.pct(record.overallPct)}) takes its place.") },
            confirmButton = { TextButton(onClick = { replacing = null; save(record, replaced = true) }, modifier = Modifier.testTag("confirm_replace_abstract")) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { replacing = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete the abstract as of ${ProjectSchedule.displayDate(record.asOf)}?") },
            text = { Text("The project's actual accomplishment goes back to the latest abstract left; with none left, to the latest weekly report.") },
            confirmButton = {
                TextButton(
                    onClick = { deleting = null; chosen = null; note = ""; onDelete?.invoke(record) },
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                    modifier = Modifier.testTag("confirm_delete_abstract")
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    var monthly by rememberSaveable(projectId) { mutableStateOf(false) }
    var monthlyDate by rememberSaveable(projectId) { mutableStateOf(today.toString()) }
    val month = remember(abstracts, monthlyDate) { ProjectAbstracts.monthly(abstracts, monthlyDate) }
    val inMonthly = monthly && shown != null
    // The abstract drawn: the month's computed one in Monthly (none when its date has no abstract), else the chosen one.
    val viewed = if (inMonthly) month.abstract else shown
    val rows = remember(viewed?.rowsJson) { viewed?.let { ProjectAbstracts.rows(it.rowsJson) }.orEmpty() }
    val parts = remember(rows) { AbstractExcel.parts(rows) }
    // The parts folded shut, by their place among the parts.
    val folded = remember(viewed?.uuid) { mutableStateListOf<Int>() }

    // One for the whole table: the headings and every row move sideways together.
    val sideways = rememberScrollState()
    LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize().testTag("actual_accomplishment")) {
        // The blocks stand 8dp apart; the rows of the table touch.
        var gap = 0.dp
        fun block(key: Any? = null, content: @Composable () -> Unit) {
            val top = gap
            gap = 8.dp
            item(key = key) { Box(Modifier.padding(top = top)) { content() } }
        }
        if (shown != null) block {
            ChipRow(listOf("Weekly", "Monthly"), if (monthly) "Monthly" else "Weekly", PaddingValues(0.dp)) { monthly = it == "Monthly"; note = "" }
        }
        if (inMonthly) {
            block { DateField("As of", monthlyDate, { monthlyDate = it }) }
            block {
                val to = month.to
                if (to == null) Text(month.error, style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.testTag("monthly_error"))
                else Text(ProjectAbstracts.monthlyNote(to, month.from), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.testTag("monthly_note"))
            }
        }
        if (shown != null && !inMonthly) block {
            val labels = dated.map { ProjectSchedule.displayDate(it.asOf) }
            Column {
                Text("As of", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                ChipRow(labels, ProjectSchedule.displayDate(shown.asOf), PaddingValues(0.dp)) { label -> chosen = dated[labels.indexOf(label)].asOf; note = "" }
            }
        }
        if (!inMonthly && onSave != null) block {
            // The three do not fit one line of a 360dp phone.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onSave != null) OutlinedButton(onClick = { picker.launch(arrayOf(XLSX)) }, modifier = Modifier.testTag("upload_abstract")) {
                    Text("Upload abstract (Excel)")
                }
                if (onSave != null && shown != null) OutlinedButton(onClick = { note = ""; making = true }, modifier = Modifier.testTag("new_abstract")) { Text("New abstract") }
            }
        }
        if (note.isNotBlank()) block {
            Text(note, style = MaterialTheme.typography.bodySmall, color = if (noteIsError) scheme.error else scheme.onSurfaceVariant, modifier = Modifier.testTag("abstract_note"))
        }
        if (shown == null) {
            block {
                EmptyLine("No abstract uploaded yet. Upload the Abstract of Physical Accomplishment of the actual work (the \"As of\" one), saved as an Excel workbook (.xlsx).")
            }
        }
        if (viewed == null) return@LazyColumn
        block {
            SectionCard("Abstract of Physical Accomplishment", Icons.Default.TableChart) {
                Field("As of", ProjectSchedule.displayDate(viewed.asOf))
                StandingRow("", listOf("Target", "Actual", "+/-"), header = true)
                remember(project, documents, viewed) { ProjectAbstracts.standing(project, documents, viewed) }.forEach { row ->
                    StandingRow(row.name, listOf(row.target?.let(ProjectAbstracts::pct) ?: "-", ProjectAbstracts.pct(row.actual), ProjectAbstracts.diffText(row.diff)))
                }
                val inUnits = remember(viewed, abstracts, payments, units) { BillingAbstract.inUnits(viewed, abstracts, payments, units) }
                // A Community Facility has no lots or units to bill by.
                if (!inMonthly && inUnits.isNotEmpty() && !com.example.data.model.isCommunityFacility(project.projectType)) Column(Modifier.padding(top = 8.dp).testTag("units_table")) {
                    // The contract's lots or units go with the name: a fifth column cuts "Contract" in two on a 360dp phone.
                    StandingRow("In lots and units", listOf("Done to date", "Billed", "Can still bill"), header = true)
                    inUnits.forEach { row ->
                        StandingRow("${row.name}, ${row.contract} ${row.unit}", listOf(ProjectAbstracts.quantityText(row.done), ProjectAbstracts.quantityText(row.billed), "${row.canBill}"))
                    }
                    val billed = if (abstracts.any { it.asOf > viewed.asOf }) "the billings dated up to ${ProjectSchedule.displayDate(viewed.asOf)}" else "every saved billing"
                    Text(
                        "At the billing abstract's unit costs (each line's amount ÷ $units). Billed: $billed. Not printed.",
                        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                // The month's abstract is not a record: it has no file, no maker and no billing.
                if (!inMonthly) {
                    // One made in the app (ProjectAbstracts.nextRecord) has no file.
                    Field("File", viewed.fileName.ifBlank { "Made in the app" })
                    Field(if (viewed.fileName.isBlank()) "Made by" else "Uploaded by", viewed.uploadedBy.ifBlank { "-" })
                }
                OutlinedButton(onClick = { onPrintAbstract(viewed) }, modifier = Modifier.padding(top = 8.dp).testTag("print_abstract")) { Text("Print abstract") }
                if (!inMonthly) OutlinedButton(onClick = { billing = viewed }, modifier = Modifier.testTag("print_billing_abstract")) { Text("Print billing abstract") }
                // Delete closes the abstract it deletes, never beside New abstract (as on the website).
                if (!inMonthly && onDelete != null) {
                    HorizontalDivider(color = scheme.outlineVariant, modifier = Modifier.padding(top = 8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { deleting = viewed },
                            colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                            modifier = Modifier.testTag("delete_abstract")
                        ) { Text("Delete this abstract") }
                    }
                }
            }
        }
        var part = -1
        var headed = false // whether the column headings stand above the next row: a part's heading breaks the table
        rows.forEachIndexed { i, row ->
            if (row.kind == "part") part++
            val index = part
            if (row.kind == "part") headed = false
            if (row.kind == "part") block("part$i") {
                val open = index !in folded
                Surface(
                    color = scheme.surfaceVariant, shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().clickable { if (open) folded += index else folded -= index }.testTag("abstract_part_$index")
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${row.no}  ${row.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                            val line = "Amount ${money(parts[index].amount)} · To date ${ProjectAbstracts.pct(parts[index].actualPct)}"
                            Text(line, style = MaterialTheme.typography.bodySmall.figures(line), color = scheme.onSurfaceVariant)
                        }
                        Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Hide its items" else "Show its items", tint = scheme.onSurfaceVariant)
                    }
                }
            }
            val inTable = when (row.kind) {
                // A part that is its own pay item (Land Acquisition) has its figures on the first row under its heading.
                "part" -> ProjectAbstracts.isPayLine(row) && index !in folded
                // The grand total stays in sight whatever is folded.
                "total" -> true
                else -> index !in folded
            }
            if (inTable) {
                if (!headed) {
                    headed = true
                    item(key = "columns$i") { ItemsHeader(sideways) }
                }
                item(key = i) { ItemRow(row, i, sideways) }
            }
        }
    }
}
