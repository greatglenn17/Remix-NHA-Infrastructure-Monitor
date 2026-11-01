package com.example

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

// Every screen takes its colours from the theme; no hand-set colours outside ui/theme.
class ThemeGuardTest {
    companion object {
        private val SRC = File("src/main/java/com/example")

        val MIGRATED = listOf(
            "MainActivity.kt",
            "ui/components/AttachmentPicker.kt",
            "ui/components/FormKit.kt",
            "ui/components/SyncChip.kt",
            "ui/components/TopAppBarHeader.kt",
            "ui/components/Zoom.kt",
            "ui/dashboard/DashboardScreen.kt",
            "ui/dashboard/NeedsAttentionPane.kt",
            "ui/dashboard/PortfolioContent.kt",
            "ui/dashboard/PortfolioCounts.kt",
            "ui/dashboard/PortfolioRow.kt",
            "ui/detail/ProjectDetailScreen.kt",
            "ui/detail/ProjectSection.kt",
            "ui/detail/sections/ChipRow.kt",
            "ui/detail/sections/DocumentsSection.kt",
            "ui/detail/sections/MoneySection.kt",
            "ui/detail/sections/PaymentCard.kt",
            "ui/detail/sections/ProjectHeader.kt",
            "ui/detail/sections/SectionCard.kt",
            "ui/detail/sections/SummarySection.kt",
            "ui/detail/tabs/AuditLogTab.kt",
            "ui/detail/tabs/DocumentDialogs.kt",
            "ui/detail/tabs/IssuesTab.kt",
            "ui/detail/tabs/MonthlyReportsTab.kt",
            "ui/detail/tabs/PaymentDialogs.kt",
            "ui/detail/tabs/PhotoGalleryTab.kt",
            "ui/detail/tabs/SubdivisionPlanTab.kt",
            "ui/detail/tabs/WeatherChartTab.kt",
            "ui/detail/tabs/WeeklyReportsTab.kt",
            "ui/dialogs/AddIssueDialog.kt",
            "ui/dialogs/AddMonthlyReportDialog.kt",
            "ui/dialogs/AddSubLogDialogs.kt",
            "ui/dialogs/AddWeeklyReportDialog.kt",
            "ui/dialogs/AssignStaffField.kt",
            "ui/dialogs/CreateProjectDialog.kt",
            "ui/dialogs/DateRangePickerDialog.kt",
            "ui/dialogs/EditProfileDialog.kt",
            "ui/dialogs/EditProjectDialog.kt",
            "ui/dialogs/GoogleDriveSyncDialog.kt",
            "ui/dialogs/ManageUsersDialog.kt",
            "ui/dialogs/NotificationsDialog.kt",
            "ui/dialogs/ScopeOfWorksEditor.kt",
            "ui/detail/sections/DeveloperRequests.kt",
            "ui/dialogs/SignatoriesDialog.kt",
            "ui/dialogs/UpdateAvailableDialog.kt",
            "ui/dialogs/WeekRange.kt",
            "ui/dialogs/WeeklyReportSteps.kt",
            "ui/login/LoginScreen.kt",
            "ui/viewmodel/ProjectViewModel.kt"
        )
    }

    @Test
    fun everyUiFile_isMigrated() {
        val onDisk = SRC.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(SRC).path.replace(File.separatorChar, '/') }
            .filter { (it.startsWith("ui/") && !it.startsWith("ui/theme/")) || it == "MainActivity.kt" }
            .sorted().toList()
        assertEquals("Files missing from MIGRATED / stale entries", onDisk, MIGRATED.sorted())
    }

    @Test
    fun migratedScreens_takeColoursFromTheTheme() {
        val offenders = MIGRATED.flatMap { path ->
            File(SRC, path).readLines().mapIndexedNotNull { i, line ->
                if ("Color(0x" in line) "$path:${i + 1}" else null
            }
        }
        assertEquals("Hand-set colours outside ui/theme: $offenders", emptyList<String>(), offenders)
    }
}
