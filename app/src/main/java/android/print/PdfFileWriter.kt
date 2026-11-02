package android.print

import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Runs a WebView's print adapter straight into a PDF file, with the same pages the print screen
 * would make. It lives in android.print because the layout and write callbacks can only be made here.
 */
object PdfFileWriter {
    fun write(adapter: PrintDocumentAdapter, attributes: PrintAttributes, file: File, onDone: (Boolean) -> Unit) {
        adapter.onStart()
        adapter.onLayout(null, attributes, null, object : PrintDocumentAdapter.LayoutResultCallback() {
            override fun onLayoutFinished(info: PrintDocumentInfo, changed: Boolean) {
                val out = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE or ParcelFileDescriptor.MODE_READ_WRITE)
                adapter.onWrite(arrayOf(PageRange.ALL_PAGES), out, CancellationSignal(), object : PrintDocumentAdapter.WriteResultCallback() {
                    override fun onWriteFinished(pages: Array<out PageRange>) = finish(true)
                    override fun onWriteFailed(error: CharSequence?) = finish(false)
                    override fun onWriteCancelled() = finish(false)
                    private fun finish(ok: Boolean) {
                        out.close()
                        adapter.onFinish()
                        onDone(ok)
                    }
                })
            }

            override fun onLayoutFailed(error: CharSequence?) {
                adapter.onFinish()
                onDone(false)
            }
        }, null)
    }
}
