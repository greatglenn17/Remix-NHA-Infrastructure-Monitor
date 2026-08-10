package com.example.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Project
import com.example.data.model.ProjectStatus
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

private data class TileThemeColors(
    val cardBg: Color,
    val borderColor: Color,
    val textColor: Color,
    val barFill: Color,
    val barBg: Color
)

@Composable
fun ProjectTileCard(
    project: Project,
    pendingDocsCount: Int,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onEditClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeColors = when (project.status) {
        ProjectStatus.SUSPENDED.label -> TileThemeColors(StatusGrayBg, StatusGrayBorder, StatusGrayText, StatusGrayBarFill, StatusGrayBarBg)
        ProjectStatus.BEHIND_SCHEDULE.label -> TileThemeColors(StatusOrangeBg, StatusOrangeBorder, StatusOrangeText, StatusOrangeBarFill, StatusOrangeBarBg)
        ProjectStatus.ONGOING.label -> {
            if (project.variance < 0) {
                TileThemeColors(StatusRedBg, StatusRedBorder, StatusRedText, StatusRedBarFill, StatusRedBarBg)
            } else {
                TileThemeColors(StatusGreenBg, StatusGreenBorder, StatusGreenText, StatusGreenBarFill, StatusGreenBarBg)
            }
        }
        else -> TileThemeColors(StatusGrayBg, StatusGrayBorder, StatusGrayText, StatusGrayBarFill, StatusGrayBarBg)
    }
    val cardBg = themeColors.cardBg
    val borderColor = themeColors.borderColor
    val textColor = themeColors.textColor
    val barFill = themeColors.barFill
    val barBg = themeColors.barBg

    val phpFormatString = com.example.utils.CurrencyFormatter.formatPhp(project.contractCostRevised)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clickable { onClick() }
            .testTag("project_tile_${project.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(2.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header Row: Status Badge & Pending Docs Badge
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Status Pill
                Surface(
                    color = borderColor,
                    shape = RoundedCornerShape(50.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(textColor, shape = RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = project.status.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textColor,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        )
                    }
                }

                // Type & Pending Docs Pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = GeoNeutralBg,
                        shape = RoundedCornerShape(50.dp)
                    ) {
                        Text(
                            text = project.projectType,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = GeoTextPrimary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }

                    if (pendingDocsCount > 0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = StatusOrangeBarFill,
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$pendingDocsCount Docs",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            }


            // Body: Thumbnail Image & Title / Location
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (project.latestUpdatePhotoUrl.isNotBlank()) {
                    AsyncImage(
                        model = project.latestUpdatePhotoUrl,
                        contentDescription = "Project Photo",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GeoNeutralBg),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(GeoNeutralBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apartment,
                            contentDescription = null,
                            tint = GeoTextSecondary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = project.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = GeoTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = project.location,
                            style = MaterialTheme.typography.bodySmall.copy(color = GeoTextSecondary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (project.assignedStaff.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = GeoTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Manager: ${project.assignedStaff}",
                                style = MaterialTheme.typography.bodySmall.copy(color = GeoTextSecondary, fontSize = 11.sp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Text(
                        text = "Cost: $phpFormatString",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = textColor.copy(alpha = 0.85f),
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }


            // Progress Bar & Variance Row
            val progress = (project.actualAccomplishment / 100.0).coerceIn(0.0, 1.0).toFloat()
            Column(modifier = Modifier.fillMaxWidth()) {
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Target: ${"%.1f".format(project.targetAccomplishment)}%  |  Actual: ${"%.1f".format(project.actualAccomplishment)}%",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        ),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    
                    // Variance Badge (+ or -)
                    val varianceStr = if (project.variance >= 0) "+%.1f%%".format(project.variance) else "%.1f%%".format(project.variance)
                    Surface(
                        color = barFill,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    ) {
                        Text(
                            text = varianceStr,
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            ),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(50.dp)),
                    color = barFill,
                    trackColor = barBg
                )
            }


            // Footer Row: Completion Date & Inspector
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Column(modifier = Modifier.align(Alignment.CenterVertically)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = GeoTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Completion: ${project.completionDateRevised}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GeoTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Update,
                            contentDescription = null,
                            tint = GeoTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Last Update: ${project.lastUpdatedDate}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = GeoTextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
                Column(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "Staff: ${project.assignedStaff}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = GeoTextSecondary,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                    Row {
                        if (onEditClick != null) {
                            IconButton(onClick = onEditClick, modifier = Modifier.size(44.dp).testTag("edit_project_button_${project.id}")) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Project",
                                    tint = GeoTextSecondary
                                )
                            }
                        }
                        if (onDeleteClick != null) {
                            IconButton(onClick = onDeleteClick, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Project",
                                    tint = StatusRedText
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
