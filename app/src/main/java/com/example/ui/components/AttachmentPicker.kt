package com.example.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.example.data.files.Attachments
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

/** The uuid of the project on screen; attachments are stored under it. Blank outside a project. */
val LocalProjectUuid = compositionLocalOf { "" }

// Opens the file picker; launch takes the mime filter passed to GetContent, e.g. "image/*".
class AttachmentPicker(val launch: (mimeType: String) -> Unit)

/** A file picker whose pick is copied into the app (see [Attachments]) and handed back as its reference. */
@Composable
fun rememberAttachmentPicker(kind: Attachments.Kind, stamp: List<String> = emptyList(), onAttached: (ref: String) -> Unit): AttachmentPicker {
    val context = LocalContext.current
    val projectUuid = LocalProjectUuid.current
    val scope = rememberCoroutineScope()
    val latestOnAttached by rememberUpdatedState(onAttached)
    val latestStamp by rememberUpdatedState(stamp)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                latestOnAttached(Attachments.import(context, uri, kind, projectUuid, latestStamp))
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

/** Takes a photo with the camera app and attaches it, e.g. a DV shot on site. Launch with no arguments. */
@Composable
fun rememberAttachmentCamera(kind: Attachments.Kind, stamp: List<String> = emptyList(), onAttached: (ref: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val projectUuid = LocalProjectUuid.current
    val scope = rememberCoroutineScope()
    val latestOnAttached by rememberUpdatedState(onAttached)
    val latestStamp by rememberUpdatedState(stamp)
    var pending by remember { mutableStateOf<java.io.File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pending ?: return@rememberLauncherForActivityResult
        pending = null
        if (!saved) { file.delete(); return@rememberLauncherForActivityResult }
        scope.launch {
            try {
                latestOnAttached(Attachments.importCameraPhoto(context, file, projectUuid, kind, latestStamp))
            } catch (e: Exception) {
                Toast.makeText(context, "Photo not attached: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    return {
        val (file, uri) = Attachments.newCameraFile(context)
        pending = file
        launcher.launch(uri)
    }
}

/**
 * Google's document scanner (Play services): finds the page edges, straightens and cleans the
 * image, then attaches it as a scan. One page per scan. Launch with no arguments.
 */
@Composable
fun rememberDocumentScanner(onAttached: (ref: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val projectUuid = LocalProjectUuid.current
    val scope = rememberCoroutineScope()
    val latestOnAttached by rememberUpdatedState(onAttached)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode != android.app.Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val page = GmsDocumentScanningResult.fromActivityResultIntent(result.data)?.pages?.firstOrNull()
            ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                latestOnAttached(Attachments.import(context, page.imageUri, Attachments.Kind.SCAN, projectUuid))
            } catch (e: Exception) {
                Toast.makeText(context, "Scan not attached: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    return {
        val activity = generateSequence(context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        if (activity == null) {
            Toast.makeText(context, "The scanner could not open here.", Toast.LENGTH_LONG).show()
        } else {
            GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
                .addOnSuccessListener { sender -> launcher.launch(IntentSenderRequest.Builder(sender).build()) }
                .addOnFailureListener { e ->
                    // Needs Google Play services; older or Play-less phones can still take a photo instead.
                    Toast.makeText(context, "Scanner unavailable: ${e.message}. Use Take photo instead.", Toast.LENGTH_LONG).show()
                }
        }
    }
}
