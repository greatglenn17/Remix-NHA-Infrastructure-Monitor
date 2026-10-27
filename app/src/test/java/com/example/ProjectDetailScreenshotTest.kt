package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.AppThemeMode
import com.example.data.model.PendingDocument
import com.example.data.model.Project
import com.example.data.model.ProjectPayment
import com.example.data.model.UserRole
import com.example.data.model.WeeklyReport
import com.example.ui.dashboard.NeedsAttentionPane
import com.example.ui.dashboard.PortfolioContent
import com.example.ui.dashboard.TwoPanes
import com.example.ui.viewmodel.PortfolioUiState
import com.example.util.Portfolio
import com.example.ui.detail.SectionChips
import com.example.ui.detail.sections.ChipRow
import com.example.ui.detail.sections.MoneySection
import com.example.ui.detail.sections.ProjectHeader
import com.example.ui.detail.sections.SummarySection
import com.example.ui.detail.tabs.PendingDocumentsTab
import com.example.ui.detail.tabs.WeeklyReportsTab
import com.example.ui.theme.NHATheme
import com.example.util.AttentionFlag
import com.example.util.AttentionKind
import com.example.util.AttentionLevel
import com.example.util.PortfolioItem
import com.github.takahirom.roborazzi.captureRoboImage
import java.time.LocalDate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// Baselines: ./gradlew recordRoborazziDebug; check: ./gradlew verifyRoborazziDebug
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectDetailScreenshotTest {

    private val project = Project(
        id = 7,
        name = "Northville 8 Housing Units, Block 12 to Block 18 Concrete Roads and Drainage Works Phase 2",
        location = "Barangay Santa Rosa I, Marilao, Bulacan",
        implementingOffice = "NHA Bulacan District Office", contractor = "JRC Builders",
        scopeOfWork = """[{"name":"Site development","amount":6000000,"weightPct":60},{"name":"Drainage","amount":4000000,"weightPct":40}]""",
        projectType = "Housing Project", landArea = "2.4 ha", numberOfUnits = "120",
        contractCostOriginal = 10_000_000.0, contractCostRevised = 10_000_000.0,
        contractDurationDays = 300, dateStarted = "2026-01-05", completionDateOriginal = "2026-11-01",
        completionDateRevised = "-", status = "Behind Schedule", targetAccomplishment = 62.0, actualAccomplishment = 44.0
    )

    private val docs = listOf(
        PendingDocument(id = 1, projectId = 7, documentName = "Variation Order No. 1 (Additive) - PHP 450,000", status = "Approved", remarks = "Reason: Added culvert | Date Approved: 2026-06-02"),
        PendingDocument(id = 2, projectId = 7, documentName = "Variation Order No. 2 (Deductive) - PHP 120,000", status = "Pending"),
        PendingDocument(id = 3, projectId = 7, documentName = "Time Extension No. 1 - 30 Days", status = "Approved", remarks = "Reason: Typhoon Kristine"),
        PendingDocument(id = 4, projectId = 7, documentName = "Contractor Notice to Proceed (NTP)", status = "Approved"),
        PendingDocument(id = 5, projectId = 7, documentName = "Program of Works", status = "Submitted"),
        PendingDocument(id = 6, projectId = 7, documentName = "As-built Plans", status = "Pending", isCoreChecklist = false)
    )

    private val payments = listOf(
        ProjectPayment(id = 1, projectId = 7, name = "1st Progress Billing", dvNo = "DV-2026-03-0141", date = "2026-03-20", periodCovered = "Jan 5 – Mar 15, 2026", grossAmount = 1_800_000.0, percentage = 17.21, balanceAmount = 8_650_000.0, balancePercentage = 82.79, fileUrl = "scan://dv141"),
        ProjectPayment(id = 2, projectId = 7, name = "2nd Progress Billing", dvNo = "DV-2026-06-0388", date = "2026-06-18", periodCovered = "Mar 16 – Jun 10, 2026", grossAmount = 2_200_000.0, percentage = 21.05, balanceAmount = 6_450_000.0, balancePercentage = 61.72),
        ProjectPayment(id = 3, projectId = 7, name = "3rd Progress Billing", dvNo = "", date = "2026-09-02", periodCovered = "Jun 11 – Aug 31, 2026", grossAmount = 1_500_000.0, percentage = 14.35, balanceAmount = 4_950_000.0, balancePercentage = 47.37),
        ProjectPayment(id = 4, projectId = 7, name = "4th Progress Billing", dvNo = "DV-2026-10-0502", date = "2026-10-15", periodCovered = "Sep 1 – Oct 10, 2026", grossAmount = 900_000.0, percentage = 8.61, balanceAmount = 4_050_000.0, balancePercentage = 38.76)
    )

    private fun report(id: Long, week: String, target: Double, actual: Double) = WeeklyReport(
        id = id, projectId = 7, reportingWeek = week, daysElapsed = 280, remainingDays = 50,
        targetAccomplishmentPct = target, actualAccomplishmentPct = actual,
        manpowerJson = "[]", equipmentJson = "[]", activitiesJson = "[]", issuesJson = "[]",
        accomplishmentItemsJson = "[]", documentsIssuedReceivedJson = "[]", attachedPhotoUrlsJson = "[]",
        submittedByStaff = "Engr. R. Santos", createdAtTimestamp = 1_760_000_000_000 + id
    )

    private val reports = listOf(
        report(2, "Week 41: October 5 - October 11, 2026", 62.0, 44.0),
        report(1, "Week 40: September 28 - October 4, 2026", 60.0, 43.0)
    )

    private val flags = listOf(
        AttentionFlag(AttentionKind.BEHIND, AttentionLevel.CRITICAL, "18.0% behind schedule"),
        AttentionFlag(AttentionKind.WAITING_APPROVAL, AttentionLevel.WARNING, "1 order waiting approval")
    )

    @Composable
    private fun frame(mode: AppThemeMode, collapsed: Boolean = false, content: @Composable () -> Unit) {
        NHATheme(themeMode = mode) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                Column {
                    ProjectHeader(project, billedFraction = 0.64f, collapsed = collapsed)
                    Box(modifier = Modifier.weight(1f)) { content() }
                }
            }
        }
    }

    @Composable
    private fun Summary() = SummarySection(
        project, flags, docs, reports.first(), openIssues = 2, canEditProject = true, onEditProject = {},
        today = LocalDate.of(2026, 10, 27)
    )

    @Composable
    private fun Money(payments: List<ProjectPayment> = this.payments, cost: Double = 10_450_000.0) = MoneySection(
        payments = payments, contractCost = cost, actualPercent = project.actualAccomplishment, documents = docs,
        canAddVariationOrder = true, onAddVariationOrder = {},
        onAddPayment = { _, _, _, _, _, _, _, _, _ -> }, onUpdatePayment = {}, onDeletePayment = {}
    )

    @Composable
    private fun Progress() = Column {
        ChipRow(SectionChips.progress(true), "Weekly") {}
        WeeklyReportsTab(reports, UserRole.ENGINEER_ADMIN, {}, {}, {})
    }

    @Composable
    private fun Documents() = Column {
        ChipRow(SectionChips.documents(true), "Checklist") {}
        PendingDocumentsTab(docs.sortedBy { it.status == "Approved" }, UserRole.ENGINEER_ADMIN, {}, { _, _, _ -> }, { _, _ -> }, {})
    }

    private fun sections(width: String, mode: AppThemeMode) {
        val theme = mode.name.lowercase()
        captureRoboImage("src/test/screenshots/detail_summary_${width}_$theme.png") { frame(mode) { Summary() } }
        captureRoboImage("src/test/screenshots/detail_progress_${width}_$theme.png") { frame(mode) { Progress() } }
        captureRoboImage("src/test/screenshots/detail_money_${width}_$theme.png") { frame(mode) { Money() } }
        captureRoboImage("src/test/screenshots/detail_documents_${width}_$theme.png") { frame(mode) { Documents() } }
    }

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun phone_light() {
        sections("411", AppThemeMode.LIGHT)
        captureRoboImage("src/test/screenshots/detail_header_collapsed_411_light.png") { frame(AppThemeMode.LIGHT, collapsed = true) { Summary() } }
        captureRoboImage("src/test/screenshots/detail_money_empty_411_light.png") { frame(AppThemeMode.LIGHT) { Money(emptyList(), 0.0) } }
    }

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun phone_dark() = sections("411", AppThemeMode.DARK)

    @Config(qualifiers = "w743dp-h1185dp")
    @Test fun tablet_portrait_light() = sections("743", AppThemeMode.LIGHT)

    @Config(qualifiers = "w743dp-h1185dp")
    @Test fun tablet_portrait_dark() = sections("743", AppThemeMode.DARK)

    @Config(qualifiers = "w1185dp-h743dp")
    @Test fun tablet_landscape_light() {
        sections("1185", AppThemeMode.LIGHT)
        val item = PortfolioItem(project, flags, 0.64f, 6_400_000.0, 10_450_000.0, 1, LocalDate.of(2026, 10, 11))
        captureRoboImage("src/test/screenshots/detail_two_pane_empty_1185_light.png") {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    NeedsAttentionPane(flags.map { item to it }) {}
                }
            }
        }
    }

    @Config(qualifiers = "w1185dp-h743dp")
    @Test fun tablet_landscape_dark() = sections("1185", AppThemeMode.DARK)

    // The real two-pane row: 380dp portfolio list, divider, project in the remaining width.
    @Config(qualifiers = "w1185dp-h743dp")
    @Test fun two_pane_project() {
        val item = PortfolioItem(project, flags, 0.64f, 6_400_000.0, 10_450_000.0, 1, LocalDate.of(2026, 10, 11))
        val state = PortfolioUiState(
            summary = Portfolio.summarize(listOf(item)), attention = Portfolio.attentionQueue(listOf(item)),
            rows = listOf(item), filter = null, totalProjects = 1
        )
        captureRoboImage("src/test/screenshots/detail_two_pane_project_1185_light.png") {
            NHATheme(themeMode = AppThemeMode.LIGHT) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    TwoPanes(
                        list = {
                            PortfolioContent(
                                state = state, officeName = "Bulacan District Office",
                                searchQuery = "", sortOrder = Portfolio.NEEDS_ATTENTION_SORT, statusFilter = "All", typeFilter = "All",
                                typeOptions = listOf("All"), emptyMessage = "" to "",
                                onFilter = {}, onSearch = {}, onSort = {}, onStatus = {}, onType = {},
                                onOpen = {}, onEdit = {}, onDelete = {}
                            )
                        },
                        fab = {},
                        detail = { frame(AppThemeMode.LIGHT) { Money() } }
                    )
                }
            }
        }
    }
}
