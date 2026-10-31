package com.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.data.model.AppThemeMode
import com.example.data.model.Project
import com.example.ui.dashboard.PortfolioContent
import com.example.ui.theme.NHATheme
import com.example.ui.viewmodel.PortfolioUiState
import com.example.util.AttentionFlag
import com.example.util.AttentionKind
import com.example.util.AttentionLevel
import com.example.util.Portfolio
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
class PortfolioScreenshotTest {

    private fun project(id: Long, name: String, location: String, status: String, target: Double, actual: Double, completion: String) =
        Project(
            id = id, name = name, location = location, implementingOffice = "NHA Bulacan", contractor = "C",
            scopeOfWork = "[]", contractCostOriginal = 10_000_000.0, contractCostRevised = 10_000_000.0,
            contractDurationDays = 365, dateStarted = "2026-01-05", completionDateOriginal = completion,
            completionDateRevised = "-", status = status, targetAccomplishment = target, actualAccomplishment = actual
        )

    private fun flag(kind: AttentionKind, level: AttentionLevel, message: String) = AttentionFlag(kind, level, message)

    private fun item(p: Project, billed: Double, docs: Int, update: LocalDate?, vararg flags: AttentionFlag) = PortfolioItem(
        project = p, flags = flags.toList(),
        billedFraction = if (billed > 0) (billed / p.contractCostOriginal).toFloat() else null,
        billedTotal = billed, contractCost = p.contractCostOriginal, pendingDocsCount = docs, lastFieldUpdate = update
    )

    private val items = listOf(
        item(project(1, "Pandi Residences Phase 2 Site Development", "Pandi, Bulacan", "Behind Schedule", 62.0, 44.0, "2026-10-20"),
            4_000_000.0, 1, LocalDate.of(2026, 10, 1),
            flag(AttentionKind.BEHIND, AttentionLevel.CRITICAL, "18.0% behind schedule"),
            flag(AttentionKind.OVERDUE, AttentionLevel.CRITICAL, "Overdue by 6 days")),
        item(project(2, "Northville 8 Housing Units, Block 12 to Block 18 Concrete Roads and Drainage Works", "Barangay Santa Rosa I, Marilao, Bulacan, Region III Central Luzon", "On-going", 40.0, 38.5, "2026-11-20"),
            6_500_000.0, 3, LocalDate.of(2026, 10, 22),
            flag(AttentionKind.BILLING_AHEAD, AttentionLevel.WARNING, "Billed 65.0%, work done 38.5%"),
            flag(AttentionKind.WAITING_APPROVAL, AttentionLevel.WARNING, "1 order waiting approval"),
            flag(AttentionKind.DUE_SOON, AttentionLevel.WARNING, "Due in 25 days")),
        item(project(3, "San Jose del Monte Townhouses", "SJDM, Bulacan", "On-going", 30.0, 19.0, "2027-05-30"),
            2_000_000.0, 0, LocalDate.of(2026, 10, 24),
            flag(AttentionKind.BEHIND, AttentionLevel.WARNING, "11.0% behind schedule")),
        item(project(4, "Bocaue Resettlement Water System", "Bocaue, Bulacan", "On-going", 20.0, 22.5, "2027-08-15"),
            0.0, 0, null,
            flag(AttentionKind.NO_RECENT_UPDATE, AttentionLevel.STALE, "No field update in 294 days")),
        item(project(5, "Plaridel Multipurpose Hall", "Plaridel, Bulacan", "Suspended", 50.0, 50.0, "2027-01-10"),
            5_000_000.0, 2, LocalDate.of(2026, 9, 15)),
        item(project(6, "Malolos Perimeter Fence", "Malolos, Bulacan", "On-going", 100.0, 100.0, "2026-09-30"),
            9_800_000.0, 0, LocalDate.of(2026, 9, 28))
    )

    private val sample = PortfolioUiState(
        summary = Portfolio.summarize(items),
        attention = Portfolio.attentionQueue(items),
        rows = Portfolio.sortByAttention(items),
        filter = null,
        totalProjects = items.size
    )

    private fun capture(state: PortfolioUiState, mode: AppThemeMode, empty: Pair<String, String> = "" to "", projectsFirst: Boolean = false) =
        captureRoboImage {
            NHATheme(themeMode = mode) { Surface(color = MaterialTheme.colorScheme.background) {
                PortfolioContent(
                    state = state, officeName = "Bulacan District Office",
                    searchQuery = "", sortOrder = Portfolio.NEEDS_ATTENTION_SORT, statusFilter = "All", typeFilter = "All",
                    typeOptions = listOf("All", "Housing Project"), emptyMessage = empty,
                    onFilter = {}, onSearch = {}, onSort = {}, onStatus = {}, onType = {},
                    onOpen = {}, onEdit = {}, onDelete = {}, projectsFirst = projectsFirst
                ) }
            }
        }

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun phone_light() = capture(sample, AppThemeMode.LIGHT)

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun phone_dark() = capture(sample, AppThemeMode.DARK)

    // A field engineer sees their own projects before the office overview.
    @Config(qualifiers = "w411dp-h891dp")
    @Test fun field_engineer_phone_light() = capture(sample, AppThemeMode.LIGHT, projectsFirst = true)

    @Config(qualifiers = "w743dp-h1185dp")
    @Test fun tablet_portrait_light() = capture(sample, AppThemeMode.LIGHT)

    @Config(qualifiers = "w1185dp-h743dp")
    @Test fun tablet_landscape_light() = capture(sample, AppThemeMode.LIGHT)

    @Config(qualifiers = "w411dp-h891dp")
    @Test fun empty_light() = capture(
        PortfolioUiState.EMPTY, AppThemeMode.LIGHT,
        "Database is Empty" to "No projects exist yet. Create a new project or sync from cloud."
    )
}
