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
            "ui/dashboard/PortfolioCounts.kt"
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
