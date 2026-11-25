package com.example

import java.time.Duration
import org.robolectric.shadows.ShadowSystemClock

/**
 * The application of every Robolectric test: Robolectric runs "Test" + the manifest's application name in its place.
 *
 * It starts Robolectric's clock at the real time. A scrolled lazy list prefetches only while its frame time is ahead of
 * System.nanoTime(), the real time since the computer started; Robolectric's clock starts at zero and gains one frame a
 * turn, so after 13 days without a restart each such test spun for 5 minutes before Compose was idle.
 */
class TestNhaApplication : NhaApplication() {
    override fun onCreate() {
        ShadowSystemClock.advanceBy(Duration.ofNanos(System.nanoTime()))
        super.onCreate()
    }
}
