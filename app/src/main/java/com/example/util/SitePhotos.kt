package com.example.util

import com.example.data.files.Attachments
import com.example.data.files.PhotoDetails
import com.example.data.model.WeeklyReport

object SitePhotos {
    /** First photo of the newest weekly report that has one, or "" when none do. */
    fun latest(reports: List<WeeklyReport>): String = reports.sortedByDescending { it.createdAtTimestamp }
        .firstNotNullOfOrNull { Attachments.parseList(it.attachedPhotoUrlsJson).firstOrNull() }
        .orEmpty()

    /** The report with [url] taken out of its photos and their captions. */
    fun withoutPhoto(report: WeeklyReport, url: String): WeeklyReport = report.copy(
        attachedPhotoUrlsJson = Attachments.toJson(Attachments.parseList(report.attachedPhotoUrlsJson) - url),
        photoDetailsJson = PhotoDetails.toJson(PhotoDetails.parse(report.photoDetailsJson) - url)
    )
}
