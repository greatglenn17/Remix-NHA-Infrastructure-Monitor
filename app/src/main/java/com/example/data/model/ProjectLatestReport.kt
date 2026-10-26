package com.example.data.model

/** Newest weekly report time per project (query result, not a table). */
data class ProjectLatestReport(val projectId: Long, val latestAt: Long)
