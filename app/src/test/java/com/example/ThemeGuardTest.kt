package com.example

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

// Screens moved to the theme must not hand-set colours; the list grows each stage.
class ThemeGuardTest {
    companion object {
        val MIGRATED = listOf(
            "ui/components/TopAppBarHeader.kt",
            "ui/dashboard/DashboardScreen.kt",
            "ui/dashboard/PortfolioContent.kt",
            "ui/dashboard/PortfolioRow.kt",
            "ui/dashboard/PortfolioCounts.kt",
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
            "ui/detail/tabs/IssuesTab.kt",
            "ui/detail/tabs/MonthlyReportsTab.kt",
            "ui/detail/tabs/PendingDocumentsTab.kt",
            "ui/detail/tabs/PhotoGalleryTab.kt",
            "ui/detail/tabs/WeeklyReportsTab.kt"
        )
    }

    @Test
    fun migratedScreens_takeColoursFromTheTheme() {
        val offenders = MIGRATED.flatMap { path ->
            File("src/main/java/com/example", path).readLines().mapIndexedNotNull { i, line ->
                if ("Color(0x" in line) "$path:${i + 1}" else null
            }
        }
        assertEquals("Hand-set colours outside ui/theme: $offenders", emptyList<String>(), offenders)
    }
}
