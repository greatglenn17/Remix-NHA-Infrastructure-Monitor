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

    /** "Mar 14, 2025: overall 10.73%", the wording the messages and the audit entries share. */
    fun summary(abstract: ProjectAbstract): String = "${ProjectSchedule.displayDate(abstract.asOf)}: overall ${pct(abstract.overallPct)}"

    fun pct(value: Double): String = "%.2f%%".format(Locale.US, value)
}
