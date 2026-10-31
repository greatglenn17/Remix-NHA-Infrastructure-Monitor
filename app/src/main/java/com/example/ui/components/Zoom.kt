package com.example.ui.components

import android.webkit.WebView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Pinch to zoom (up to 5x) and drag to move a zoomed photo; double-tap zooms in or back out. */
fun Modifier.zoomable(): Modifier = composed {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    clipToBounds()
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 5f)
                // shortcut: panning is not clamped to the photo's edges; double-tap brings it back.
                offset = if (scale == 1f) Offset.Zero else offset + pan * scale
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero })
        }
        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
}

/**
 * A Blue Book form shown before printing, A4 pages on a grey desk. Pinch or the +/− buttons zoom;
 * Print opens Android's print screen as before.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrintPreview(title: String, html: String, onPrint: () -> Unit, onDismiss: () -> Unit) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Close preview") } },
                    actions = {
                        IconButton(onClick = { webView?.zoomOut() }, modifier = Modifier.testTag("preview_zoom_out")) { Icon(Icons.Default.ZoomOut, contentDescription = "Zoom out") }
                        IconButton(onClick = { webView?.zoomIn() }, modifier = Modifier.testTag("preview_zoom_in")) { Icon(Icons.Default.ZoomIn, contentDescription = "Zoom in") }
                        Button(onClick = onPrint, modifier = Modifier.padding(end = 8.dp).testTag("preview_print_button")) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Print")
                        }
                    }
                )
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = true
                            loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
