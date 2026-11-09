package com.example

import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.util.BarChart
import com.example.util.BarChart.Bar
import com.example.util.BarChart.Item
import com.example.util.BarChart.Version
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class BarChartTest {

    // San Ildefonso Heights Phase 1, approved bar chart with cash flow (360 c.d.).
    private val sanIldefonso = Version("Original", "2026-01-01", listOf(
        Item("Land Acquisition", 26235612.20, listOf(Bar(1, 60))),
        Item("Mobilization/Demobilization", 1650985.67, listOf(Bar(1, 15), Bar(356, 360))),
        Item("Permits/Licenses", 200000.0, listOf(Bar(16, 45))),
        Item("Temporary Facilities", 483840.0, listOf(Bar(16, 45))),
        Item("Safety and Health", 302400.0, listOf(Bar(16, 355))),
        Item("Survey Works", 1669468.37, listOf(Bar(46, 90))),
        Item("Site Preparations", 6823711.50, listOf(Bar(46, 165))),
        Item("Roadworks", 10464952.20, listOf(Bar(61, 300))),
        Item("Drainage Works", 6557196.92, listOf(Bar(166, 300))),
        Item("Water Supply System", 3251701.34, listOf(Bar(166, 300))),
        Item("Electrical System", 415744.0, listOf(Bar(301, 355))),
        Item("Housing Construction", 228766486.0, listOf(Bar(76, 355)))
    ))

    private fun project(chart: List<Version>, start: String = "2026-01-01") = Project(
        name = "SIH", location = "", implementingOffice = "", contractor = "", scopeOfWork = "",
        contractCostOriginal = 0.0, contractCostRevised = 0.0, contractDurationDays = 360,
        dateStarted = start, completionDateOriginal = "", completionDateRevised = "",
        barChartJson = BarChart.toJson(chart)
    )

    private fun approved(name: String, remarks: String) = PendingDocument(projectId = 1, documentName = name, status = "Approved", remarks = remarks)

    @Test
    fun months_matchTheApprovedChartsCumulativeCashFlow() {
        val excel = listOf(14711306.53, 29607168.37, 46016224.00, 73567648.19, 101119072.38, 128907410.20,
            156932661.68, 184957913.16, 212983164.64, 241008416.08, 265772562.81, 286822098.20)
        val months = BarChart.months(sanIldefonso)
        assertEquals(12, months.size)
        months.zip(excel).forEach { (m, cash) -> assertEquals("Month ${m.no}", cash, m.cumulativeCash, 0.05) }
        assertEquals(5.13, months[0].cumulativePct, 0.005)
        assertEquals(100.0, months[11].cumulativePct, 1e-9)
    }

    @Test
    fun target_onADate_spreadsEachBarPerDay() {
        val p = project(listOf(sanIldefonso))
        // Day 30 is the end of Month 1.
        assertEquals(5.129, BarChart.targetOn(p, emptyList(), LocalDate.of(2026, 1, 30))!!, 0.001)
        assertEquals(0.0, BarChart.targetOn(p, emptyList(), LocalDate.of(2025, 12, 31))!!, 1e-9)
        assertEquals(100.0, BarChart.targetOn(p, emptyList(), LocalDate.of(2027, 6, 1))!!, 1e-9)
        assertNull(BarChart.targetOn(project(emptyList()), emptyList(), LocalDate.of(2026, 1, 30)))
    }

    @Test
    fun itemTargets_areEachItemsOwnShare() {
        val targets = BarChart.itemTargetsOn(project(listOf(sanIldefonso)), emptyList(), LocalDate.of(2026, 1, 30))!!
        assertEquals(50.0, targets["Land Acquisition"]!!, 1e-9)
        assertEquals(75.0, targets["Mobilization/Demobilization"]!!, 1e-9)
        assertEquals(0.0, targets["Housing Construction"]!!, 1e-9)
    }

    @Test
    fun fullSuspension_freezesTheTarget_partialDoesNot() {
        val p = project(listOf(sanIldefonso))
        val full = approved("Work Suspension Order No. 1", "Duration: 10 Days | Effectivity: 2026-01-21 | End: 2026-01-31")
        // Jan 21-30 suspended, so Feb 9 is read as contract day 30.
        assertEquals(5.129, BarChart.targetOn(p, listOf(full), LocalDate.of(2026, 2, 9))!!, 0.001)
        assertEquals(BarChart.targetOn(p, listOf(full), LocalDate.of(2026, 1, 20)), BarChart.targetOn(p, listOf(full), LocalDate.of(2026, 1, 30)))
        val partial = approved("Partial Work Suspension Order No. 1", full.remarks)
        assertEquals(BarChart.targetOn(p, emptyList(), LocalDate.of(2026, 2, 9)), BarChart.targetOn(p, listOf(partial), LocalDate.of(2026, 2, 9)))
        val pending = full.copy(status = "Pending")
        assertEquals(BarChart.targetOn(p, emptyList(), LocalDate.of(2026, 2, 9)), BarChart.targetOn(p, listOf(pending), LocalDate.of(2026, 2, 9)))
    }

    @Test
    fun partialSuspension_pausesOnlyTheItemsItNames_andItsContinuancesFollow() {
        val p = project(listOf(sanIldefonso))
        val partial = approved(
            "Partial Work Suspension Order No. 1",
            "Duration: 10 Days | Effectivity: 2026-01-21 | End: 2026-01-31 | Reason: ROW" + BarChart.itemsRemark(listOf("Land Acquisition"))
        )
        val feb9 = LocalDate.of(2026, 2, 9) // calendar day 40
        val items = BarChart.itemTargetsOn(p, listOf(partial), feb9)!!
        assertEquals(50.0, items["Land Acquisition"]!!, 1e-9) // paused 10 days: day 30 of 60
        assertEquals(83.33, items["Permits/Licenses"]!!, 1e-9) // goes on: day 40 is its 25th of 30
        val continuance = approved("1st Partial Continuance of Work Suspension Order No. 1", "Duration: 5 Days | Effectivity: 2026-01-31 | End: 2026-02-05")
        assertEquals(41.67, BarChart.itemTargetsOn(p, listOf(partial, continuance), feb9)!!["Land Acquisition"]!!, 1e-9)
        // The overall target loses only the paused item's share.
        val expected = BarChart.targetOn(p, emptyList(), feb9)!! - 26235612.20 * (10.0 / 60) / sanIldefonso.total * 100
        assertEquals(expected, BarChart.targetOn(p, listOf(partial), feb9)!!, 1e-9)
    }

    @Test
    fun openSuspension_endsOnItsResumptionOrder() {
        val p = project(listOf(sanIldefonso))
        val open = approved("Work Suspension Order No. 1", "Duration: 0 Days | Effectivity: 2026-01-21")
        val resumed = approved("Work Resumption Order No. 1", "Effectivity: 2026-01-31")
        assertEquals(5.129, BarChart.targetOn(p, listOf(open, resumed), LocalDate.of(2026, 2, 9))!!, 0.001)
        // Without a resumption it is still suspended: frozen at Jan 20.
        assertEquals(BarChart.targetOn(p, emptyList(), LocalDate.of(2026, 1, 20)), BarChart.targetOn(p, listOf(open), LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun revisedChart_appliesFromItsEffectiveDate_andAlreadyDrawsEarlierSuspensions() {
        val revised = Version("Revised No. 1", "2026-03-01", listOf(Item("All works", 100.0, listOf(Bar(1, 100)))))
        val p = project(listOf(sanIldefonso, revised))
        val suspension = approved("Work Suspension Order No. 1", "Duration: 10 Days | Effectivity: 2026-01-21 | End: 2026-01-31")
        // Before Mar 1 the original chart, frozen 10 days.
        assertEquals(5.129, BarChart.targetOn(p, listOf(suspension), LocalDate.of(2026, 2, 9))!!, 0.001)
        // From Mar 1 (calendar day 60) the revised chart, which already shows the January idle days.
        assertEquals(60.0, BarChart.targetOn(p, listOf(suspension), LocalDate.of(2026, 3, 1))!!, 1e-9)
        // A suspension after the revision still freezes it.
        val later = approved("Work Suspension Order No. 2", "Duration: 5 Days | Effectivity: 2026-03-02 | End: 2026-03-07")
        assertEquals(61.0, BarChart.targetOn(p, listOf(suspension, later), LocalDate.of(2026, 3, 7))!!, 1e-9)
    }

    @Test
    fun json_roundTrips_andJunkIsNoChart() {
        assertEquals(listOf(sanIldefonso), BarChart.parse(BarChart.toJson(listOf(sanIldefonso))))
        assertEquals(emptyList<Version>(), BarChart.parse(""))
        assertEquals(emptyList<Version>(), BarChart.parse("not json"))
    }

    @Test
    fun cells_mapToContractDays() {
        assertEquals(16, BarChart.cellStart(1, 20))
        assertEquals(45, BarChart.cellEnd(2, 15))
        assertEquals(2 to 15, BarChart.cellOf(45))
        assertEquals(1 to 5, BarChart.cellOf(1))
        assertEquals(12 to 30, BarChart.cellOf(360))
    }

    @Test
    fun withPlannedTarget_leavesCompletedAndChartlessProjectsAlone() {
        val day = LocalDate.of(2026, 1, 30)
        val done = project(listOf(sanIldefonso)).copy(targetAccomplishment = 100.0, actualAccomplishment = 100.0)
        assertEquals(done, BarChart.withPlannedTarget(done, emptyList(), day))
        val typed = project(emptyList()).copy(targetAccomplishment = 42.0)
        assertEquals(typed, BarChart.withPlannedTarget(typed, emptyList(), day))
        assertEquals(5.13, BarChart.withPlannedTarget(project(listOf(sanIldefonso)), emptyList(), day).targetAccomplishment, 1e-9)
    }

    @Test
    fun status_isSuspendedDuringAFullSuspension_andResumesOnItsResumptionOrder() {
        val p = project(listOf(sanIldefonso)).copy(actualAccomplishment = 50.0) // ahead, so not behind once it resumes
        val full = approved("Work Suspension Order No. 1", "Duration: 30 Days | Effectivity: 2026-01-21 | End: 2026-02-20")
        val resumed = approved("Work Resumption Order No. 1", "Effectivity: 2026-02-10")
        fun status(docs: List<PendingDocument>, date: LocalDate) = BarChart.withSchedule(p, docs, date).status
        assertEquals("On-going", status(listOf(full), LocalDate.of(2026, 1, 20)))
        assertEquals("Suspended", status(listOf(full), LocalDate.of(2026, 1, 21)))
        assertEquals("Suspended", status(listOf(full), LocalDate.of(2026, 2, 10)))
        // The resumption order ends it before its End date, and the target picks up again from then.
        assertEquals("On-going", status(listOf(full, resumed), LocalDate.of(2026, 2, 10)))
        assertEquals(BarChart.targetOn(p, emptyList(), LocalDate.of(2026, 1, 20)), BarChart.targetOn(p, listOf(full, resumed), LocalDate.of(2026, 2, 9)))
        assertEquals("On-going", status(listOf(full), LocalDate.of(2026, 2, 20)))
        // Pending and Partial suspensions leave it going.
        assertEquals("On-going", status(listOf(full.copy(status = "Pending")), LocalDate.of(2026, 2, 1)))
        assertEquals("On-going", status(listOf(full.copy(documentName = "Partial Work Suspension Order No. 1")), LocalDate.of(2026, 2, 1)))
    }

    @Test
    fun status_anyNegativeSlippageIsBehind_elseOnGoing() {
        val stored = project(listOf(sanIldefonso)).copy(status = "Suspended")
        // The target on Jan 30 is 5.13%.
        fun status(actual: Double, was: String = "Suspended") =
            BarChart.withSchedule(stored.copy(actualAccomplishment = actual, status = was), emptyList(), LocalDate.of(2026, 1, 30)).status
        assertEquals("Behind Schedule", status(5.01))
        assertEquals("On-going", status(5.13))
        assertEquals("On-going", status(6.0, was = "Behind Schedule"))
        assertEquals("Behind Schedule", status(5.12, was = "On-going"))
        val done = stored.copy(actualAccomplishment = 100.0, targetAccomplishment = 100.0)
        assertEquals(done, BarChart.withSchedule(done, emptyList(), LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun suspension_pausesThroughItsEnd_andGivesItsDaysToATimeExtension() {
        val p = project(listOf(sanIldefonso)).copy(actualAccomplishment = 99.0) // ahead, so not behind once it resumes
        // Saved before the effectivity date counted as day 1, with its End a day late.
        val ws = approved("Work Suspension Order No. 1", "Duration: 90 Days | Effectivity: 2026-05-26 | End: 2026-08-24")
        assertEquals("Suspended", BarChart.withSchedule(p, listOf(ws), LocalDate.of(2026, 8, 23)).status)
        assertEquals("On-going", BarChart.withSchedule(p, listOf(ws), LocalDate.of(2026, 8, 24)).status)
        assertEquals(90L, BarChart.suspendedDays(listOf(ws), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
        val c1 = approved("1st Continuance of Work Suspension Order No. 1", com.example.util.ProjectSchedule.suspensionRemarks(30, "2026-08-24"))
        assertEquals("Suspended", BarChart.withSchedule(p, listOf(ws, c1), LocalDate.of(2026, 9, 22)).status)
        assertEquals("On-going", BarChart.withSchedule(p, listOf(ws, c1), LocalDate.of(2026, 9, 23)).status)
        assertEquals(120, BarChart.suspensionExtensionDays(listOf(ws, c1)))
        // A pending continuance adds nothing yet; a resumption cuts the suspension short (and leaves the continuance none).
        assertEquals(90, BarChart.suspensionExtensionDays(listOf(ws, c1.copy(status = "Pending"))))
        assertEquals(10, BarChart.suspensionExtensionDays(listOf(ws, c1, approved("Work Resumption Order No. 1", "Effectivity: 2026-06-05"))))
        // Days already given by a time extension for a suspension are not asked for again.
        val te = approved("Time Extension No. 1 - 90 Days", "Reason: Work Suspension Order No. 1")
        assertEquals(30, BarChart.suspensionExtensionDays(listOf(ws, c1, te)))
        assertEquals(120, BarChart.suspensionExtensionDays(listOf(ws, c1, te.copy(remarks = "Reason: Typhoon"))))
    }
}
