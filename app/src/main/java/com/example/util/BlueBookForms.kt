package com.example.util

import androidx.compose.ui.graphics.toArgb
import com.example.data.model.AccomplishmentItem
import com.example.data.model.DailyActivity
import com.example.data.model.DailyHourlyWeather
import com.example.data.model.DeveloperRequest
import com.example.data.model.EquipmentItem
import com.example.data.model.ISSUE_ACTION_BY
import com.example.data.model.ManpowerItem
import com.example.data.model.MonthlyReport
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectAbstract
import com.example.data.model.ProjectIssue
import com.example.data.model.ProjectPayment
import com.example.data.model.SdpSites
import com.example.data.model.WeatherCondition
import com.example.data.model.WeeklyIssue
import com.example.data.model.WeeklyReport
import com.example.data.model.netAmount
import com.example.ui.theme.WeatherCloudyYellow
import com.example.ui.theme.WeatherFairGreen
import com.example.ui.theme.WeatherRainShowersCyan
import com.example.ui.theme.WeatherRainyBlue
import com.example.ui.theme.WeatherStormyRed
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/** The person printing the form, for the "Prepared by" block. */
data class Preparer(val name: String, val title: String, val office: String)

/** The Prepared by / Checked by / Noted by block of the weekly and monthly reports. */
data class Signatories(
    val preparedName: String, val preparedPosition: String,
    val checkedName: String, val checkedPosition: String,
    val notedName: String, val notedPosition: String,
    val preparedOffice: String = "", val checkedOffice: String = "", val notedOffice: String = ""
)

data class Extension(val no: String, val days: Int, val reason: String, val status: String)

/** A suspension or variation as the forms list it, e.g. "Work Suspension Order No. 1", "146 calendar days – January 3, 2019 to May 29, 2019". */
data class Order(val label: String, val value: String, val reason: String, val status: String)

/**
 * The contract figures and orders the forms list, read from the project's Orders the same way
 * the Key Dates and Money cards read them. Orders not yet approved are marked "(for approval)".
 */
data class ContractLogs(
    val originalCost: Double,
    /** Null until a variation order is approved. */
    val revisedCost: Double?,
    val revisedCompletion: String,
    val suspensions: List<Order>,
    val resumptions: List<Pair<String, String>>,
    val extensions: List<Extension>,
    val variations: List<Order>
) {
    companion object {
        fun from(project: Project, documents: List<PendingDocument>): ContractLogs {
            fun field(remarks: String, key: String) = Regex("$key: ([^|]*)").find(remarks)?.groupValues?.get(1)?.trim().orEmpty()
            fun mark(d: PendingDocument) = if (d.status.equals("Approved", ignoreCase = true)) "" else " (for approval)"
            fun named(prefix: String) = documents.filter { it.documentName.startsWith(prefix, ignoreCase = true) }
            val original = ContractCost.original(project.scopeOfWork, project.contractCostOriginal)
            return ContractLogs(
                originalCost = original,
                revisedCost = ContractCost.revised(project.scopeOfWork, project.contractCostOriginal, documents),
                revisedCompletion = ProjectSchedule.revisedCompletion(project.dateStarted, project.contractDurationDays, documents)
                    ?: ProjectSchedule.effectiveCompletion(project.completionDateOriginal, project.completionDateRevised),
                // Continuances ("1st Continuance of Work Suspension Order No. 1") are listed with their suspension, without its reason again.
                suspensions = documents.filter { it.documentName.contains("Work Suspension", ignoreCase = true) }.map { d ->
                    val days = field(d.remarks, "Duration").substringBefore(" ").takeIf { it.isNotEmpty() }?.let { "$it calendar days – " }.orEmpty()
                    val from = BlueBookForms.date(field(d.remarks, "Effectivity"))
                    val to = field(ProjectSchedule.withEnd(d.remarks), "End")
                    Order(
                        ProjectSchedule.orderLabel(d.documentName), days + (if (to.isEmpty()) from else "$from to ${BlueBookForms.date(to)}"),
                        if (ProjectSchedule.isSuspension(d)) field(d.remarks, "Reason") else "", mark(d).trim()
                    )
                },
                resumptions = documents.filter { it.documentName.contains("Work Resumption", ignoreCase = true) }.map { d ->
                    ProjectSchedule.orderLabel(d.documentName) to BlueBookForms.date(field(d.remarks, "Effectivity")) + mark(d)
                },
                extensions = named("Time Extension").map { d ->
                    val m = Regex("Time Extension No\\. ?(\\S*) - (\\d+) Days", RegexOption.IGNORE_CASE).find(d.documentName)
                    Extension(m?.groupValues?.get(1).orEmpty(), m?.groupValues?.get(2)?.toIntOrNull() ?: 0, field(d.remarks, "Reason"), mark(d).trim())
                },
                variations = named("Variation Order").map { d ->
                    val amount = d.documentName.substringAfter(" - ", "")
                    Order(
                        d.documentName.substringBefore(" - ").substringBefore(" ("),
                        amount.removePrefix("PHP").replace(",", "").toDoubleOrNull()?.let(BlueBookForms::peso) ?: amount.replace("PHP", "Php"),
                        field(d.remarks, "Reason"), mark(d).trim()
                    )
                }
            )
        }
    }
}

/**
 * Builds the NHA Blue Book forms (Project Profile, weekly and monthly Project Engineer's
 * Report) as A4 HTML pages filled from the app's own records, for [BlueBookPrint].
 */
object BlueBookForms {

    fun projectProfile(project: Project, logs: ContractLogs, preparer: Preparer, asOf: LocalDate, banner: String = ""): String {
        val rows = StringBuilder()
        fun row(label: String, value: String) { rows.append("<tr><td class='pl'>${esc(label)}</td><td class='pc'>:</td><td>$value</td></tr>") }
        fun sub(label: String, value: String) { rows.append("<tr><td class='pl ps'>${esc(label)}</td><td class='pc'>:</td><td>$value</td></tr>") }
        // The reason goes on its own line, under the days or amount.
        fun withReason(value: String, status: String, reason: String) =
            esc(value + if (status.isEmpty()) "" else " $status") + if (reason.isEmpty()) "" else "<br>(${esc(reason)})"
        val asOfText = asOf.format(LONG_DATE)
        row("Project Title", esc(project.name))
        row("Project Location", esc(project.location))
        row("Projected Beneficiaries", esc(project.projectedBeneficiaries))
        row("Contractor", esc(project.contractor))
        row("Board Resolution/Date", esc(project.boardResolution))
        row("Notice of Award", esc(date(project.noticeOfAward)))
        row("Notice to Proceed", esc(date(project.noticeToProceed)))
        row("Date Commenced", esc(date(project.dateStarted)))
        row("Land Area", esc(project.landArea))
        row("Number of Units", esc(project.numberOfUnits))
        row("Original Contract Amount", esc(peso(logs.originalCost)))
        row("Original Contract Duration", esc("${project.contractDurationDays} calendar days (c.d.)"))
        row("Contract Suspension/s", if (logs.suspensions.isEmpty()) "None" else "")
        // A suspension reads as three lines: its days, its effectivity dates, its reason.
        logs.suspensions.forEach { sub(it.label, withReason(it.value.replaceFirst(" – ", "\n"), it.status, it.reason).replaceFirst("\n", "<br>")) }
        row("Contract Resumption", if (logs.resumptions.isEmpty()) "None" else "")
        logs.resumptions.forEach { (label, value) -> sub(label, esc(value)) }
        row("Contract Time Extensions", if (logs.extensions.isEmpty()) "None" else "")
        logs.extensions.forEach { sub("Time Extension No. ${it.no}", withReason("${it.days} calendar days", it.status, it.reason)) }
        row("Variation Order/s", if (logs.variations.isEmpty()) "None" else "")
        logs.variations.forEach { sub(it.label, withReason(it.value, it.status, it.reason)) }
        row("Revised Contract Cost", logs.revisedCost?.let { esc(peso(it)) }.orEmpty())
        row("Revised Completion Date", esc(date(logs.revisedCompletion)))
        row("% Actual Accomplishment as of $asOfText", esc(pct(project.actualAccomplishment)))
        row("% Target Accomplishment as of $asOfText", esc(pct(project.targetAccomplishment)))
        row("% Delay/Advance", esc(pct(project.variance)))
        row("Status of the Project", esc(project.displayStatus))
        return page(
            "Project Profile",
            """
            <h1 class='title'>PROJECT PROFILE</h1>
            <table class='profile'>$rows</table>
            <div class='sig1'>Prepared By:<br><br><br><b>${esc(preparer.name.uppercase())}</b><br>${esc(preparer.title)}<br>${esc(preparer.office)}</div>
            """,
            banner
        )
    }

    fun weeklyReport(
        project: Project,
        report: WeeklyReport,
        manpower: List<ManpowerItem>,
        equipment: List<EquipmentItem>,
        activities: List<DailyActivity>,
        issues: List<WeeklyIssue>,
        accomplishment: List<AccomplishmentItem>,
        weather: List<DailyHourlyWeather>,
        photos: List<Pair<String, String>>,  // image src to its title
        logs: ContractLogs,
        signatories: Signatories,
        banner: String = "",
        documentsIssued: List<List<String>> = emptyList()  // weekDocuments
    ): String {
        val b = StringBuilder()
        b.append(header("For the Week of ${esc(report.reportingWeek.replace(Regex("^Week \\d+: "), ""))}"))
        b.append(basicInfo(project, "DAYS ELAPSED:", "${report.daysElapsed} calendar days", "REMAINING DAYS:", "${report.remainingDays} calendar days", logs))

        // II. The app's own 24-hour weather clock per day, as on the Weather tab.
        b.append("<table><tr><th class='sec'>II. DAILY WEATHER CHART</th></tr><tr><td>")
        b.append("<div class='legend'>LEGEND: ")
        WeatherCondition.entries.forEach { b.append("<span><i style='background:${weatherHex(it)}'></i>${esc(it.label)}</span>") }
        b.append("</div><table class='clocks'><tr>")
        WEEK_DAYS.forEach { b.append("<th>${it.uppercase()}</th>") }
        b.append("</tr><tr>")
        WEEK_DAYS.forEach { day ->
            val log = weather.firstOrNull { it.dayOfWeek.equals(day, ignoreCase = true) }
            b.append("<td>${weatherClock(log)}<div class='small'>${esc(log?.date?.let(::date) ?: "")}</div></td>")
        }
        b.append("</tr></table></td></tr></table>")

        // III & IV side by side, as on the form.
        b.append("<table><tr><th colspan='2' class='sec'>III. MANPOWER</th><th colspan='6' class='sec'>IV. TOOLS, PLANT AND EQUIPMENT (O-Operational, I-Idle, R-Reserve)</th></tr>")
        b.append("<tr><th rowspan='2'>DESIGNATION</th><th rowspan='2'>NO.</th><th rowspan='2'>DESCRIPTION</th><th rowspan='2'>NO.</th><th colspan='3'>STATUS</th><th rowspan='2'>REMARKS</th></tr><tr><th>O</th><th>I</th><th>R</th></tr>")
        repeat(maxOf(7, manpower.size, equipment.size)) { i ->
            val m = manpower.getOrNull(i)
            val e = equipment.getOrNull(i)
            fun mark(s: String) = if (e != null && e.status.startsWith(s, ignoreCase = true)) "&#10003;" else ""
            b.append("<tr><td>${i + 1}. ${esc(m?.designation ?: "")}</td><td class='n'>${m?.count ?: ""}</td>")
            b.append("<td>${i + 1}. ${esc(e?.description ?: "")}</td><td class='n'>${e?.count ?: ""}</td>")
            b.append("<td class='c'>${mark("O")}</td><td class='c'>${mark("I")}</td><td class='c'>${mark("R")}</td><td>${esc(e?.remarks ?: "")}</td></tr>")
        }
        b.append("<tr class='tot'><td>TOTAL (Weekly)</td><td class='n'>${manpower.sumOf { it.count }}</td><td>TOTAL (Weekly)</td><td class='n'>${equipment.sumOf { it.count }}</td><td colspan='4'></td></tr></table>")

        b.append("<table><tr><th colspan='3' class='sec'>V. ACTIVITIES OF THE WEEK</th></tr><tr><th>DAY OF THE WEEK</th><th>DESCRIPTION</th><th>REMARKS</th></tr>")
        // One row per day or day range, in week order; weekdays no activity covers stay as blank rows.
        val labels = activities.map { it.day.trim() }.distinctBy { it.lowercase() }
        val covered = labels.flatMap { ActivityDays.covered(it) }.toSet()
        val blank = ActivityDays.DAYS.take(5).filterIndexed { i, _ -> i !in covered }
        (labels + blank).sortedBy { ActivityDays.start(it).let { i -> if (i < 7) (i + 2) % 7 else i } }.forEach { day ->
            val list = activities.filter { it.day.trim().equals(day, ignoreCase = true) }
            b.append("<tr><td class='day'>${esc(day.uppercase())}</td><td>${lines(list.map { it.description })}</td><td>${lines(list.map { it.remarks })}</td></tr>")
        }
        b.append("</table>")

        b.append("<table><tr><th colspan='4' class='sec'>VI. ISSUES / PROBLEMS ENCOUNTERED FOR THIS WEEK</th></tr>")
        b.append("<tr><th rowspan='2'>DESCRIPTION</th><th colspan='2'>ACTION TAKEN</th><th rowspan='2'>REMARKS</th></tr><tr><th>IMPLEMENTING OFFICE</th><th>CONTRACTOR/DEVELOPER</th></tr>")
        numbered(maxOf(5, issues.size)) { i ->
            issues.getOrNull(i).let {
                // An issue saved before "action by" existed keeps its action under the Implementing Office, as the monthly form does.
                val byContractor = it?.actionBy == ISSUE_ACTION_BY[1]
                listOf(it?.description, it?.actionTaken.takeUnless { byContractor }, it?.actionTaken.takeIf { byContractor }, it?.remarks)
            }
        }.let(b::append)
        b.append("</table>")

        b.append("<table><tr><th colspan='4' class='sec'>VII. WEEKLY ACCOMPLISHMENT STATUS</th></tr><tr><th>ITEM DESCRIPTION</th><th>TARGET ACCOMPLISHMENT</th><th>ACTUAL ACCOMPLISHMENT</th><th>VARIANCE</th></tr>")
        repeat(maxOf(5, accomplishment.size)) { i ->
            val a = accomplishment.getOrNull(i)
            b.append("<tr><td>${esc(a?.itemDescription ?: "")}</td><td class='n'>${a?.let { pct(it.targetPct) } ?: ""}</td><td class='n'>${a?.let { pct(it.actualPct) } ?: ""}</td><td class='n'>${a?.let { pct(it.variancePct) } ?: ""}</td></tr>")
        }
        b.append("<tr class='tot'><td>GRAND TOTAL</td><td class='n'>${pct(report.targetAccomplishmentPct)}</td><td class='n'>${pct(report.actualAccomplishmentPct)}</td><td class='n'>${pct(report.actualAccomplishmentPct - report.targetAccomplishmentPct)}</td></tr></table>")

        b.append("<table><tr><th colspan='4' class='sec'>VIII. DOCUMENTS ISSUED (If any) – SITE INSTRUCTION / SITE MEMORANDUM / NON-CONFORMANCE REPORT, ETC.</th></tr>")
        b.append("<tr><th rowspan='2'>DOCUMENTS ISSUED / RECEIVED BY THE CONTRACTOR/DEVELOPER</th><th colspan='3'>ACTION TAKEN</th></tr><tr><th>DATE</th><th>IMPLEMENTING OFFICE</th><th>CONTRACTOR/ DEVELOPER</th></tr>")
        // Developer requests and letters to the contractor from that week; spare rows are left for writing by hand.
        numbered(maxOf(5, documentsIssued.size)) { i -> documentsIssued.getOrNull(i) ?: listOf(null, null, null, null) }.let(b::append)
        b.append("</table>")

        b.append("<table class='photos'><tr><th colspan='2' class='sec'>IX. ATTACHED PHOTOS FOR THE UPDATES IN THE SITE</th></tr>")
        if (photos.isEmpty()) b.append("<tr><td colspan='2' class='empty'>No photos attached.</td></tr>")
        photos.chunked(2).forEach { pair ->
            b.append("<tr>")
            pair.forEach { (src, title) -> b.append("<td><img src='$src'>${if (title.isBlank()) "" else "<div>${esc(title)}</div>"}</td>") }
            if (pair.size == 1) b.append("<td></td>")
            b.append("</tr>")
        }
        b.append("</table>")

        b.append("<div class='keep'><p class='note'>This form must be submitted to NHA Regional Office, duly accomplished and signed by the concerned personnel on a weekly basis.</p>")
        b.append(signatures(signatories, "Checked and Reviewed by")).append("</div>")
        return page("Weekly Project Engineer's Report", b.toString(), banner, narrow = true)
    }

    /**
     * The monthly form for [month] ("July 2026"), as the Monthly tab shows it: progress from the
     * month's last weekly report, payments up to month end, issues dated in the month, and
     * workable days counted from the weather logs. [saved] supplies recommendations and sign-offs.
     */
    fun monthlyReport(
        project: Project,
        month: YearMonth,
        lastWeekly: WeeklyReport?,
        accomplishment: List<AccomplishmentItem>,
        payments: List<ProjectPayment>,
        issues: List<ProjectIssue>,
        logs: ContractLogs,
        documents: List<PendingDocument>,
        weather: List<DailyHourlyWeather>,
        saved: MonthlyReport?,
        signatories: Signatories,
        banner: String = ""
    ): String {
        val b = StringBuilder()
        b.append(header("For the Month of ${esc(month.format(MONTH))}"))
        fun list(items: List<Order>) = items.joinToString("<br>") {
            esc("${it.label}: ${it.value}" + (if (it.reason.isEmpty()) "" else " (${it.reason})") + if (it.status.isEmpty()) "" else " ${it.status}")
        }.ifEmpty { "None" }
        b.append(basicInfo(project, "VARIATION ORDER/S", list(logs.variations), "WORK SUSPENSION ORDER/S", list(logs.suspensions), logs))

        b.append("<table><tr><th colspan='5' class='sec'>II. PROJECT STATUS</th></tr><tr><th colspan='5' class='sub'>STATUS OF ACCOMPLISHMENT</th></tr>")
        b.append("<tr><th>ITEM</th><th>% WEIGHT</th><th>TARGET ACCOMPLISHMENT</th><th>ACTUAL ACCOMPLISHMENT</th><th>TOTAL</th></tr>")
        val items = accomplishment.ifEmpty { com.example.data.model.ACCOMPLISHMENT_LINES.map { AccomplishmentItem(it, 0.0, 0.0, 0.0) } }
        items.forEach { a ->
            b.append("<tr><td>${esc(a.itemDescription)}</td><td class='n'>${pct(a.weightPct)}</td><td class='n'>${pct(a.targetPct)}</td><td class='n'>${pct(a.actualPct)}</td><td class='n'>${pct(a.weightPct * a.actualPct / 100)}</td></tr>")
        }
        b.append("<tr class='tot'><td>TOTAL</td><td class='n'>${pct(items.sumOf { it.weightPct })}</td><td class='n'>${lastWeekly?.let { pct(it.targetAccomplishmentPct) } ?: ""}</td><td class='n'>${lastWeekly?.let { pct(it.actualAccomplishmentPct) } ?: ""}</td><td class='n'>${pct(items.sumOf { it.weightPct * it.actualPct / 100 })}</td></tr></table>")

        b.append("<table><tr><th colspan='6' class='sec'>III. STATUS OF PAYMENT</th></tr><tr><th rowspan='2'></th><th rowspan='2'>DATE</th><th rowspan='2'>PERIOD COVERED</th><th colspan='2'>PAYMENTS MADE</th><th rowspan='2'>BALANCE</th></tr><tr><th>NET AMOUNT</th><th>%</th></tr>")
        val paid = com.example.data.model.netBillings(payments).filter { p -> parseDate(p.date)?.let { YearMonth.from(it) <= month } ?: true }
        repeat(maxOf(3, paid.size)) { i ->
            val p = paid.getOrNull(i)
            b.append("<tr><td>${i + 1}. ${esc(p?.name ?: "")}</td><td>${esc(p?.date?.let(::date) ?: "")}</td><td>${esc(p?.periodCovered ?: "")}</td><td class='n'>${p?.let { esc(peso(it.netAmount)) } ?: ""}</td><td class='n'>${p?.let { pct(it.percentage) } ?: ""}</td><td class='n'>${p?.let { esc("${peso(it.balanceAmount)} (${pct(it.balancePercentage)})") } ?: ""}</td></tr>")
        }
        // The balance after the last billing is what is left; the shares add up as the rows show them.
        val lastPaid = paid.lastOrNull()
        b.append("<tr class='tot'><td colspan='3'>GRAND TOTAL</td><td class='n'>${esc(peso(paid.sumOf { it.netAmount }))}</td><td class='n'>${pct(paid.sumOf { it.percentage })}</td><td class='n'>${lastPaid?.let { esc("${peso(it.balanceAmount)} (${pct(it.balancePercentage)})") } ?: ""}</td></tr>")
        b.append("</table>")

        b.append("<table><tr><th colspan='7' class='sec'>IV. CONTRACT SCHEDULE STATUS</th></tr><tr><th>TIME EXTENSION NO.</th><th>NO. OF DAYS</th><th>REVISED CONTRACT DURATION</th><th>PERIOD CONSIDERED (AS APPLICABLE)</th><th>REASON FOR TIME EXTENSION</th><th>REVISED COMPLETION DATE</th><th>REMARKS</th></tr>")
        // Each extension adds its days to the running duration, as the Key Dates card does.
        var duration = project.contractDurationDays
        val start = parseDate(project.dateStarted)
        repeat(maxOf(4, logs.extensions.size)) { i ->
            val t = logs.extensions.getOrNull(i)
            if (t != null) duration += t.days
            val end = if (t != null) start?.let { ProjectSchedule.lastDay(it, duration) }?.format(LONG_DATE).orEmpty() else ""
            b.append("<tr><td class='c'>${esc(t?.no ?: "${i + 1}")}</td><td class='n'>${t?.days ?: ""}</td><td class='n'>${if (t != null) "$duration c.d." else ""}</td><td></td><td>${esc(t?.reason ?: "")}</td><td>$end</td><td>${esc(t?.status ?: "")}</td></tr>")
        }
        b.append("</table>")

        b.append("<table><tr><th colspan='4' class='sec'>V. SUMMARY OF UNWORKABLE DAYS DUE TO WEATHER CONDITION</th></tr><tr><th>MONTH NO.</th><th>PERIOD COVERED</th><th>NO. OF WORKABLE DAYS</th><th>NO. OF UNWORKABLE DAYS</th></tr>")
        // Only months with weather logged, numbered by contract month when the start date is known.
        val logged = workableDays(weather).filterKeys { it <= month }.toSortedMap()
        val first = start?.let { YearMonth.from(it) }
        if (logged.isEmpty()) b.append("<tr><td colspan='4' class='empty'>No weather logged yet.</td></tr>")
        logged.entries.forEachIndexed { i, (m, days) ->
            val no = first?.let { (ChronoUnit.MONTHS.between(it, m) + 1).toInt() } ?: (i + 1)
            b.append("<tr><td class='c'>$no</td><td>${esc(m.format(MONTH))}</td><td class='n'>${days.first}</td><td class='n'>${days.second}</td></tr>")
        }
        b.append("<tr class='tot'><td colspan='2'>GRAND TOTAL</td><td class='n'>${logged.values.sumOf { it.first }}</td><td class='n'>${logged.values.sumOf { it.second }}</td></tr></table>")

        b.append("<table><tr><th colspan='3' class='sec'>VI. STATUS OF SUBMISSION OF DOCUMENTARY REQUIREMENTS</th></tr><tr><th>DOCUMENT</th><th>STATUS</th><th>REMARKS</th></tr>")
        numbered(maxOf(5, documents.size)) { i -> documents.getOrNull(i).let { listOf(it?.documentName, it?.status, it?.remarks) } }.let(b::append)
        b.append("</table>")

        b.append("<table><tr><th colspan='4' class='sec'>VII. ISSUES / CONCERNS / SITE INSTRUCTIONS</th></tr>")
        b.append("<tr><th rowspan='2'>ISSUES/CONCERNS/OBSERVATIONS (INCLUDING COMPLIANCE WITH CPES REQUIREMENTS, ETC.)</th><th colspan='3'>ACTION TAKEN</th></tr><tr><th>DATE</th><th>IMPLEMENTING OFFICE</th><th>CONTRACTOR/DEVELOPER</th></tr>")
        val monthIssues = issues.filter { parseDate(it.date)?.let { d -> YearMonth.from(d) == month } ?: false }
        numbered(maxOf(5, monthIssues.size)) { i ->
            monthIssues.getOrNull(i).let { issue ->
                val action = issue?.let { listOf(it.actionTaken, it.remarks).filter(String::isNotBlank).joinToString("; ") }
                // An issue logged before "action taken by" existed keeps its action under the Implementing Office.
                val byContractor = issue?.actionBy == ISSUE_ACTION_BY[1]
                listOf(issue?.description, issue?.date?.let(::date), action?.takeUnless { byContractor }, action?.takeIf { byContractor })
            }
        }.let(b::append)
        b.append("</table>")

        b.append("<div class='keep'><table><tr><th class='sec'>VIII. RECOMMENDATIONS (If any)</th></tr><tr><td class='rec'>${esc(saved?.recommendations ?: "").replace("\n", "<br>")}</td></tr></table>")
        b.append("<p class='note'>This form must be submitted to NHA Regional Office, duly accomplished and signed by the concerned personnel on or before end of each month.</p>")
        b.append(signatures(signatories)).append("</div>")
        return page("Monthly Project Engineer's Report", b.toString(), banner, narrow = true)
    }

    /** One site's plan for [sdpPlan]: [image] is the framed plan as a data URI. */
    class SdpSheet(val site: String, val image: String, val summary: SdpSummary.Summary)

    const val LANDSCAPE_PAGE = "size: A4 landscape"

    /**
     * The subdivision plan coloured by lot progress, filling one A4 page, with a legend of saleable
     * units per band over its bottom-left corner. With several [sheets] (sites) the plans sit side by
     * side on a [landscape] page, or one above the other on a portrait one, and the legend counts each
     * site and the total. [bandHex] the colour of each band of [SdpSummary.BANDS]; road lots take its red, yellow and green.
     */
    fun sdpPlan(project: Project, sheets: List<SdpSheet>, bandHex: List<String>, asOf: LocalDate, banner: String = "", landscape: Boolean = false): String {
        val multi = sheets.size > 1
        // Columns after "Progress": each site, then the total; one site has just its units.
        val span = if (multi) sheets.size + 2 else 2
        fun counts(of: (SdpSummary.Summary) -> Int) =
            (if (multi) sheets.joinToString("") { "<td class='n'>${of(it.summary)}</td>" } else "") + "<td class='n'>${sheets.sumOf { of(it.summary) }}</td>"
        val legend = StringBuilder("<table class='lg'><tr><th class='sec' colspan='$span'>LEGEND</th></tr><tr><th class='sub'>Progress</th>")
        if (multi) sheets.forEach { legend.append("<th class='sub'>${esc(SdpSites.label(it.site))}</th>") }
        legend.append("<th class='sub'>${if (multi) "Total" else "Units"}</th></tr>")
        SdpSummary.BANDS.forEachIndexed { i, band ->
            legend.append("<tr><td><i class='sw' style='background:${bandHex[i]}'></i>${esc(band)}</td>${counts { it.saleableBands[i] }}</tr>")
        }
        legend.append("<tr class='tot'><td>Total units</td>${counts { it.saleable.count }}</tr>")
        // Road lot colours always show, so the key is the same on every print.
        legend.append("<tr><th class='sub' colspan='$span'>Road lot</th></tr>")
        SdpSummary.ROAD_STATES.forEachIndexed { i, state ->
            legend.append("<tr><td colspan='$span'><i class='sw' style='background:${bandHex[listOf(0, 3, 5)[i]]}'></i>${esc(state)}</td></tr>")
        }
        legend.append("</table>")
        // Height left for plans under the banner and title: 244mm portrait, 157mm landscape; a site name takes 6mm.
        // One plan has the legend over its corner; several plans would lose lots under it, so it sits beside
        // them (landscape) or below them (portrait, about 6mm a row).
        val room = if (landscape) 157 else 244
        val legendHeight = 12 * 6
        val maxHeight = when {
            !multi -> room
            landscape -> room - 6
            else -> (room - legendHeight - sheets.size * 9) / sheets.size
        }
        val style = if (multi || landscape) " style='max-height:${maxHeight}mm'" else ""
        val plan = if (!multi) "<div class='plan'><img src='${sheets.single().image}'$style>$legend</div>"
        else "<div class='plan multi${if (landscape) " row" else ""}'>" +
            sheets.joinToString("") { "<figure><figcaption>${esc(SdpSites.label(it.site))}</figcaption><img src='${it.image}'$style></figure>" } +
            "$legend</div>"
        return page(
            "Subdivision Plan Progress",
            """
            <h1>STATUS OF CONSTRUCTION</h1>
            <div class='period'>${esc(listOf(project.name, project.location).filter { it.isNotBlank() }.joinToString(", "))} &#183; as of ${asOf.format(LONG_DATE)}</div>
            $plan
            """,
            banner,
            fullPage = true,
            landscape = landscape
        )
    }

    /**
     * The Abstract of Physical Accomplishment of [payment] on a landscape sheet, laid out as the district's billing
     * format: each line's contract quantity, unit cost and amount, what was billed before, in this billing and to date
     * (quantity, amount, share of the line), and the balance. [units] is the project's lots or housing units; "actual"
     * here is what is billed to date. web/print.js billingAbstractHtml prints the same page.
     */
    fun billingAbstract(
        project: Project, documents: List<PendingDocument>, payments: List<ProjectPayment>, payment: ProjectPayment,
        units: Int, signatories: Signatories, banner: String = ""
    ): String {
        val abstract = BillingAbstract.of(project, documents, payments, payment)
        fun money(v: Double) = "%,.2f".format(Locale.US, v)
        // A quantity: 2 decimals at most, none when whole.
        val quantity = java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols(Locale.US)).apply { roundingMode = java.math.RoundingMode.HALF_UP }
        fun qty(v: Double) = quantity.format(v)
        class Row(val amount: Double, val previous: Double, val thisPeriod: Double)
        fun sumOf(lines: List<BillingAbstract.Line>) = Row(lines.sumOf { it.amount }, lines.sumOf { it.previous }, lines.sumOf { it.thisPeriod })
        // Previous, this period, to date: quantity (a line's only), amount, share of the row's contract amount.
        fun billed(r: Row, unitCost: Double) = listOf(r.previous, r.thisPeriod, r.previous + r.thisPeriod).joinToString("") { x ->
            "<td class='n'>${if (unitCost > 0) qty(x / unitCost) else ""}</td><td class='n'>${money(x)}</td><td class='n'>${if (r.amount > 0) pct(x / r.amount * 100) else ""}</td>"
        }
        fun line(l: BillingAbstract.Line): String {
            val unitCost = l.amount / units
            val left = l.amount - l.previous - l.thisPeriod
            return "<tr><td class='c'>${l.no}</td><td>${l.name}</td><td class='n'>${qty(units.toDouble())}</td><td class='c'>${l.unit}</td><td class='n'>${money(unitCost)}</td><td class='n'>${money(l.amount)}</td>" +
                "${billed(Row(l.amount, l.previous, l.thisPeriod), unitCost)}<td class='n'>${if (unitCost > 0) qty(left / unitCost) else ""}</td><td class='n'>${money(left)}</td></tr>"
        }
        fun total(label: String, r: Row) = "<tr class='tot'><td></td><td>${esc(label)}</td><td></td><td></td><td class='n'>${money(r.amount / units)}</td><td class='n'>${money(r.amount)}</td>" +
            "${billed(r, 0.0)}<td></td><td class='n'>${money(r.amount - r.previous - r.thisPeriod)}</td></tr>"
        val all = sumOf(abstract.parts.flatMap { it.lines })
        val actual = if (all.amount > 0) (all.previous + all.thisPeriod) / all.amount * 100 else 0.0
        fun info(label: String, value: String, label2: String = "", value2: String = "") =
            "<tr><td class='pl'>$label</td><td class='pc'>:</td><td>${esc(value)}</td><td class='pl'>$label2</td><td>${if (label2.isEmpty()) "" else ": ${esc(value2)}"}</td></tr>"
        val rows = abstract.parts.joinToString("") { p ->
            (p.heading?.let { "<tr><td class='c'>${it.first}</td><td colspan='16'>${it.second}</td></tr>" } ?: "") + p.lines.joinToString("", transform = ::line) + total("Subtotal (${p.name})", sumOf(p.lines))
        }
        return page(
            "Abstract of Physical Accomplishment",
            """
            <h1>ABSTRACT OF PHYSICAL ACCOMPLISHMENT</h1>
            <div class='period'>Period covered: ${esc(payment.periodCovered.ifBlank { date(payment.date) })}</div>
            <table class='apa-info'>
              ${info("PROJECT NAME", project.name, "CONTRACT DURATION", "${project.contractDurationDays} C.D.")}
              ${info("LOCATION", project.location, "DATE STARTED", date(project.dateStarted))}
              ${info("CONTRACTOR", project.contractor, "COMPLETION DATE", date(ProjectSchedule.effectiveCompletion(project.completionDateOriginal, project.completionDateRevised)))}
              ${info("CONTRACT COST", peso(ContractCost.current(project.scopeOfWork, project.contractCostOriginal, documents)))}
              ${info("TARGET ACCOMPLISHMENT", pct(abstract.targetPct))}
              ${info("ACTUAL ACCOMPLISHMENT", pct(actual))}
              ${info("ADVANCE/SLIPPAGE", pct(Math.round(actual * 100) / 100.0 - Math.round(abstract.targetPct * 100) / 100.0))}
            </table>
            <table class='apa'>
              $APA_HEAD
              $rows
              ${total("GRAND TOTAL", all)}
            </table>
            <div class='keep'>${signatures(signatories, "Checked by", "Approved by")}</div>
            """,
            banner,
            fullPage = true,
            landscape = true
        )
    }

    /** The district's column layout of an Abstract of Physical Accomplishment: 17 columns under three header rows. */
    private val APA_HEAD = "<tr><th rowspan='3'>ITEM NO.</th><th rowspan='3'>ITEM DESCRIPTION</th><th rowspan='3'>QTY</th><th rowspan='3'>UNIT</th><th rowspan='3'>UNIT COST</th>" +
        "<th rowspan='3'>AMOUNT</th><th colspan='9'>ACCOMPLISHMENT</th><th colspan='2' rowspan='2'>BALANCE</th></tr>" +
        "<tr><th colspan='3'>PREVIOUS</th><th colspan='3'>THIS PERIOD</th><th colspan='3'>TO DATE</th></tr>" +
        "<tr>${"<th>QTY</th><th>AMOUNT</th><th>% WT</th>".repeat(3)}<th>QTY</th><th>AMOUNT</th></tr>"

    /**
     * An uploaded abstract of the actual work (Actual Accomplishment tab) on a landscape sheet: every row of the Excel
     * file with all its columns, an empty cell left blank. The website shows the same table in its tab.
     */
    fun actualAbstract(project: Project, abstract: ProjectAbstract, banner: String = ""): String {
        // A quantity: 4 decimals at most, none when whole.
        val quantity = java.text.DecimalFormat("#,##0.####", java.text.DecimalFormatSymbols(Locale.US)).apply { roundingMode = java.math.RoundingMode.HALF_UP }
        fun n(text: String?) = "<td class='n'>${text.orEmpty()}</td>"
        fun money(v: Double?) = n(v?.let { "%,.2f".format(Locale.US, it) })
        fun qty(v: Double?) = n(v?.let { quantity.format(it) })
        fun figures(f: List<Double?>) = qty(f.getOrNull(0)) + money(f.getOrNull(1)) + n(f.getOrNull(2)?.let(::pct))
        val rows = ProjectAbstracts.rows(abstract.rowsJson).joinToString("") { r ->
            val name = esc(r.name).let { if (r.kind == "part" || r.kind == "group") "<b>$it</b>" else it }
            "<tr${if (r.kind == "subtotal" || r.kind == "total") " class='tot'" else ""}><td class='c'>${esc(r.no)}</td><td>$name</td>${qty(r.qty)}<td class='c'>${esc(r.unit)}</td>" +
                "${money(r.unitCost)}${money(r.amount)}${figures(r.prev)}${figures(r.period)}${figures(r.toDate)}${qty(r.balance.getOrNull(0))}${money(r.balance.getOrNull(1))}</tr>"
        }
        fun info(label: String, value: String) = "<tr><td class='pl'>$label</td><td class='pc'>:</td><td>${esc(value)}</td></tr>"
        return page(
            "Abstract of Physical Accomplishment",
            """
            <h1>ABSTRACT OF PHYSICAL ACCOMPLISHMENT</h1>
            <div class='period'>As of ${esc(date(abstract.asOf))}</div>
            <table class='apa-info'>
              ${info("PROJECT NAME", project.name)}
              ${info("LOCATION", project.location)}
              ${info("CONTRACTOR", project.contractor)}
              ${info("ACTUAL ACCOMPLISHMENT", pct(abstract.overallPct))}
            </table>
            <table class='apa'>
              $APA_HEAD
              $rows
            </table>
            """,
            banner,
            fullPage = true,
            landscape = true
        )
    }

    private fun header(period: String) = """
        <h1>PROJECT ENGINEER&#8217;S REPORT</h1><div class='period'>$period</div>
    """

    /** Section I, shared by both reports; the right-hand cells of the first two rows differ. */
    private fun basicInfo(project: Project, label1: String, value1: String, label2: String, value2: String, logs: ContractLogs): String {
        val te = logs.extensions.joinToString("<br>") { esc("No. ${it.no}: ${it.days} c.d." + if (it.status.isEmpty()) "" else " ${it.status}") }.ifEmpty { "None" }
        return """
        <table class='info'>
          <tr><th colspan='4' class='sec'>I. BASIC INFORMATION</th></tr>
          <tr><td class='lab'>PROJECT NAME</td><td colspan='3'>${esc(project.name)}</td></tr>
          <tr><td class='lab'>LOCATION</td><td colspan='3'>${esc(project.location)}</td></tr>
          <tr><td class='lab'>IMPLEMENTING OFFICE</td><td colspan='3'>${esc(project.implementingOffice)}</td></tr>
          <tr><td class='lab'>CONTRACTOR</td><td colspan='3'>${esc(project.contractor)}</td></tr>
          <tr><td class='lab'>SCOPE OF WORK</td><td colspan='3'>${scopeText(project.scopeOfWork)}</td></tr>
          <tr><td class='lab'>CONTRACT COST (ORIGINAL)</td><td class='nw'>${esc(peso(logs.originalCost))}</td><td class='lab'>$label1</td><td>$value1</td></tr>
          <tr><td class='lab'>CONTRACT COST (REVISED)</td><td class='nw'>${esc(peso(logs.revisedCost ?: logs.originalCost))}</td><td class='lab'>$label2</td><td>$value2</td></tr>
          <tr><td class='lab'>CONTRACT DURATION</td><td colspan='3'>${project.contractDurationDays} calendar days</td></tr>
          <tr><td class='lab'>DATE STARTED</td><td>${esc(date(project.dateStarted))}</td><td class='lab'>TIME EXTENSION/S</td><td>$te</td></tr>
          <tr><td class='lab'>COMPLETION DATE (ORIGINAL)</td><td>${esc(date(project.completionDateOriginal))}</td><td class='lab'>REVISED COMPLETION DATE</td><td>${esc(date(logs.revisedCompletion))}</td></tr>
        </table>
        """
    }

    private fun office(name: String) = if (name.isBlank()) "" else "<br>${esc(name.trim())}"

    private fun signatures(s: Signatories, checkedTitle: String = "Checked by", notedTitle: String = "Noted by") = """
        <table class='sig'><tr>
          <td>Prepared by:<br><br><br><b>${esc(s.preparedName.uppercase())}</b><div class='line'></div>${esc(s.preparedPosition)}${office(s.preparedOffice)}</td>
          <td>$checkedTitle:<br><br><br><b>${esc(s.checkedName.uppercase())}</b><div class='line'></div>${esc(s.checkedPosition)}${office(s.checkedOffice)}</td>
          <td>$notedTitle:<br><br><br><b>${esc(s.notedName.uppercase())}</b><div class='line'></div>${esc(s.notedPosition)}${office(s.notedOffice)}</td>
        </tr></table>
    """

    /** The Weather tab's clock: inner ring 12 AM–11 AM, outer ring 12 PM–11 PM, one slice per hour. */
    internal fun weatherClock(log: DailyHourlyWeather?): String {
        val parts = log?.hourlyConditionsCsv?.split(",").orEmpty()
        fun cond(hour: Int): WeatherCondition? =
            if (log == null) null else runCatching { WeatherCondition.valueOf(parts.getOrNull(hour)?.trim() ?: "FAIR") }.getOrDefault(WeatherCondition.FAIR)
        val cx = 50.0; val cy = 50.0; val r = 36.0; val inner = r * 0.6; val center = r * 0.25
        fun x(radius: Double, deg: Double) = "%.2f".format(Locale.US, cx + radius * cos(Math.toRadians(deg)))
        fun y(radius: Double, deg: Double) = "%.2f".format(Locale.US, cy + radius * sin(Math.toRadians(deg)))
        fun wedge(radius: Double, i: Int, c: WeatherCondition?): String {
            val a = -90.0 + i * 30
            val fill = c?.let(::weatherHex) ?: "none"
            return "<path d='M$cx,$cy L${x(radius, a)},${y(radius, a)} A$radius,$radius 0 0 1 ${x(radius, a + 30)},${y(radius, a + 30)} Z' fill='$fill'/>"
        }
        val s = StringBuilder("<svg viewBox='0 0 100 100' class='clock'>")
        for (i in 0..11) s.append(wedge(r, i, cond(i + 12)))
        for (i in 0..11) s.append(wedge(inner, i, cond(i)))
        s.append("<circle cx='$cx' cy='$cy' r='$center' fill='#fff'/>")
        listOf(r, inner, center).forEach { s.append("<circle cx='$cx' cy='$cy' r='$it' fill='none' stroke='#888' stroke-width='0.5'/>") }
        for (i in 0..11) {
            val a = -90.0 + i * 30
            s.append("<line x1='${x(center, a)}' y1='${y(center, a)}' x2='${x(r, a)}' y2='${y(r, a)}' stroke='#888' stroke-width='0.5'/>")
            s.append("<text x='${x(r + 7, a)}' y='${y(r + 7, a)}' font-size='6' text-anchor='middle' dominant-baseline='middle'>${if (i == 0) 12 else i}</text>")
        }
        log?.date?.let { d -> runCatching { LocalDate.parse(d).dayOfMonth }.getOrNull() }?.let {
            s.append("<text x='$cx' y='$cy' font-size='8' font-weight='bold' text-anchor='middle' dominant-baseline='middle'>$it</text>")
        }
        return s.append("</svg>").toString()
    }

    private fun weatherHex(c: WeatherCondition) = when (c) {
        WeatherCondition.FAIR -> WeatherFairGreen
        WeatherCondition.CLOUDY -> WeatherCloudyYellow
        WeatherCondition.RAIN_SHOWERS -> WeatherRainShowersCyan
        WeatherCondition.RAINY -> WeatherRainyBlue
        WeatherCondition.STORMY -> WeatherStormyRed
    }.toArgb().let { "#%06X".format(it and 0xFFFFFF) }

    private fun numbered(count: Int, cells: (Int) -> List<String?>): String = buildString {
        repeat(count) { i ->
            val c = cells(i)
            append("<tr><td>${i + 1}. ${esc(c.first() ?: "")}</td>")
            c.drop(1).forEach { append("<td>${esc(it ?: "")}</td>") }
            append("</tr>")
        }
    }

    private fun lines(values: List<String>) = values.filter { it.isNotBlank() }.joinToString("<br>") { esc(it) }

    private fun scopeText(scopeJson: String): String = runCatching {
        val arr = org.json.JSONArray(scopeJson)
        (0 until arr.length()).joinToString("<br>") { esc(arr.getJSONObject(it).getString("name")) }
    }.getOrElse { esc(scopeJson) }

    /**
     * Workable and unworkable logged days per month. A day is unworkable when most of its
     * 8 AM–5 PM working hours were Rainy or Stormy; days with no log are not counted.
     */
    internal fun workableDays(weather: List<DailyHourlyWeather>): Map<YearMonth, Pair<Int, Int>> =
        weather.mapNotNull { log -> parseDate(log.date)?.let { it to log } }
            .distinctBy { it.first }
            .groupBy({ YearMonth.from(it.first) }, { it.second })
            .mapValues { (_, logs) ->
                val bad = logs.count { log ->
                    val hours = log.hourlyConditionsCsv.split(",")
                    (8..16).count { h -> runCatching { !WeatherCondition.valueOf(hours[h].trim()).isWorkable }.getOrDefault(false) } > 4
                }
                (logs.size - bad) to bad
            }

    fun parseMonth(month: String): YearMonth? = runCatching { YearMonth.parse(month.trim(), MONTH) }.getOrNull()

    /** First day of a reporting week: "Oct 24, 2026 - Oct 30, 2026", or the older "Week 30: July 20 - July 26, 2026". */
    fun weekStart(reportingWeek: String): LocalDate? {
        val parts = reportingWeek.replace(Regex("^Week \\d+: "), "").split(" - ").map { it.trim() }
        if (parts.size != 2) return null
        fun parse(text: String) = WEEK_FORMATS.firstNotNullOfOrNull { runCatching { LocalDate.parse(text, it) }.getOrNull() }
        val end = parse(parts[1]) ?: return null
        return parse(parts[0]) ?: parse("${parts[0]}, ${end.year}")
    }

    /**
     * Section VIII rows for [report]'s week as (document, date, implementing office, contractor/developer):
     * developer requests NHA received that week and letters the contractor received that week, by date.
     */
    fun weekDocuments(report: WeeklyReport, letters: List<com.example.data.model.ContractorLetter>, requests: List<DeveloperRequest>): List<List<String>> {
        val start = weekStart(report.reportingWeek) ?: return emptyList()
        fun inWeek(day: String) = parseDate(day)?.let { !it.isBefore(start) && !it.isAfter(start.plusDays(6)) } == true
        val fromDeveloper = requests.filter { inWeek(it.dateReceived) }.map { r ->
            listOf(
                "Developer's request: ${r.requestType}" + r.details.takeIf { it.isNotBlank() }?.let { " – $it" }.orEmpty(),
                r.dateReceived,
                if (r.datePackaged.isBlank()) "Received; not yet packaged" else "Packaged ${date(r.datePackaged)}",
                r.dateRequested.takeIf { it.isNotBlank() }?.let { "Requested ${date(it)}" }.orEmpty()
            )
        }
        val received = letters.filter { inWeek(it.dateReceived) }.map { l ->
            listOf(l.subject, l.dateReceived, "Letter issued", "Received ${date(l.dateReceived)}")
        }
        return (fromDeveloper + received).sortedBy { it[1] }.map { row -> row.toMutableList().also { it[1] = date(it[1]) } }
    }

    private fun parseDate(value: String): LocalDate? = runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    internal fun date(value: String): String = parseDate(value)?.format(LONG_DATE) ?: value

    internal fun peso(amount: Double) = "Php " + "%,.2f".format(Locale.US, amount)

    private fun pct(value: Double) = "%.2f%%".format(Locale.US, value)

    internal fun esc(text: String) = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("'", "&#39;").replace("\"", "&quot;")

    private val LONG_DATE = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH)
    private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val WEEK_FORMATS = listOf("MMM d, yyyy", "MMMM d, yyyy").map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
    // The reporting week runs Saturday to Friday.
    private val WEEK_DAYS = listOf("Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday")

    // Print ink, not app colours: black text on white A4 paper.
    // [banner] is the NHA header image as a data URI; blank leaves it out.
    // [narrow] is the website's 6mm page margin (web/style.css @page), so both print the same pages; the profile keeps 12mm.
    private fun page(title: String, body: String, banner: String, fullPage: Boolean = false, landscape: Boolean = false, narrow: Boolean = fullPage) = """
        <!doctype html><html><head><meta charset='utf-8'><title>${esc(title)}</title><style>
        @page { ${if (landscape) LANDSCAPE_PAGE else "size: A4"}; margin: ${if (narrow) "6mm" else "12mm"}; }
        body { font-family: Arial, Helvetica, sans-serif; font-size: 9pt; color: #000; background: #fff; margin: 0; }
        /* On screen (the app's print preview) the form sits on an A4-wide sheet; printing ignores this. */
        @media screen { html { background: #9e9e9e; } body { width: ${if (landscape) "285mm" else if (narrow) "198mm" else "186mm"}; padding: ${if (narrow) "6mm" else "12mm"}; margin: 6mm auto; } }
        table { width: 100%; border-collapse: collapse; margin-bottom: 3mm; page-break-inside: auto; }
        tr { page-break-inside: avoid; }
        /* A title or column-header row never ends a page: it moves on with the rows below it. */
        tr:has(> th) { break-after: avoid; }
        th, td { border: 0.6pt solid #000; padding: 1.2mm 1.5mm; vertical-align: top; text-align: left; height: 4mm; }
        th { font-weight: bold; text-align: center; vertical-align: middle; }
        th.sec { text-align: left; background: #d9d9d9; }
        th.sub { background: #f2f2f2; }
        td.lab { font-weight: bold; width: 22%; }
        td.n { text-align: right; white-space: nowrap; }
        td.c { text-align: center; }
        td.nw { white-space: nowrap; }
        td.day { font-weight: bold; width: 18%; }
        /* Room to write a few lines; typed recommendations make it taller. Short, so the sign-offs fit under section VII. */
        td.rec { height: 15mm; }
        td.empty { text-align: center; color: #555; }
        tr.tot { break-before: avoid; }
        tr.tot td { font-weight: bold; }
        h1 { font-size: 13pt; text-align: center; margin: 3mm 0 0; }
        h1.title { font-size: 14pt; margin: 4mm 0 6mm; text-decoration: underline; }
        .period { text-align: center; margin-bottom: 3mm; font-weight: bold; }
        .legend { margin-bottom: 1.5mm; }
        .legend span { margin-right: 3mm; white-space: nowrap; }
        .legend i { display: inline-block; width: 3mm; height: 3mm; border: 0.4pt solid #000; margin-right: 1mm; vertical-align: middle; }
        table.clocks th, table.clocks td { text-align: center; width: 14.28%; padding: 0.5mm; font-size: 7pt; }
        svg.clock { width: 100%; height: auto; }
        .small { font-size: 6.5pt; }
        table.photos td { width: 50%; text-align: center; }
        table.photos img { max-width: 100%; max-height: 80mm; }
        table.profile td { border: none; padding: 0.8mm 1mm; font-size: 10.5pt; }
        td.pl { width: 38%; font-weight: bold; }
        table.profile td.ps { font-weight: normal; padding-left: 10mm; }
        td.pc { width: 3%; }
        .sig1 { margin-top: 12mm; font-size: 10.5pt; }
        table.apa-info { width: auto; }
        table.apa-info td { border: none; padding: 0.3mm 1mm; height: auto; width: auto; white-space: nowrap; }
        table.apa-info td:nth-child(3) { padding-right: 20mm; }
        table.apa { font-size: 7.5pt; }
        table.apa th, table.apa td { padding: 1mm 0.8mm; }
        img.banner { display: block; width: 100%; margin-bottom: 3mm; }
        table.sig { margin-top: 6mm; }
        table.sig td { border: none; width: 33%; }
        .line { border-top: 0.6pt solid #000; width: 85%; margin-top: 0.5mm; }
        .note { font-size: 8pt; font-style: italic; }
        /* The closing note and sign-offs (with the monthly recommendations) print on one page. */
        .keep { break-inside: avoid; }
        /* SDP print: narrow margins, the plan as big as one page allows and the legend over its bottom-left corner. */
        body.full img.banner { width: auto; max-height: 22mm; margin: 0 auto 1mm; }
        body.full h1 { font-size: 16pt; margin: 1mm 0 0; }
        body.full .period { font-size: 10pt; margin-bottom: 2mm; }
        div.plan { position: relative; text-align: center; }
        div.plan img { max-width: 100%; max-height: 244mm; }
        div.plan figure { margin: 0 0 3mm; }
        div.plan figcaption { font-weight: bold; font-size: 11pt; margin-bottom: 1mm; }
        div.plan.row { display: flex; gap: 3mm; justify-content: center; align-items: flex-start; }
        div.plan.row figure { flex: 1 1 0; min-width: 0; }
        table.lg { position: absolute; left: 0; bottom: 0; width: auto; background: #fff; font-size: 10pt; margin: 0; }
        div.plan.multi table.lg { position: static; }
        div.plan.row table.lg { flex: none; align-self: flex-end; }
        table.lg th, table.lg td { padding: 1mm 2mm; height: auto; white-space: nowrap; }
        i.sw { display: inline-block; width: 4mm; height: 4mm; border: 0.4pt solid #000; margin-right: 1.5mm; vertical-align: middle; }
        </style></head><body${if (fullPage) " class='full'" else ""}>${if (banner.isEmpty()) "" else "<img class='banner' src='$banner'>"}$body</body></html>
    """.trimIndent()
}
