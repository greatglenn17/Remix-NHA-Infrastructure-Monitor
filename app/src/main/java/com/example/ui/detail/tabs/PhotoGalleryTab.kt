package com.example.ui.detail.tabs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.model.WeeklyReport
import com.example.ui.theme.*

data class PhotoItem(
    val url: String,
    val weekDate: String,
    val submittedBy: String,
    val detail: com.example.data.files.PhotoDetail = com.example.data.files.PhotoDetail()
)

@Composable
fun PhotoGalleryTab(weeklyReports: List<WeeklyReport>) {
    var selectedPhoto by remember { mutableStateOf<PhotoItem?>(null) }

    // One album per reporting week, in report order.
    val albums = remember(weeklyReports) {
        weeklyReports.map { report ->
            val details = com.example.data.files.PhotoDetails.parse(report.photoDetailsJson)
            report.reportingWeek to com.example.data.files.Attachments.parseList(report.attachedPhotoUrlsJson).map { url ->
                PhotoItem(
                    url = url,
                    weekDate = report.reportingWeek,
                    submittedBy = report.submittedByStaff,
                    detail = details[url] ?: com.example.data.files.PhotoDetail()
                )
            }
        }.filter { it.second.isNotEmpty() }
    }
    val photos = albums.flatMap { it.second }
    val context = androidx.compose.ui.platform.LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        if (photos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Collections,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Site Update Photos Uploaded Yet",
                        style = MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Update photos attached to weekly reports will appear here chronologically.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }
        } else {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "PROJECT UPDATE PHOTO GALLERY (${photos.size} Photos)",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    albums.forEach { (week, weekPhotos) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "$week  ·  ${weekPhotos.size} photo${if (weekPhotos.size == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(weekPhotos) { photo ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clickable { selectedPhoto = photo }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = photo.url,
                                    contentDescription = "Project Photo",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                val label = photo.detail.title.ifBlank { photo.detail.takenAt }
                                if (label.isNotBlank()) Surface(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(6.dp)) {
                                        Text(
                                            text = label,
                                            maxLines = 2,
                                            style = MaterialTheme.typography.labelSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }
            }
        }

        // Full Screen Image Modal Dialog
        selectedPhoto?.let { photo ->
            Dialog(onDismissRequest = { selectedPhoto = null }) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = photo.weekDate,
                                style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { selectedPhoto = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        AsyncImage(
                            model = photo.url,
                            contentDescription = "Enlarged Photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val d = photo.detail
                        if (d.title.isNotBlank()) Text(d.title, style = MaterialTheme.typography.titleSmall.copy(color = Color.White, fontWeight = FontWeight.Bold))
                        if (d.takenAt.isNotBlank()) Text("Taken: ${d.takenAt}", style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray))
                        if (d.location.isNotBlank()) {
                            Text("Location: ${d.location}", style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray))
                            TextButton(onClick = {
                                val uri = android.net.Uri.parse("geo:${d.lat},${d.lng}?q=${d.lat},${d.lng}")
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
                            }) { Text("Open in Maps") }
                        }
                        Text(
                            text = "Submitted by: ${photo.submittedBy}",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.LightGray)
                        )
                    }
                }
            }
        }
    }
}
