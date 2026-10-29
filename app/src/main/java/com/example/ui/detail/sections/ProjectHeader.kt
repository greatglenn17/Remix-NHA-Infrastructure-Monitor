package com.example.ui.detail.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.Project
import com.example.ui.dashboard.Badge
import com.example.ui.dashboard.ProgressLine
import com.example.ui.dashboard.StatusBadge
import com.example.ui.dashboard.containerOf
import com.example.ui.dashboard.formatVariance
import com.example.ui.dashboard.varianceColor
import com.example.ui.theme.LocalNHAColors
import com.example.util.BillingProgress
import com.example.util.ProjectAttention

/** Name, location, status, variance and the two bars; [collapsed] keeps only one line; [onClose] shows an X (two panes). */
@Composable
fun ProjectHeader(
    project: Project,
    billedTotal: Double,
    contractCost: Double,
    collapsed: Boolean,
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val billedFraction = BillingProgress.billedFraction(billedTotal, contractCost)?.takeIf { billedTotal > 0 }
    val scheme = MaterialTheme.colorScheme
    val colors = LocalNHAColors.current
    val varianceColor = varianceColor(ProjectAttention.slippage(project), colors)
    Surface(color = scheme.surface, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = if (collapsed) 8.dp else 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.name,
                    style = if (collapsed) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                    maxLines = if (collapsed) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusBadge(project.status, colors)
                Spacer(Modifier.width(6.dp))
                Badge(formatVariance(project.variance), varianceColor, containerOf(varianceColor, colors))
                if (onClose != null) {
                    IconButton(onClick = onClose, modifier = Modifier.testTag("close_project_button")) {
                        Icon(Icons.Default.Close, contentDescription = "Close project", tint = scheme.onSurfaceVariant)
                    }
                }
            }
            AnimatedVisibility(visible = !collapsed) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            project.location,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ProgressLine(
                            label = "Work done",
                            value = "%.2f%%".format(project.actualAccomplishment),
                            fraction = (project.actualAccomplishment / 100).toFloat(),
                            color = varianceColor,
                            modifier = Modifier.weight(1f)
                        )
                        ProgressLine(
                            label = "Billed",
                            value = BillingProgress.label(billedTotal, contractCost),
                            fraction = billedFraction ?: 0f,
                            color = scheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
