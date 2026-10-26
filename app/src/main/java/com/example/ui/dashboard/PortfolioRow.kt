package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.data.model.ProjectStatus
import com.example.ui.theme.LocalNHAColors
import com.example.ui.theme.NHAColors
import com.example.ui.theme.TabularNumbers
import com.example.util.AttentionLevel
import com.example.util.PortfolioItem
import com.example.util.ProjectAttention
import com.example.util.ProjectSchedule
import java.text.NumberFormat
import java.util.Locale

/** One project in the "All projects" list. The two bars sit side by side when [wide]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortfolioRow(
    item: PortfolioItem,
    wide: Boolean,
    onOpen: (Project) -> Unit,
    onEdit: ((Project) -> Unit)?,
    onDelete: ((Project) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val project = item.project
    val scheme = MaterialTheme.colorScheme
    val colors = LocalNHAColors.current
    val varianceColor = varianceColor(project.variance, colors)

    Surface(
        onClick = { onOpen(project) },
        shape = RoundedCornerShape(12.dp),
        color = scheme.surface,
        border = BorderStroke(1.dp, scheme.outline),
        modifier = modifier.fillMaxWidth().testTag("project_tile_${project.id}")
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 12.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Badge(formatVariance(project.variance), varianceColor, containerOf(varianceColor, colors))
                if (onEdit != null || onDelete != null) RowMenu(project, onEdit, onDelete) else Spacer(Modifier.width(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                Text(
                    text = project.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(project.status, colors)
            }

            Spacer(Modifier.height(8.dp))
            val noBillings = item.billedFraction == null || item.billedTotal <= 0
            val workDone: @Composable (Modifier) -> Unit = { m ->
                ProgressLine(
                    label = "Work done",
                    value = "%.1f%%".format(project.actualAccomplishment),
                    fraction = (project.actualAccomplishment / 100).toFloat(),
                    color = varianceColor,
                    modifier = m
                )
            }
            val billed: @Composable (Modifier) -> Unit = { m ->
                val fraction = item.billedFraction ?: 0f
                ProgressLine(
                    label = "Billed",
                    value = if (noBillings) "No billings yet" else "%.1f%% · %s".format(fraction * 100, peso(item.billedTotal)),
                    fraction = if (noBillings) 0f else fraction,
                    color = scheme.primary,
                    modifier = m
                )
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(end = 8.dp)) {
                    workDone(Modifier.weight(1f))
                    billed(Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 8.dp)) {
                    workDone(Modifier.fillMaxWidth())
                    billed(Modifier.fillMaxWidth())
                }
            }

            item.flags.firstOrNull()?.let { flag ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = flag.message + if (item.flags.size > 1) "  +${item.flags.size - 1} more" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = levelColor(flag.level, colors),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val completion = ProjectSchedule.effectiveCompletion(project.completionDateOriginal, project.completionDateRevised)
                MetaItem(Icons.Default.Event, "Completion ${completion.ifBlank { "-" }}")
                MetaItem(Icons.Default.Description, if (item.pendingDocsCount == 1) "1 document pending" else "${item.pendingDocsCount} documents pending")
                MetaItem(Icons.Default.Update, item.lastFieldUpdate?.let { "Field update $it" } ?: "No field update yet")
            }
        }
    }
}

@Composable
private fun RowMenu(project: Project, onEdit: ((Project) -> Unit)?, onDelete: ((Project) -> Unit)?) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More actions for ${project.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onEdit != null) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { open = false; onEdit(project) },
                    modifier = Modifier.testTag("edit_project_button_${project.id}")
                )
            }
            if (onDelete != null) {
                val red = LocalNHAColors.current.behind
                DropdownMenuItem(
                    text = { Text("Delete", color = red) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = red) },
                    onClick = { open = false; onDelete(project) },
                    modifier = Modifier.testTag("delete_project_button_${project.id}")
                )
            }
        }
    }
}

@Composable
private fun ProgressLine(label: String, value: String, fraction: Float, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Row {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                value,
                style = MaterialTheme.typography.labelMedium.merge(TabularNumbers),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = color,
            trackColor = MaterialTheme.colorScheme.outline,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )
    }
}

@Composable
private fun MetaItem(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusBadge(status: String, colors: NHAColors) {
    val color = when (status) {
        ProjectStatus.BEHIND_SCHEDULE.label -> colors.behind
        ProjectStatus.SUSPENDED.label -> colors.neutral
        else -> colors.onTrack
    }
    Badge(status, color, containerOf(color, colors))
}

@Composable
internal fun Badge(text: String, color: Color, container: Color) {
    Surface(color = container, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.merge(TabularNumbers),
            fontWeight = FontWeight.Bold,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

internal fun levelColor(level: AttentionLevel, colors: NHAColors) = when (level) {
    AttentionLevel.CRITICAL -> colors.behind
    AttentionLevel.WARNING -> colors.watch
    AttentionLevel.STALE -> colors.neutral
}

private fun varianceColor(variance: Double, colors: NHAColors) = when {
    variance >= 0 -> colors.onTrack
    variance <= ProjectAttention.CRITICAL_SLIPPAGE -> colors.behind
    variance <= ProjectAttention.WARNING_SLIPPAGE -> colors.watch
    else -> colors.neutral
}

private fun containerOf(color: Color, colors: NHAColors) = when (color) {
    colors.onTrack -> colors.onTrackContainer
    colors.behind -> colors.behindContainer
    colors.watch -> colors.watchContainer
    else -> colors.neutralContainer
}

private fun formatVariance(v: Double) = if (v >= 0) "+%.1f%%".format(v) else "−%.1f%%".format(-v)

private fun peso(amount: Double): String = NumberFormat.getCurrencyInstance(Locale("en", "PH")).format(amount)
