package com.example.ui.detail.tabs

import androidx.compose.runtime.Composable
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.core.entry.entryModelOf

@Composable
fun TestChart() {
    val model = entryModelOf(1, 2, 3, 4)
    Chart(
        chart = lineChart(),
        model = model,
        startAxis = rememberStartAxis(),
        bottomAxis = rememberBottomAxis(),
    )
}
