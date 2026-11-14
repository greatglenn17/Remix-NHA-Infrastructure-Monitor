package com.example.util

import com.example.data.model.ACCOMPLISHMENT_LINES
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.WeeklyReport
import com.example.data.model.accomplishmentLine
import com.example.data.model.lineAverages
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * The uploaded abstracts of a project (Actual Accomplishment tab): each is kept under its "As of" date, and the latest
 * is the project's actual accomplishment. web/logic.js has the website's copy (abstractRows, latestAbstract,
 * abstractInWeek, abstractFields, abstractLineAmounts, abstractPeriodAmounts, abstractStanding, projectActualAfter);
 * change both together.
 */
object ProjectAbstracts {
    private fun round2(v: Double) = Math.round(v * 100) / 100.0

    /** A record's rowsJson as rows; empty when it does not read. */
    fun rows(json: String): List<AbstractExcel.Row> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            fun number(key: String) = if (o.isNull(key)) null else o.optDouble(key).takeUnless { it.isNaN() }
            fun numbers(key: String, size: Int) = o.optJSONArray(key).let { a ->
                List(size) { k -> if (a == null || a.isNull(k)) null else a.optDouble(k).takeUnless { it.isNaN() } }
            }
            AbstractExcel.Row(
                o.optString("kind"), o.optString("no"), o.optString("name"), number("qty"), o.optString("unit"), number("unitCost"), number("amount"),
                numbers("prev", 3), numbers("period", 3), numbers("toDate", 3), numbers("balance", 2), numbers("equiv", 3)
            )
        }
    }.getOrDefault(emptyList())

    /** The rows as a record keeps them: the same keys the website writes, an empty cell as null. */
    fun rowsJson(rows: List<AbstractExcel.Row>): String = JSONArray(rows.map { r ->
        fun array(values: List<Double?>) = JSONArray(values.map { it ?: JSONObject.NULL })
        JSONObject().put("kind", r.kind).put("no", r.no).put("name", r.name).put("qty", r.qty ?: JSONObject.NULL).put("unit", r.unit)
            .put("unitCost", r.unitCost ?: JSONObject.NULL).put("amount", r.amount ?: JSONObject.NULL)
            .put("prev", array(r.prev)).put("period", array(r.period)).put("toDate", array(r.toDate)).put("balance", array(r.balance)).put("equiv", array(r.equiv))
    }).toString()

    /** The abstract of the latest "As of" date; of two with one date (uploaded on two devices), the one changed last. */
    fun latest(abstracts: List<ProjectAbstract>): ProjectAbstract? = abstracts.maxWithOrNull(compareBy({ it.asOf }, { it.updatedAt }))

    /** The latest abstract dated within [reportingWeek]'s seven days: the one a weekly report of that week takes its actual from. */
    fun inWeek(abstracts: List<ProjectAbstract>, reportingWeek: String): ProjectAbstract? {
        val start = BlueBookForms.weekStart(reportingWeek) ?: return null
        val days = (0L..6L).map { start.plusDays(it).toString() }
        return latest(abstracts.filter { it.asOf in days })
    }

    /** A read workbook as the record to save, or null when it has no "As of" date to file it under. */
    fun record(projectId: Long, found: AbstractExcel.Abstract, fileName: String, uploadedBy: String): ProjectAbstract? {
        val asOf = found.asOf ?: return null
        val total = found.parts.sumOf { it.amount }
        // Without a grand total row, the parts' % weighted by their contract amounts.
        val overall = found.overallPct ?: if (total > 0) Math.round(found.parts.sumOf { it.amount * it.actualPct } / total * 100) / 100.0 else 0.0
        return ProjectAbstract(projectId = projectId, asOf = asOf.toString(), overallPct = overall, rowsJson = rowsJson(found.rows), fileName = fileName, uploadedBy = uploadedBy, equivUnits = found.equivUnits)
    }

    /** The three lines' actual % of an abstract at 2 decimals (the website's "25.21"), in the order of ACCOMPLISHMENT_LINES; a line it has no part for is 0. */
    fun lineActuals(abstract: ProjectAbstract): List<Double> =
        AbstractExcel.actuals(AbstractExcel.parts(rows(abstract.rowsJson))).let { actual -> ACCOMPLISHMENT_LINES.map {
            // BigDecimal of the double rounds a tie as the website's toFixed does (25.215 is stored just under it); an overrun part reads 100.
            java.math.BigDecimal((actual[it] ?: 0.0).coerceAtMost(100.0)).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
        } }

    /** Each accomplishment line's sum, at 2 decimals, of [of] over the own subtotal rows of the abstract's parts of that line. */
    private fun lineSums(abstract: ProjectAbstract, of: (AbstractExcel.Row) -> Double?): List<Double> {
        val parts = AbstractExcel.partSubtotals(rows(abstract.rowsJson))
        return ACCOMPLISHMENT_LINES.map { line -> round2(parts.filter { accomplishmentLine(it.first.name) == line }.sumOf { (_, subtotal) -> subtotal?.let(of) ?: 0.0 }) }
    }

    /** The contract amount of each accomplishment line as the abstract has it (its parts' amounts). */
    fun lineAmounts(abstract: ProjectAbstract): List<Double> = lineSums(abstract) { it.amount }

    /** Each accomplishment line's THIS PERIOD amount on the abstract; 0 where the sheet has none. */
    fun periodAmounts(abstract: ProjectAbstract): List<Double> = lineSums(abstract) { it.period.getOrNull(1) }

    /** A row of the tab's Target / Actual / +/- table; [target] and [diff] are null without a bar chart. */
    data class Standing(val name: String, val target: Double?, val actual: Double, val diff: Double?)

    /**
     * The tab's table for an abstract: the three accomplishment lines, then "Overall". A line's target is the weekly
     * form's (the approved bar chart's plan for that line on the "As of" date; null without a chart), the overall the
     * chart's plan that day, else the project's stored target. The actuals are the abstract's. The difference is the
     * actual less the target, each at 2 decimals.
     */
    fun standing(project: Project, documents: List<PendingDocument>, abstract: ProjectAbstract): List<Standing> {
        val plan = runCatching { LocalDate.parse(abstract.asOf.trim()) }.getOrNull()?.let { BarChart.planOn(project, documents, it) }
        val total = plan?.version?.total ?: 0.0
        val targets = plan?.takeIf { total > 0 }?.let { p -> lineAverages(p.version.items, { it.name }, { it.amount / total * 100 }, { p.itemTargets[it.name] ?: 0.0 }) }
        fun row(name: String, target: Double?, actual: Double) = Standing(name, target, actual, target?.let { round2(round2(actual) - round2(it)) })
        return ACCOMPLISHMENT_LINES.zip(lineActuals(abstract)) { line, actual -> row(line, targets?.let { round2(it[line] ?: 0.0) }, actual) } +
            row("Overall", plan?.plannedPct ?: project.targetAccomplishment, abstract.overallPct)
    }

    /** A standing row's +/- as shown: "+4.66%", "-7.58%", "0.00%"; "-" without a target. */
    fun diffText(diff: Double?): String = diff?.let { (if (it > 0) "+" else "") + pct(it) } ?: "-"

    /**
     * What the project's actual accomplishment is once an abstract is saved or deleted: the latest abstract's overall;
     * with none left the latest weekly report's; with neither, [current].
     */
    fun projectActualAfter(abstracts: List<ProjectAbstract>, weekly: List<WeeklyReport>, current: Double): Double =
        latest(abstracts)?.overallPct ?: BillingAbstract.latestWeekly(weekly)?.actualAccomplishmentPct ?: current

    /* The next abstract, made in the app from the one before it, by the sheet's own formulas. web/logic.js has the
       website's copy (isPayLine, abstractCover, nextAbstractProblem, nextAbstractSource, nextAbstractTyped,
       nextAbstractRows, nextAbstractErrors, nextAbstractFields, nextAbstractNote, quantityText); change both together. */

    private fun round4(v: Double) = Math.round(v * 10000) / 10000.0

    /** A row the engineer types a quantity for: a pay item, or a part that is its own pay item (Land Acquisition), with a quantity and an amount. */
    fun isPayLine(r: AbstractExcel.Row): Boolean = (r.kind == "item" || r.kind == "part") && (r.qty ?: 0.0) > 0 && r.amount != null

    /**
     * For each subtotal and the grand total (by row index), the indexes of the pay lines it adds up. A subtotal right
     * after another one is its part's (every line since the part row); any other, the lines since the subtotal before
     * it or the part row; the grand total, every line.
     */
    fun cover(rows: List<AbstractExcel.Row>): Map<Int, List<Int>> {
        val cover = linkedMapOf<Int, List<Int>>()
        var part = 0
        var group = 0
        rows.forEachIndexed { i, r ->
            if (r.kind == "part") { part = i; group = i }
            if (r.kind != "subtotal" && r.kind != "total") return@forEachIndexed
            val from = if (r.kind == "total") 0 else if (rows.getOrNull(i - 1)?.kind == "subtotal") part else group
            cover[i] = (from until i).filter { isPayLine(rows[it]) }
            group = i + 1
        }
        return cover
    }

    /** Why the next abstract cannot be made from [abstract], "" when it can: its subtotals must add up from its pay items. */
    fun nextProblem(abstract: ProjectAbstract): String {
        val rows = rows(abstract.rowsJson)
        val from = "the abstract as of ${ProjectSchedule.displayDate(abstract.asOf)}"
        val off = cover(rows).entries.firstOrNull { (i, lines) -> Math.abs(lines.sumOf { rows[it].amount ?: 0.0 } - (rows[i].amount ?: 0.0)) > 1 }
        return when {
            rows.none { it.kind == "total" } || rows.none(::isPayLine) -> "There are no pay items with a grand total in $from, so the next one cannot be made from it. Upload the next abstract instead."
            off != null -> "\"${rows[off.key].name}\" does not add up from its items in $from, so the next one cannot be made from it. Upload the next abstract instead."
            else -> ""
        }
    }

    /** What an abstract made in the app continues: [source], whose TO DATE is the new PREVIOUS; [same], the one already filed for that date; [error], why the date cannot be used. */
    data class NextSource(val source: ProjectAbstract?, val same: ProjectAbstract?, val error: String)

    /** The abstract of [asOf] (yyyy-MM-dd) continues the latest one dated before it; saving replaces the one already filed for that date. */
    fun nextSource(abstracts: List<ProjectAbstract>, asOf: String): NextSource {
        val latest = latest(abstracts) ?: return NextSource(null, null, "No abstract uploaded yet.")
        val source = latest(abstracts.filter { it.asOf < asOf })
        val error = when {
            asOf.isEmpty() -> "Pick the \"As of\" date."
            asOf < latest.asOf -> "The date must not be before the latest abstract's, ${ProjectSchedule.displayDate(latest.asOf)}."
            source == null -> "The date must be after ${ProjectSchedule.displayDate(latest.asOf)}, the first abstract's."
            else -> nextProblem(source)
        }
        return NextSource(source, latest(abstracts.filter { it.asOf == asOf }), error)
    }

    /** This period's quantities of [same] (the abstract of the date being made again) by row index; empty when its rows are not the source's. */
    fun nextTyped(sourceRows: List<AbstractExcel.Row>, same: ProjectAbstract?): Map<Int, Double> {
        val rows = same?.let { rows(it.rowsJson) }.orEmpty()
        if (rows.size != sourceRows.size || rows.indices.any { rows[it].kind != sourceRows[it].kind || rows[it].no != sourceRows[it].no || rows[it].name != sourceRows[it].name }) return emptyMap()
        return rows.indices.filter { isPayLine(rows[it]) && rows[it].period.getOrNull(0) != null }.associateWith { rows[it].period[0]!! }
    }

    /**
     * The rows of the next abstract. [rows] are the source's, [typed] this period's quantity by row index (none typed
     * is no work this period). As on the sheet: PREVIOUS is the source's TO DATE; a pay line's THIS PERIOD amount is
     * the quantity at its unit cost and its % that amount over the line's amount; TO DATE is previous plus this
     * period; BALANCE the rest; the equivalent units the quantity's share of [equivUnits]. Subtotals and the grand
     * total add up their pay lines' THIS PERIOD amounts. Cells the sheet leaves blank stay blank.
     * One figure is not the sheet's: TO DATE % is the TO DATE amount over the row's amount, where the sheet adds the
     * rounded percentages, so a finished line reads 100.00 however many abstracts it took.
     */
    fun nextRows(rows: List<AbstractExcel.Row>, typed: Map<Int, Double>, equivUnits: Double = 0.0): List<AbstractExcel.Row> {
        val out = rows.toMutableList()
        rows.forEachIndexed { i, r ->
            if (!isPayLine(r)) return@forEachIndexed
            val qty = r.qty ?: return@forEachIndexed
            val contract = r.amount ?: return@forEachIndexed
            val q = typed[i]
            val n = q?.takeIf { it.isFinite() } ?: 0.0
            val prev = List(3) { r.toDate.getOrNull(it) ?: 0.0 }
            val amount = round2(n * (r.unitCost ?: (contract / qty)))
            val share = if (contract != 0.0) round2(amount / contract * 100) else 0.0
            val toDate = listOf(round4(prev[0] + n), round2(prev[1] + amount), if (contract != 0.0) round2(round2(prev[1] + amount) / contract * 100) else prev[2])
            val done = round2(toDate[0] / qty * equivUnits)
            out[i] = r.copy(
                prev = prev, period = listOf(q, amount, share), toDate = toDate, balance = listOf(round4(qty - toDate[0]), round2(contract - toDate[1])),
                equiv = if (equivUnits > 0) listOf(round2(prev[0] / qty * equivUnits), done, round2(equivUnits - done)) else listOf(null, null, null)
            )
        }
        for ((i, lines) in cover(rows)) {
            val r = rows[i]
            val contract = r.amount ?: 0.0
            val amount = round2(lines.sumOf { out[it].period.getOrNull(1) ?: 0.0 })
            val share = if (contract != 0.0) round2(amount / contract * 100) else 0.0
            val before = listOf(r.toDate.getOrNull(1) ?: 0.0, r.toDate.getOrNull(2) ?: 0.0)
            val done = round2(before[0] + amount)
            out[i] = r.copy(
                prev = listOf(null, before[0], before[1].takeIf { r.prev.getOrNull(2) != null }), period = listOf(null, amount, share.takeIf { r.period.getOrNull(2) != null }),
                toDate = listOf(null, done, if (contract != 0.0) round2(done / contract * 100) else before[1]), balance = listOf(null, r.balance.getOrNull(1)?.let { round2(contract - done) }), equiv = listOf(null, null, null)
            )
        }
        return out
    }

    /** A quantity as the abstract shows it: up to 4 decimals, with commas. */
    fun quantityText(v: Double): String =
        java.text.DecimalFormat("#,##0.####", java.text.DecimalFormatSymbols(Locale.US)).apply { roundingMode = java.math.RoundingMode.HALF_UP }.format(v) // a tie rounds up, as the website's does

    /** What stops the typed quantities from being saved, one line per pay line (a NaN is a box that is not a number); empty when they can. A line the abstract before it already has over its quantity is carried as it is: only more work on it is refused. */
    fun nextErrors(rows: List<AbstractExcel.Row>, typed: Map<Int, Double>): List<String> {
        val next = nextRows(rows, typed)
        return rows.indices.filter { isPayLine(rows[it]) }.mapNotNull { i ->
            val label = "${rows[i].no} ${rows[i].name}".trim()
            val qty = rows[i].qty ?: 0.0
            val done = next[i].toDate[0] ?: 0.0
            when {
                typed[i]?.isFinite() == false -> "$label: this period must be a number."
                done < 0 -> "$label: to date would go below zero."
                done > qty && (typed[i] ?: 0.0) > 0 -> "$label: to date ${quantityText(done)} would pass the quantity ${quantityText(qty)}."
                else -> null
            }
        }
    }

    /** The record of the abstract of [asOf] made from [source] with [rows] (nextRows); it has no file. */
    fun nextRecord(source: ProjectAbstract, rows: List<AbstractExcel.Row>, asOf: String, uploadedBy: String) = ProjectAbstract(
        projectId = source.projectId, asOf = asOf, overallPct = rows.firstOrNull { it.kind == "total" }?.toDate?.getOrNull(2) ?: 0.0,
        rowsJson = rowsJson(rows), fileName = "", uploadedBy = uploadedBy, equivUnits = source.equivUnits
    )

    /** The New abstract form's line: the overall its quantities give and what it continues, word for word on the website. */
    fun nextNote(source: ProjectAbstract, same: ProjectAbstract?, rows: List<AbstractExcel.Row>): String =
        "Overall to date ${pct(rows.firstOrNull { it.kind == "total" }?.toDate?.getOrNull(2) ?: 0.0)}, from ${pct(source.overallPct)} as of ${ProjectSchedule.displayDate(source.asOf)}." +
            if (same != null) " Saving replaces the abstract already filed for this date." else ""

    /** "Mar 14, 2025: overall 10.73%", the wording the messages and the audit entries share. */
    fun summary(abstract: ProjectAbstract): String = "${ProjectSchedule.displayDate(abstract.asOf)}: overall ${pct(abstract.overallPct)}"

    fun pct(value: Double): String = "%.2f%%".format(Locale.US, value)
}
