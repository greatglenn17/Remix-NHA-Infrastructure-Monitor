package com.example.data.files

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okio.Path.Companion.toOkioPath
import java.io.IOException

/** Lets every image view show `nhafile:` attachments, downloading them when needed. */
class AttachmentFetcher(private val ref: String, private val options: Options) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val file = Attachments.ensureLocal(options.context, ref)
            ?: throw IOException("Attachment is not uploaded yet")
        return SourceResult(
            source = ImageSource(file.toOkioPath()),
            mimeType = Attachments.contentTypeOf(file.name),
            dataSource = DataSource.DISK
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == Attachments.SCHEME) AttachmentFetcher(data.toString(), options) else null
    }
}
