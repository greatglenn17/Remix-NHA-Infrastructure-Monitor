package com.example.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PdfFileWriter
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Opens Android's print screen for a [BlueBookForms] page on A4 paper, where the user can
 * print it or choose "Save as PDF"; or emails it as a PDF. [context] must be an Activity.
 */
object BlueBookPrint {
    // The print job reads from the WebView after this call returns, so it must outlive it.
    private var pending: WebView? = null

    // The form's own @page rule says which way the paper turns.
    private fun a4(html: String) =
        if (html.contains(BlueBookForms.LANDSCAPE_PAGE)) PrintAttributes.MediaSize.ISO_A4.asLandscape() else PrintAttributes.MediaSize.ISO_A4

    fun print(context: Context, jobName: String, html: String) = load(context, html) { view ->
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(jobName, view.createPrintDocumentAdapter(jobName), PrintAttributes.Builder().setMediaSize(a4(html)).build())
    }

    /** Makes the same A4 pages as [print] into a PDF and opens an email app with it attached. */
    fun email(context: Context, jobName: String, html: String) = pdf(context, jobName, html) { file ->
        if (file == null) {
            Toast.makeText(context, "Could not make the PDF. Try Print, then Save as PDF.", Toast.LENGTH_LONG).show()
            return@pdf
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_SUBJECT, jobName)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // An email app is one that takes both mailto: and a sent PDF (a mailto selector finds no apps on newer Android).
        val pm = context.packageManager
        fun screens(intent: Intent) = pm.queryIntentActivities(intent, 0).map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
        val emailApp = screens(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))).intersect(screens(send).toSet()).singleOrNull()
        try {
            context.startActivity(emailApp?.let { Intent(send).setComponent(it) } ?: Intent.createChooser(send, "Email $jobName"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app on this phone can send the PDF.", Toast.LENGTH_LONG).show()
        }
    }

    /** Writes [html] as A4 pages to a PDF in the cache; [onDone] gets the file, or null when it failed. Call on the main thread. */
    fun pdf(context: Context, jobName: String, html: String, onDone: (File?) -> Unit) = load(context, html) { view ->
        val dir = File(context.cacheDir, "pdf").apply { mkdirs() }
        val file = File(dir, pdfName(jobName))
        val attributes = PrintAttributes.Builder()
            .setMediaSize(a4(html))
            .setResolution(PrintAttributes.Resolution("pdf", "pdf", 600, 600))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        PdfFileWriter.write(view.createPrintDocumentAdapter(jobName), attributes, file) { ok -> onDone(file.takeIf { ok }) }
    }

    /** "Weekly Report - Oct 24, 2026 - Oct 30, 2026.pdf", without characters a file name can't hold. */
    fun pdfName(jobName: String): String =
        jobName.replace(Regex("[^A-Za-z0-9 .,()_-]"), "_").trim().take(120).ifBlank { "NHA Report" } + ".pdf"

    private fun load(context: Context, html: String, onLoaded: (WebView) -> Unit) {
        val webView = WebView(context)
        pending = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) = onLoaded(view)
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
