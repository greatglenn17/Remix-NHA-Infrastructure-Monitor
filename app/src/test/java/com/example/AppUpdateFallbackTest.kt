package com.example

import com.example.util.AppUpdateManager
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress

/** Phones read update.json from Hosting first and fall back to raw GitHub when Hosting fails or serves no JSON. */
@RunWith(RobolectricTestRunner::class)
class AppUpdateFallbackTest {

    @Test
    fun fallsBackWhenTheFirstUrlFails() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        fun serve(path: String, code: Int, body: String) = server.createContext(path) { ex ->
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        serve("/down", 503, "")
        serve("/html", 200, "<!doctype html><title>NHA Monitor</title>")
        serve("/ok", 200, """{"versionCode": 999999, "versionName": "9.9.9", "apkDownloadUrl": "https://example.test/a.apk"}""")
        server.start()
        try {
            val base = "http://127.0.0.1:${server.address.port}"
            val info = AppUpdateManager.firstUpdateInfo(listOf("$base/down", "$base/html", "$base/ok"))
            assertEquals(999999, info?.latestVersionCode)
            assertEquals(true, info?.isUpdateAvailable)
            assertNull(AppUpdateManager.firstUpdateInfo(listOf("$base/down", "$base/html")))
        } finally {
            server.stop(0)
        }
    }
}
