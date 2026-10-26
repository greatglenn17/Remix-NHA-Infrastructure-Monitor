package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalNHAColors
import com.example.ui.theme.TabularNumbers
import com.example.util.PortfolioFilter
import com.example.util.PortfolioSummary

/** The four counts; tapping one filters the project list, tapping it again clears the filter. */
@Composable
fun PortfolioCounts(
    summary: PortfolioSummary,
    selected: PortfolioFilter?,
    wide: Boolean,
    onFilter: (PortfolioFilter?) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalNHAColors.current
    val tiles = listOf(
        CountTile(PortfolioFilter.BEHIND, "Behind schedule", summary.behind, Icons.Default.TrendingDown, colors.behind),
        CountTile(PortfolioFilter.BILLING_AHEAD, "Billing ahead of work", summary.billingAhead, Icons.Default.Payments, colors.watch),
        CountTile(PortfolioFilter.WAITING_APPROVAL, "Waiting approval", summary.waitingApproval, Icons.Default.HourglassTop, colors.watch),
        CountTile(PortfolioFilter.DUE_SOON, "Due in 30 days", summary.dueSoon, Icons.Default.Event, colors.watch)
    )
    val rows = if (wide) listOf(tiles) else tiles.chunked(2)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    CountTileView(tile, tile.filter == selected, onClick = { onFilter(tile.filter) }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private data class CountTile(val filter: PortfolioFilter, val label: String, val count: Int, val icon: ImageVector, val tint: Color)

@Composable
private fun CountTileView(tile: CountTile, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    // A zero count is not a problem, so it is shown in the secondary text colour.
    val tint = if (tile.count > 0) tile.tint else scheme.onSurfaceVariant
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = scheme.surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) scheme.primary else scheme.outline),
        modifier = modifier.heightIn(min = 72.dp).testTag("count_${tile.filter.name.lowercase()}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(tile.icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${tile.count}",
                    style = MaterialTheme.typography.headlineSmall.merge(TabularNumbers),
                    fontWeight = FontWeight.Bold,
                    color = tint
                )
            }
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
