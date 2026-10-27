package com.example.util

import com.example.data.files.Attachments
import com.example.data.model.WeeklyReport

object SitePhotos {
    /** First photo of the newest weekly report that has one, or "" when none do. */
    fun latest(reports: List<WeeklyReport>): String = reports.sortedByDescending { it.createdAtTimestamp }
        .firstNotNullOfOrNull { Attachments.parseList(it.attachedPhotoUrlsJson).firstOrNull() }
        .orEmpty()
}
