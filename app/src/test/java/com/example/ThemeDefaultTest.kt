package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthManager
import com.example.data.model.AppThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ThemeDefaultTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun unsetTheme_defaultsToLight() {
        assertEquals(AppThemeMode.LIGHT, AuthManager(context).getSavedAppThemeMode())
    }

    @Test
    fun savedChoice_isKept() {
        AuthManager(context).saveAppThemeMode(AppThemeMode.SYSTEM)
        assertEquals(AppThemeMode.SYSTEM, AuthManager(context).getSavedAppThemeMode())
    }
}
