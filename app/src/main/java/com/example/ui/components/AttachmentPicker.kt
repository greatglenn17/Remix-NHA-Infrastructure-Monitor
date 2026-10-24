package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.data.files.Attachments
import kotlinx.coroutines.launch

/** The uuid of the project on screen; attachments are stored under it. Blank outside a project. */
val LocalProjectUuid = compositionLocalOf { "" }

// Opens the file picker; launch takes the mime filter passed to GetContent, e.g. "image/*".
class AttachmentPicker(val launch: (mimeType: String) -> Unit)

/** A file picker whose pick is copied into the app (see [Attachments]) and handed back as its reference. */
@Composable
fun rememberAttachmentPicker(kind: Attachments.Kind, onAttached: (ref: String) -> Unit): AttachmentPicker {
    val context = LocalContext.current
    val projectUuid = LocalProjectUuid.current
    val scope = rememberCoroutineScope()
    val latestOnAttached by rememberUpdatedState(onAttached)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                latestOnAttached(Attachments.import(context, uri, kind, projectUuid))
            } catch (e: Exception) {
                Toast.makeText(context, "File not attached: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    return AttachmentPicker { mimeType -> launcher.launch(mimeType) }
}

/** Opens an attachment in a viewer app, downloading it first when another phone added it. */
@Composable
fun rememberAttachmentOpener(): (value: String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return { value ->
        scope.launch {
            if (!Attachments.open(context, value)) {
                Toast.makeText(context, "This file isn't available yet. It may still be uploading from the other phone.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
