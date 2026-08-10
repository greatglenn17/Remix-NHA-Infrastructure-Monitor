package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController

@RunWith(AndroidJUnit4::class)
class AppCrashTest {

    @Test
    fun testAppLaunch() {
        val controller: ActivityController<MainActivity> = Robolectric.buildActivity(MainActivity::class.java)
        controller.setup()
        
        // Let background tasks execute
        org.robolectric.shadows.ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
    }
}
