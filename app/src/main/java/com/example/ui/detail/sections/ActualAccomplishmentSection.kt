package com.example.ui.detail.sections

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.ACCOMPLISHMENT_LINES
import com.example.data.model.ProjectAbstract
import com.example.ui.theme.figures
import com.example.util.AbstractExcel
import com.example.util.ProjectAbstracts
import com.example.util.ProjectSchedule
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun money(value: Double) = "%,.2f".format(Locale.US, value)

/** "Amount 400,000.00 · To date 100,000.00 (25.00%)", leaving out what the sheet left empty. */
private fun figuresLine(amount: Double?, toDate: Double?, pct: Double?): String = listOfNotNull(
    amount?.let { "Amount ${money(it)}" },
    when {
        toDate != null -> "To date ${money(toDate)}" + pct?.let { " (${ProjectAbstracts.pct(it)})" }.orEmpty()
        pct != null -> "To date ${ProjectAbstracts.pct(pct)}"
        else -> null
    }
).joinToString(" · ")

/**
 * Actual Accomplishment: the uploaded Abstracts of Physical Accomplishment of the actual work, each kept under its
 * "As of" date; the latest is the project's actual. Shows the chosen one's figures and every pay item, the parts
 * folding. [onSave] and [onDelete] are null for roles that cannot upload.
 */
@Composable
fun ActualAccomplishmentSection(
    projectId: Long,
    abstracts: List<ProjectAbstract>,
    uploadedBy: String,
    onSave: ((ProjectAbstract) -> Unit)?,
    onDelete: ((ProjectAbstract) -> Unit)?,
    onViewTable: (ProjectAbstract) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Newest first; of two filed under one date on two devices, the one changed last (ProjectAbstracts.latest).
    val dated = abstracts.sortedWith(compareByDescending<ProjectAbstract> { it.asOf }.thenByDescending { it.updatedAt }).distinctBy { it.asOf }
    var chosen by rememberSaveable(projectId) { mutableStateOf<String?>(null) }
    val shown = dated.firstOrNull { it.asOf == chosen } ?: dated.firstOrNull()
    var note by remember(projectId) { mutableStateOf("") }
    var noteIsError by remember(projectId) { mutableStateOf(false) }
    var replacing by remember { mutableStateOf<ProjectAbstract?>(null) }
    var deleting by remember { mutableStateOf<ProjectAbstract?>(null) }

    fun save(record: ProjectAbstract, replaced: Boolean) {
        onSave?.invoke(record)
        chosen = record.asOf
        noteIsError = false
        note = "${if (replaced) "Replaced" else "Saved"} the abstract as of ${ProjectAbstracts.summary(record)}."
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (read, fileName) = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { AbstractExcel.read(it) } } to runCatching {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                }.getOrNull().orEmpty()
            }
            val found = read.getOrNull()
            val record = found?.let { ProjectAbstracts.record(projectId, it, fileName, uploadedBy) }
            val error = when {
                read.isFailure -> "That file could not be read. Pick the abstract saved as an Excel workbook (.xlsx)."
                found == null -> "No Abstract of Physical Accomplishment was found in that file."
                found.billing -> "That is the billing abstract. Pick the abstract of the actual accomplishment."
                record == null -> "That abstract has no \"As of\" date, so it cannot be filed. Check the line under its title."
                else -> null
            }
            when {
                error != null || record == null -> { noteIsError = true; note = error.orEmpty() }
                dated.any { it.asOf == record.asOf } -> replacing = record
                else -> save(record, replaced = false)
            }
        }
    }
    replacing?.let { record ->
        AlertDialog(
            onDismissRequest = { replacing = null },
            title = { Text("Replace the abstract as of ${ProjectSchedule.displayDate(record.asOf)}?") },
            text = { Text("An abstract with that date is already uploaded. The file you picked (overall ${ProjectAbstracts.pct(record.overallPct)}) takes its place.") },
            confirmButton = { TextButton(onClick = { replacing = null; save(record, replaced = true) }, modifier = Modifier.testTag("confirm_replace_abstract")) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { replacing = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete the abstract as of ${ProjectSchedule.displayDate(record.asOf)}?") },
            text = { Text("The project's actual accomplishment goes back to the latest abstract left; with none left, to the latest weekly report.") },
            confirmButton = {
                TextButton(
                    onClick = { deleting = null; chosen = null; note = ""; onDelete?.invoke(record) },
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                    modifier = Modifier.testTag("confirm_delete_abstract")
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }

    val rows = remember(shown?.rowsJson) { shown?.let { ProjectAbstracts.rows(it.rowsJson) }.orEmpty() }
    val parts = remember(rows) { AbstractExcel.parts(rows) }
    // The parts folded shut, by their place among the parts.
    val folded = remember(shown?.uuid) { mutableStateListOf<Int>() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize().testTag("actual_accomplishment")
    ) {
        if (shown != null) item {
            val labels = dated.map { ProjectSchedule.displayDate(it.asOf) }
            Column {
                Text("As of", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
                ChipRow(labels, ProjectSchedule.displayDate(shown.asOf), PaddingValues(0.dp)) { label -> chosen = dated[labels.indexOf(label)].asOf; note = "" }
            }
        }
        if (onSave != null || (onDelete != null && shown != null)) item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onSave != null) OutlinedButton(onClick = { picker.launch(arrayOf(XLSX)) }, modifier = Modifier.testTag("upload_abstract")) {
                    Text("Upload abstract (Excel)")
                }
                if (onDelete != null && shown != null) TextButton(
                    onClick = { deleting = shown },
                    colors = ButtonDefaults.textButtonColors(contentColor = scheme.error),
                    modifier = Modifier.testTag("delete_abstract")
                ) { Text("Delete") }
            }
        }
        if (note.isNotBlank()) item {
            Text(note, style = MaterialTheme.typography.bodySmall, color = if (noteIsError) scheme.error else scheme.onSurfaceVariant, modifier = Modifier.testTag("abstract_note"))
        }
        if (shown == null) {
            item {
                EmptyLine("No abstract uploaded yet. Upload the Abstract of Physical Accomplishment of the actual work (the \"As of\" one), saved as an Excel workbook (.xlsx).")
            }
            return@LazyColumn
        }
        item {
            SectionCard("Abstract of Physical Accomplishment", Icons.Default.TableChart) {
                Field("As of", ProjectSchedule.displayDate(shown.asOf))
                Field("Overall", ProjectAbstracts.pct(shown.overallPct))
                ACCOMPLISHMENT_LINES.zip(ProjectAbstracts.lineActuals(shown)).forEach { (line, actual) -> Field(line, ProjectAbstracts.pct(actual)) }
                Field("File", shown.fileName.ifBlank { "-" })
                Field("Uploaded by", shown.uploadedBy.ifBlank { "-" })
                OutlinedButton(onClick = { onViewTable(shown) }, modifier = Modifier.padding(top = 8.dp).testTag("view_abstract_table")) { Text("View full table") }
            }
        }
        var part = -1
        rows.forEachIndexed { i, row ->
            if (row.kind == "part") part++
            val index = part
            when {
                row.kind == "part" -> item(key = i) {
                    val open = index !in folded
                    Surface(
                        color = scheme.surfaceVariant, shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().clickable { if (open) folded += index else folded -= index }.testTag("abstract_part_$index")
                    ) {
                        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${row.no}  ${row.name}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurface)
                                val line = figuresLine(parts[index].amount, null, parts[index].actualPct)
                                Text(line, style = MaterialTheme.typography.bodySmall.figures(line), color = scheme.onSurfaceVariant)
                            }
                            Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "Hide its items" else "Show its items", tint = scheme.onSurfaceVariant)
                        }
                    }
                }
                // The grand total stays in sight whatever is folded.
                row.kind != "total" && index in folded -> Unit
                row.kind == "group" -> item(key = i) {
                    Text(
                        "${row.no}  ${row.name}".trim(), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                else -> item(key = i) {
                    val bold = if (row.kind == "item") null else FontWeight.SemiBold
                    Column(Modifier.fillMaxWidth()) {
                        Text("${row.no}  ${row.name}".trim(), style = MaterialTheme.typography.bodyMedium, fontWeight = bold, color = scheme.onSurface)
                        val line = figuresLine(row.amount, row.toDate.getOrNull(1), row.toDate.getOrNull(2))
                        if (line.isNotEmpty()) Text(line, style = MaterialTheme.typography.bodySmall.figures(line), fontWeight = bold, color = scheme.onSurfaceVariant)
                        if (row.kind != "item") HorizontalDivider(color = scheme.outlineVariant, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
}
