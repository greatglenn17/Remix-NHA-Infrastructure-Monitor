package com.example.ui.detail

/** The project screen's four sections (spec "Project detail (v1.8.0)"). */
enum class ProjectSection(val label: String) {
    SUMMARY("Summary"),
    PROGRESS("Progress"),
    MONEY("Payments"),
    DOCUMENTS("Documents")
}

/** Chips that switch views inside the Progress and Documents sections. */
object SectionChips {
    // Lots follows today's Subdivision Plan rule (Housing projects), so the first plan can be uploaded there.
    fun progress(isHousing: Boolean): List<String> =
        listOfNotNull("Weekly", "Monthly", "Lots".takeIf { isHousing }, "Photos", "Weather", "Issues")

    const val SUSPENSIONS = "Suspension / Resumption"
    const val TIME_EXTENSIONS = "Time Extensions"
    const val VARIATION_ORDERS = "Variation Orders"
    const val LETTERS = "Letters to Contractor"
    val ORDER_CHIPS = listOf(SUSPENSIONS, TIME_EXTENSIONS, VARIATION_ORDERS, LETTERS)

    fun documents(canViewAuditLogs: Boolean): List<String> =
        ORDER_CHIPS + listOfNotNull("Related Files", "Audit log".takeIf { canViewAuditLogs })

    fun chipOrFirst(chips: List<String>, chip: String?): String = chip?.takeIf { it in chips } ?: chips.first()
}
