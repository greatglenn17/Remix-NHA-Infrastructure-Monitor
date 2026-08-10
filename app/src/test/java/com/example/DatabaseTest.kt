package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PrepopulateData
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    @Test
    fun testPrepopulate() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val db = AppDatabase.getDatabase(context, CoroutineScope(Dispatchers.IO))
        
        // Force database creation
        db.projectDao().getAllProjects()
        
        PrepopulateData.populateDatabase(db.projectDao(), db.reportDao())
    }
}
