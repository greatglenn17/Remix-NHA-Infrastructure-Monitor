package com.example.data.model

import org.json.JSONArray

/** Reads and writes [Project.assignedUids], a JSON array of Firebase account uids. */
object AssignedUids {

    /** Returns the uids in [json]; empty or unreadable input gives an empty list. */
    fun parse(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { array.optString(it, "").takeIf { uid -> uid.isNotEmpty() } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Writes [uids] as a JSON array, dropping duplicates and keeping order. */
    fun toJson(uids: List<String>): String {
        val array = JSONArray()
        uids.distinct().forEach { array.put(it) }
        return array.toString()
    }

    fun contains(json: String, uid: String): Boolean = uid in parse(json)
}
