package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.example.data.files.AttachmentFetcher

// Open for the unit tests' TestNhaApplication.
open class NhaApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components { add(AttachmentFetcher.Factory()) }
        .build()
}
