package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.components.SyncChip
import com.example.ui.components.meaning
import com.example.ui.theme.LocalNHAColors
import com.example.ui.theme.TabularNumbers
import com.example.util.PortfolioFilter
import com.example.util.PortfolioSummary
import com.example.util.ProjectAttention

/** The non-zero counts (a zero has nothing to filter to); tapping one filters the project list, tapping it again clears the filter. */
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
    ).filter { it.count > 0 || it.filter == selected }
    if (tiles.isEmpty()) return
    val rows = if (wide) listOf(tiles) else tiles.chunked(2)
    var showHelp by rememberSaveable { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile ->
                    CountTileView(tile, tile.filter == selected, onClick = { onFilter(tile.filter) }, modifier = Modifier.weight(1f))
                }
                // An odd tile on a phone keeps the half width of its neighbours.
                if (!wide && row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        TextButton(onClick = { showHelp = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("What these mean", style = MaterialTheme.typography.labelLarge)
        }
    }
    if (showHelp) TermHelpDialog(onDismiss = { showHelp = false })
}

@Composable
internal fun TermHelpDialog(onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What these mean", color = scheme.onSurface) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TERM_HELP.forEach { (term, meaning) ->
                    if (term == SYNC_HELP_FIRST) {
                        HorizontalDivider(color = scheme.outlineVariant)
                        Text("Sync chip at the top", style = MaterialTheme.typography.labelLarge, color = scheme.primary)
                    }
                    Column {
                        Text(term, style = MaterialTheme.typography.titleSmall, color = scheme.onSurface)
                        Text(meaning, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
                    }
                }
            }
        },
        containerColor = scheme.surface,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

private fun pct(points: Double) = "%.0f%%".format(points)

private const val SYNC_HELP_FIRST = "SYNCED"

/** The count tiles and the top-bar sync chip in plain words, from the same thresholds ProjectAttention checks. */
internal val TERM_HELP = listOf(
    "Behind schedule" to "Work done is ${pct(-ProjectAttention.WARNING_SLIPPAGE)} or more behind the plan, or the project is marked Behind Schedule. " +
        "Shown in red from ${pct(-ProjectAttention.CRITICAL_SLIPPAGE)} behind.",
    "Billing ahead of work" to "Billed is more than ${pct(ProjectAttention.BILLING_GAP_POINTS)} ahead of work done, " +
        "for example billed 65%, work done 38.50%.",
    "Waiting approval" to "Variation orders, time extensions, suspensions and resumptions not yet approved.",
    "Due in ${ProjectAttention.DUE_SOON_DAYS} days" to "Unfinished projects whose completion date (the revised one, if any) is ${ProjectAttention.DUE_SOON_DAYS} days away or less.",
    SYNC_HELP_FIRST to SyncChip.Synced.meaning,
    "UPLOADING" to SyncChip.Uploading(0).meaning,
    "CHANGES WAITING" to SyncChip.Waiting(0).meaning + " Tap the chip to retry now.",
    "THIS PHONE ONLY" to SyncChip.PhoneOnly.meaning
)

private data class CountTile(val filter: PortfolioFilter, val label: String, val count: Int, val icon: ImageVector, val tint: Color)

@Composable
private fun CountTileView(tile: CountTile, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    // A zero count is not a problem, so it is shown in the secondary text colour.
    val tint = if (tile.count > 0) tile.tint else scheme.onSurfaceVariant
    // selected + onClick makes the tile selectable, so TalkBack reads it as a filter that is on or off.
    Surface(
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = scheme.surface,
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) scheme.primary else scheme.outlineVariant),
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
                Spacer(Modifier.weight(1f))
                // The corner mark says the tile filters the list, and shows when it does.
                Icon(
                    if (isSelected) Icons.Default.Check else Icons.Default.FilterList,
                    contentDescription = null,
                    tint = if (isSelected) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
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
