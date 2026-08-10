package com.example.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.DashboardSummaryStats
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkTextPrimary
import com.example.ui.theme.StatusOrangeText
import com.example.ui.theme.StatusRedText

@Composable
fun DashboardSummaryCard(stats: DashboardSummaryStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatBox("ACTIVE PROJECTS", "${stats.activeProjectsCount}", DarkTextPrimary)
        StatBox("SLIPPAGE / BEHIND", "${stats.slippageProjectsCount}", StatusRedText)
        StatBox("PENDING DOCS", "${stats.pendingDocsCount}", StatusOrangeText)
    }
}
