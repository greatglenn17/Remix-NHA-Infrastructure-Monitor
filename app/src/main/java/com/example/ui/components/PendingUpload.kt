package com.example.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Uuids of records this phone saved that the cloud has not confirmed yet; empty when nothing leaves the phone. */
val LocalPendingUploads = compositionLocalOf { emptySet<String>() }

/** "Waiting to upload" on a record this phone saved and the cloud has not confirmed yet; nothing otherwise. */
@Composable
fun PendingUploadMark(uuid: String, modifier: Modifier = Modifier) {
    if (uuid !in LocalPendingUploads.current) return
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier.testTag("pending_upload_mark"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CloudUpload, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text("Waiting to upload", style = MaterialTheme.typography.labelSmall, color = color)
    }
}
