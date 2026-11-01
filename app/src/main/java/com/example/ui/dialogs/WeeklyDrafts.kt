package com.example.ui.dialogs

import android.content.Context
import com.example.data.model.WeeklyReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** A new weekly report not saved yet: the form's values plus the week's hourly weather. */
data class WeeklyDraft(val savedAt: Long, val report: WeeklyReport, val weather: Map<String, List<String>>)

/**
 * One draft per project, kept on this phone only (never synced, never a report record),
 * so a half-filled weekly report survives the app closing. Saving or discarding the report removes it.
 */
object WeeklyDrafts {
    private fun prefs(context: Context) = context.getSharedPreferences("weekly_drafts", Context.MODE_PRIVATE)
    private fun key(projectId: Long) = "project_$projectId"

    fun save(context: Context, projectId: Long, draft: WeeklyDraft) {
        val r = draft.report
        val json = JSONObject()
            .put("savedAt", draft.savedAt)
            .put("reportingWeek", r.reportingWeek)
            .put("target", r.targetAccomplishmentPct)
            .put("actual", r.actualAccomplishmentPct)
            .put("manpower", r.manpowerJson)
            .put("equipment", r.equipmentJson)
            .put("activities", r.activitiesJson)
            .put("issues", r.issuesJson)
            .put("accomplishments", r.accomplishmentItemsJson)
            .put("photos", r.attachedPhotoUrlsJson)
            .put("photoDetails", r.photoDetailsJson)
            .put("weather", JSONObject(draft.weather.mapValues { JSONArray(it.value) }))
        prefs(context).edit().putString(key(projectId), json.toString()).apply()
    }

    /** The project's draft, or null when there is none or it can't be read. */
    fun load(context: Context, projectId: Long): WeeklyDraft? = runCatching {
        val json = JSONObject(prefs(context).getString(key(projectId), null) ?: return null)
        val weather = json.getJSONObject("weather").let { w ->
            w.keys().asSequence().associateWith { day -> w.getJSONArray(day).let { a -> List(a.length()) { a.getString(it) } } }
        }
        WeeklyDraft(
            savedAt = json.getLong("savedAt"),
            report = WeeklyReport(
                projectId = projectId, reportingWeek = json.getString("reportingWeek"), daysElapsed = 0, remainingDays = 0,
                targetAccomplishmentPct = json.getDouble("target"), actualAccomplishmentPct = json.getDouble("actual"),
                manpowerJson = json.getString("manpower"), equipmentJson = json.getString("equipment"),
                activitiesJson = json.getString("activities"), issuesJson = json.getString("issues"),
                accomplishmentItemsJson = json.getString("accomplishments"), documentsIssuedReceivedJson = "[]",
                attachedPhotoUrlsJson = json.getString("photos"), photoDetailsJson = json.getString("photoDetails"),
                submittedByStaff = ""
            ),
            weather = weather
        )
    }.getOrNull()

    fun clear(context: Context, projectId: Long) {
        prefs(context).edit().remove(key(projectId)).apply()
    }

    fun savedAtText(at: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.US).format(Instant.ofEpochMilli(at).atZone(zone))
}
