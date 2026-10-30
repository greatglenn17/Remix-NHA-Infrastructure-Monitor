package com.example.util

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Opens Android's print screen for a [BlueBookForms] page on A4 paper, where the user can
 * print it or choose "Save as PDF". [context] must be an Activity.
 */
object BlueBookPrint {
    // The print job reads from the WebView after this call returns, so it must outlive it.
    private var pending: WebView? = null

    fun print(context: Context, jobName: String, html: String, landscape: Boolean = false) {
        val webView = WebView(context)
        pending = webView
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                printManager.print(
                    jobName,
                    view.createPrintDocumentAdapter(jobName),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.let { if (landscape) it.asLandscape() else it }).build()
                )
            }
        }
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }
}
