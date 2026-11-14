package com.example.ui.detail

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The project screen's sections (spec "Project detail (v1.8.0)"); Actual is the uploaded abstracts of the actual work. */
enum class ProjectSection(val label: String) {
    SUMMARY("Summary"),
    PROGRESS("Progress"),
    ACTUAL("Actual"),
    MONEY("Payments"),
    DOCUMENTS("Documents")
}

/** The sections as fixed tabs; no sideways scrolling. */
@Composable
fun ProjectSectionTabs(section: ProjectSection, onSelect: (ProjectSection) -> Unit) {
    BoxWithConstraints {
        // Five tabs on a 360dp phone are 72dp each: the label is the tab's whole content (no 16dp side padding) and 13sp,
        // or "Documents" would be cut off. With the phone's font set larger the labels shrink together to still fit.
        val base = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp)
        val measurer = rememberTextMeasurer()
        val widest = ProjectSection.entries.maxOf { measurer.measure(it.label, base, softWrap = false).size.width }
        val tab = constraints.maxWidth / ProjectSection.entries.size
        val style = if (widest > tab) base.copy(fontSize = base.fontSize * (tab * 0.96f / widest)) else base
        TabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
            ProjectSection.entries.forEach { s ->
                Tab(selected = s == section, onClick = { onSelect(s) }, modifier = Modifier.height(48.dp).testTag("detail_tab_${s.ordinal}")) {
                    Text(s.label, style = style, maxLines = 1, softWrap = false)
                }
            }
        }
    }
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
