package com.example.util

import com.example.data.model.ACCOMPLISHMENT_LINES
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.WeeklyReport
import com.example.data.model.accomplishmentLine
import com.example.data.model.isCommunityFacility
import com.example.data.model.lineAverages
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale

/**
 * The uploaded abstracts of a project (Actual Accomplishment tab): each is kept under its "As of" date, and the latest
 * is the project's actual accomplishment. web/logic.js has the website's copy (abstractRows, latestAbstract,
 * abstractForWeek, abstractFields, abstractLineAmounts, abstractPeriodAmounts, abstractToDateAmounts, abstractStanding, projectActualAfter,
 * monthlyAbstract, monthlyAbstractNote); change both together.
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
                numbers("prev", 3), numbers("period", 3), numbers("toDate", 3), numbers("balance", 2), numbers("equiv", 3),
                o.optJSONArray("was")?.let { a -> List(a.length()) { j -> a.optJSONArray(j).let { t -> List(3) { k -> if (t == null || t.isNull(k)) null else t.optDouble(k).takeUnless { it.isNaN() } } } } }.orEmpty()
            )
        }
    }.getOrDefault(emptyList())

    /** The rows as a record keeps them: the same keys the website writes, an empty cell as null. */
    fun rowsJson(rows: List<AbstractExcel.Row>): String = JSONArray(rows.map { r ->
        fun array(values: List<Double?>) = JSONArray(values.map { it ?: JSONObject.NULL })
        JSONObject().put("kind", r.kind).put("no", r.no).put("name", r.name).put("qty", r.qty ?: JSONObject.NULL).put("unit", r.unit)
            .put("unitCost", r.unitCost ?: JSONObject.NULL).put("amount", r.amount ?: JSONObject.NULL)
            .put("prev", array(r.prev)).put("period", array(r.period)).put("toDate", array(r.toDate)).put("balance", array(r.balance)).put("equiv", array(r.equiv))
            .apply { if (r.was.isNotEmpty()) put("was", JSONArray(r.was.map(::array))) }
    }).toString()

    /** The abstract of the latest "As of" date; of two with one date (uploaded on two devices), the one changed last. */
    fun latest(abstracts: List<ProjectAbstract>): ProjectAbstract? = abstracts.maxWithOrNull(compareBy({ it.asOf }, { it.updatedAt }))

    /** The latest abstract dated on or before [reportingWeek]'s last day: the one a weekly report of that week takes its actual from; null when there is none. */
    fun forWeek(abstracts: List<ProjectAbstract>, reportingWeek: String): ProjectAbstract? {
        val last = BlueBookForms.weekStart(reportingWeek)?.plusDays(6)?.toString() ?: return null
        return latest(abstracts.filter { it.asOf <= last })
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
        AbstractExcel.actuals(AbstractExcel.parts(rows(abstract.rowsJson))).let { actual -> ACCOMPLISHMENT_LINES.map { shown(actual[it] ?: 0.0) } }

    // BigDecimal of the double rounds a tie as the website's toFixed does (25.215 is stored just under it); an overrun part reads 100.
    private fun shown(pct: Double) = java.math.BigDecimal(pct.coerceAtMost(100.0)).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

    /**
     * An abstract's part as a community facility's work (I. General Requirements, II. ...), a line of its weekly report:
     * [amount] and [actualPct] are its own subtotal's, [names] its own name and those of the rows under it, which
     * [workOf] matches a bar chart item against. web/logic.js abstractWorks, nameLikeness and workOf are the website's.
     */
    data class Work(val name: String, val amount: Double, val actualPct: Double, val names: List<String>)

    fun works(abstract: ProjectAbstract): List<Work> {
        val works = mutableListOf<Work>()
        for (r in rows(abstract.rowsJson)) when {
            r.kind == "part" -> works += Work(r.name, 0.0, 0.0, listOf(r.name))
            works.isEmpty() || r.kind == "total" -> Unit
            r.kind == "subtotal" -> works[works.lastIndex] = works.last().copy(amount = r.amount ?: 0.0, actualPct = r.toDate.getOrNull(2) ?: 0.0)
            else -> works[works.lastIndex] = works.last().let { it.copy(names = it.names + r.name) }
        }
        return works
    }

    /** Each work's actual % as the weekly form shows it, by the work's name. */
    fun workActuals(abstract: ProjectAbstract): Map<String, Double> = works(abstract).associate { it.name to shown(it.actualPct) }

    /**
     * How alike two names are, 0 to 1: the share of the pairs of neighbouring characters they have in common (letters
     * and digits only, any case), so a typo ("Misccellaneous Works") or a shorter wording ("Construction of Perimeter
     * Fence" for "... Fence and Gate") still matches.
     */
    fun likeness(a: String, b: String): Double {
        fun pairs(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().windowed(2).toSet()
        val x = pairs(a)
        val y = pairs(b)
        return if (x.isEmpty() || y.isEmpty()) 0.0 else 2.0 * x.count { it in y } / (x.size + y.size)
    }

    /**
     * The index in [works] of the work a bar chart item belongs to: the one with the name most like the item's among
     * each work's own name and its rows' (a chart can list "Construction of Guard House" on its own while the abstract
     * has it under Architectural Works); the first on a tie, -1 when none is at least half alike.
     */
    fun workOf(itemName: String, works: List<Work>): Int {
        var best = -1
        var top = 0.5
        works.forEachIndexed { k, w ->
            val score = w.names.maxOf { likeness(itemName, it) }
            if (score > top || (best < 0 && score == top)) { best = k; top = score }
        }
        return best
    }

    /**
     * Each work's target %: the chart's plan ([itemTargets], by item name) for the chart [items] that belong to the
     * work, by their amounts; 0 for a work with no chart item.
     */
    fun workTargets(works: List<Work>, items: List<BarChart.Item>, itemTargets: Map<String, Double>): Map<String, Double> {
        val planned = works.map { doubleArrayOf(0.0, 0.0) } // each work's chart amount, and that amount by its items' targets
        for (i in items) {
            val k = workOf(i.name, works)
            if (k >= 0) { planned[k][0] += i.amount; planned[k][1] += i.amount * (itemTargets[i.name] ?: 0.0) }
        }
        return works.mapIndexed { k, w -> w.name to if (planned[k][0] > 0) planned[k][1] / planned[k][0] else 0.0 }.toMap()
    }

    /** Each accomplishment line's sum, at 2 decimals, of [of] over the own subtotal rows of the abstract's parts of that line. */
    private fun lineSums(abstract: ProjectAbstract, of: (AbstractExcel.Row) -> Double?): List<Double> {
        val parts = AbstractExcel.partSubtotals(rows(abstract.rowsJson))
        return ACCOMPLISHMENT_LINES.map { line -> round2(parts.filter { accomplishmentLine(it.first.name) == line }.sumOf { (_, subtotal) -> subtotal?.let(of) ?: 0.0 }) }
    }

    /** The contract amount of each accomplishment line as the abstract has it (its parts' amounts). */
    fun lineAmounts(abstract: ProjectAbstract): List<Double> = lineSums(abstract) { it.amount }

    /** Each accomplishment line's THIS PERIOD amount on the abstract; 0 where the sheet has none. */
    fun periodAmounts(abstract: ProjectAbstract): List<Double> = lineSums(abstract) { it.period.getOrNull(1) }

    /** Each accomplishment line's TO DATE amount on the abstract; 0 where the sheet has none. */
    fun toDateAmounts(abstract: ProjectAbstract): List<Double> = lineSums(abstract) { it.toDate.getOrNull(1) }

    /** A row of the tab's Target / Actual / +/- table; [target] and [diff] are null without a bar chart. */
    data class Standing(val name: String, val target: Double?, val actual: Double, val diff: Double?)

    /**
     * The tab's table for an abstract: the three accomplishment lines, then "Overall". A line's target is the weekly
     * form's (the approved bar chart's plan for that line on the "As of" date; null without a chart), the overall the
     * chart's plan that day, else the project's stored target. The actuals are the abstract's. The difference is the
     * actual less the target, each at 2 decimals. A Community Facility's rows are its abstract's works, as on its weekly
     * form (none when the abstract has no works to read), then "Overall".
     */
    fun standing(project: Project, documents: List<PendingDocument>, abstract: ProjectAbstract): List<Standing> {
        val plan = runCatching { LocalDate.parse(abstract.asOf.trim()) }.getOrNull()?.let { BarChart.planOn(project, documents, it) }
        val charted = plan?.takeIf { it.version.total > 0 }
        fun row(name: String, target: Double?, actual: Double) = Standing(name, target, actual, target?.let { round2(round2(actual) - round2(it)) })
        val lines = if (isCommunityFacility(project.projectType)) {
            val works = works(abstract).takeIf { all -> all.sumOf { it.amount } > 0 }.orEmpty()
            val targets = charted?.let { workTargets(works, it.version.items, it.itemTargets) }
            works.map { row(it.name, targets?.let { t -> round2(t[it.name] ?: 0.0) }, shown(it.actualPct)) }
        } else {
            val targets = charted?.let { p -> lineAverages(p.version.items, { it.name }, { it.amount / p.version.total * 100 }, { p.itemTargets[it.name] ?: 0.0 }) }
            ACCOMPLISHMENT_LINES.zip(lineActuals(abstract)) { line, actual -> row(line, targets?.let { round2(it[line] ?: 0.0) }, actual) }
        }
        return lines + row("Overall", plan?.plannedPct ?: project.targetAccomplishment, abstract.overallPct)
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
       abstractHouseLines, abstractHouses, nextAbstractRows, nextAbstractErrors, nextAbstractFields, nextAbstractNote, quantityText); change both together. */

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
     * The indexes of the pay lines of a Housing Construction part, which the New abstract takes in houses; none when
     * [units], the project's housing units, is only the fallback 1 ([BillingAbstract.units]).
     */
    fun houseLines(rows: List<AbstractExcel.Row>, units: Int): List<Int> {
        if (units <= 1) return emptyList()
        var line = ""
        return rows.indices.filter { i -> if (rows[i].kind == "part") line = accomplishmentLine(rows[i].name); line == ACCOMPLISHMENT_LINES[2] && isPayLine(rows[i]) }
    }

    /**
     * [typed] (by row index, [rows] the source's) with each house line's houses this period as its quantity, each house
     * QTY / [units]. It is worked from the houses to date, so the last house gives the QTY exactly however the abstracts
     * before it rounded: the to date is the houses to date x QTY / [units] at 4 decimals, the QTY itself within 0.01
     * house of [units], and this period the to date less the previous. [back] turns this period's quantities into
     * houses. The rest, 0 and a NaN (a box that is not a number) stay.
     */
    fun houses(rows: List<AbstractExcel.Row>, typed: Map<Int, Double>, units: Int, back: Boolean = false): Map<Int, Double> {
        val lines = houseLines(rows, units).toSet()
        return typed.mapValues { (i, v) ->
            val qty = rows[i].qty ?: 0.0
            val prev = rows[i].toDate.getOrNull(0) ?: 0.0
            when {
                i !in lines || v == 0.0 || !v.isFinite() -> v
                back -> round4(v * units / qty)
                else -> round4((if (kotlin.math.abs(prev * units / qty + v - units) < 0.01) qty else round4(prev + v * qty / units)) - prev)
            }
        }
    }

    /**
     * The most each pay line's box takes this period, by row index ([rows] the source's): what is left of its QTY, in
     * houses on a house line ([houses]), so typing more cannot pass the QTY. A line already past its QTY takes 0 more.
     * A quantity is cut to 4 decimals, so its to date stays within the QTY; houses round, and a sliver left is 0.0001
     * house, since the last house's 0.01 snap takes either to the QTY exactly.
     */
    fun nextMost(rows: List<AbstractExcel.Row>, units: Int): Map<Int, Double> {
        val lines = houseLines(rows, units).toSet()
        return rows.indices.filter { isPayLine(rows[it]) && (rows[it].qty ?: 0.0) > 0 }.associateWith { i ->
            val qty = rows[i].qty!!
            val left = maxOf(0.0, qty - (rows[i].toDate.getOrNull(0) ?: 0.0))
            val h = left * units / qty
            if (i in lines) (if (h > 0) maxOf(round4(h), 0.0001) else 0.0) else kotlin.math.floor(left * 1e4 + 1e-6) / 1e4
        }
    }

    /** A box's [text], or [most] in its place when it is a number above [most], as the website's cappedBox. */
    fun capped(text: String, most: Double?): String {
        val n = text.replace(",", "").trim()
        return if (most != null && Regex("""-?(\d+(\.\d*)?|\.\d+)""").matches(n) && n.toDouble() > most) java.math.BigDecimal.valueOf(most).stripTrailingZeros().toPlainString() else text
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

    /** The abstract of a month ([monthly]): the [abstract] to show, the two it was made from, and [error], why there is none ("" when there is). */
    data class Monthly(val abstract: ProjectAbstract?, val to: ProjectAbstract?, val from: ProjectAbstract?, val error: String)

    /**
     * The abstract of a month, computed and never saved. `to` is the latest abstract filed on or before [date]
     * (yyyy-MM-dd) and gives every row with its TO DATE; `from` is the latest one filed by the end of the month before
     * (null with none) and gives PREVIOUS, row by row (the first row of `from` not yet matched with the same kind, item
     * no. and name; 0 without one). THIS PERIOD is the difference. A cell with no TO DATE stays empty. The abstract is
     * dated [date].
     */
    fun monthly(abstracts: List<ProjectAbstract>, date: String): Monthly {
        val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return Monthly(null, null, null, "Pick the date of the monthly abstract.")
        fun upTo(last: String) = latest(abstracts.filter { it.asOf <= last })
        val to = upTo(date) ?: return Monthly(null, null, null, "No abstract is filed on or before ${ProjectSchedule.displayDate(date)}.")
        val from = upTo(day.withDayOfMonth(1).minusDays(1).toString())
        // A row of `from` is taken out once matched, so two rows named "Subtotal" match in order.
        val before = from?.let { rows(it.rowsJson) }.orEmpty().toMutableList<AbstractExcel.Row?>()
        val rows = rows(to.rowsJson).map { r ->
            val at = before.indexOfFirst { it != null && it.kind == r.kind && it.no == r.no && it.name == r.name }
            val p = before.getOrNull(at)
            if (at >= 0) before[at] = null
            val prev = List(3) { k -> r.toDate.getOrNull(k)?.let { p?.toDate?.getOrNull(k) ?: 0.0 } }
            r.copy(prev = prev, period = List(3) { k -> prev[k]?.let { was -> (r.toDate[k] ?: 0.0) - was }?.let { if (k == 0) round4(it) else round2(it) } })
        }
        return Monthly(
            ProjectAbstract(projectId = to.projectId, asOf = date, overallPct = to.overallPct, rowsJson = rowsJson(rows), fileName = "", uploadedBy = "", equivUnits = to.equivUnits),
            to, from, ""
        )
    }

    /** The Monthly view's line under its date: the two abstracts it was made from, word for word on the website. */
    fun monthlyNote(to: ProjectAbstract, from: ProjectAbstract?): String = "To date is the abstract as of ${ProjectSchedule.displayDate(to.asOf)}. " +
        (from?.let { "Previous is the abstract as of ${ProjectSchedule.displayDate(it.asOf)}." } ?: "Previous is zero: no abstract was filed by the end of the month before.")

    /** "Mar 14, 2025: overall 10.73%", the wording the messages and the audit entries share. */
    fun summary(abstract: ProjectAbstract): String = "${ProjectSchedule.displayDate(abstract.asOf)}: overall ${pct(abstract.overallPct)}"

    fun pct(value: Double): String = "%.2f%%".format(Locale.US, value)
}
