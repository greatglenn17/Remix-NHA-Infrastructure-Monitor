package com.example.ui.detail.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.files.Attachments
import com.example.data.model.ContractorLetter
import com.example.ui.components.DateField
import com.example.ui.components.rememberAttachmentOpener
import com.example.ui.components.rememberAttachmentPicker
import com.example.ui.components.rememberDiscardGuard
import com.example.ui.components.rememberDocumentScanner
import com.example.ui.theme.LocalNHAColors
import com.example.util.ProjectSchedule
import java.time.LocalDate

/** Letters NHA sent the contractor as a table: date, subject, the contractor's action, and their reply letter when there is one. */
@Composable
fun ContractorLettersCard(
    letters: List<ContractorLetter>,
    onAdd: (() -> Unit)?,
    onOpen: ((ContractorLetter) -> Unit)?,
    modifier: Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val openFile = rememberAttachmentOpener()
    SectionCard(
        "Letters to contractor",
        Icons.Default.Mail,
        modifier.testTag("summary_contractor_letters"),
        action = onAdd?.let { add ->
            {
                TextButton(onClick = add, modifier = Modifier.testTag("add_contractor_letter_button")) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add letter")
                }
            }
        }
    ) {
        if (letters.isEmpty()) {
            EmptyLine("No letters to the contractor yet.")
            return@SectionCard
        }
        val weights = listOf(1f, 1.4f, 1.6f)
        @Composable
        fun RowScope.Cell(text: String, i: Int, bold: Boolean = false, color: androidx.compose.ui.graphics.Color = scheme.onSurface) =
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = if (bold) FontWeight.SemiBold else null, modifier = Modifier.weight(weights[i]))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Date", "Subject", "Action taken by contractor").forEachIndexed { i, h -> Cell(h, i, bold = true, color = scheme.onSurfaceVariant) }
            Text("Reply", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant, modifier = Modifier.width(48.dp))
        }
        HorizontalDivider(color = scheme.outlineVariant)
        letters.forEach { letter ->
            Row(
                Modifier.fillMaxWidth().then(if (onOpen != null) Modifier.clickable { onOpen(letter) } else Modifier)
                    .padding(vertical = 6.dp).testTag("contractor_letter_${letter.id}"),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Cell(ProjectSchedule.displayDate(letter.dateSent.ifBlank { "-" }), 0)
                Cell(letter.subject.ifBlank { "-" }, 1)
                // No action yet stands out until the contractor does something.
                if (letter.actionTaken.isBlank()) Cell("None yet", 2, color = LocalNHAColors.current.watch) else Cell(letter.actionTaken, 2)
                Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                    when {
                        letter.replyFileUrl.isNotBlank() -> IconButton(onClick = { openFile(letter.replyFileUrl) }, modifier = Modifier.testTag("contractor_reply_${letter.id}")) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Open the contractor's reply", tint = scheme.primary)
                        }
                        letter.dateReplied.isNotBlank() -> Text("Replied", style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
                        else -> Text("-", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
                    }
                }
            }
            HorizontalDivider(color = scheme.outlineVariant)
        }
    }
}

/**
 * A letter attached to a record ([what], e.g. "reply"): Scan / Upload buttons, or once attached, View / Remove.
 * [onChange] gets the new attachment reference, or blank when removed.
 */
@Composable
fun LetterFileField(what: String, file: String, tag: String, onChange: (String) -> Unit) {
    val scan = rememberDocumentScanner(onChange)
    val upload = rememberAttachmentPicker(Attachments.Kind.SCAN, onAttached = onChange)
    val openFile = rememberAttachmentOpener()
    if (file.isNotBlank()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AttachFile, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("${what.replaceFirstChar { it.uppercase() }} letter attached", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { openFile(file) }) { Text("View") }
            TextButton(onClick = { onChange("") }, modifier = Modifier.testTag("${tag}_remove")) { Text("Remove") }
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = scan, modifier = Modifier.weight(1f).testTag("${tag}_scan")) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Scan $what")
            }
            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = { upload.launch("*/*") }, modifier = Modifier.weight(1f).testTag("${tag}_upload")) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Upload $what")
            }
        }
    }
}

/** Adds or edits a letter to the contractor; [existing] null adds one. The contractor's reply letter can be scanned or uploaded. */
@Composable
fun ContractorLetterDialog(
    projectId: Long,
    existing: ContractorLetter?,
    onDismiss: () -> Unit,
    onSave: (ContractorLetter) -> Unit,
    onDelete: ((ContractorLetter) -> Unit)?,
    today: LocalDate = LocalDate.now()
) {
    var dateSent by remember { mutableStateOf(existing?.dateSent ?: today.toString()) }
    var subject by remember { mutableStateOf(existing?.subject ?: "") }
    var action by remember { mutableStateOf(existing?.actionTaken ?: "") }
    var dateReplied by remember { mutableStateOf(existing?.dateReplied ?: "") }
    var replyFile by remember { mutableStateOf(existing?.replyFileUrl ?: "") }
    val snapshot = listOf(dateSent, subject, action, dateReplied, replyFile)
    val initial = remember { snapshot }
    val close = rememberDiscardGuard(snapshot != initial, onDismiss)
    AlertDialog(
        onDismissRequest = close,
        title = { Text(if (existing == null) "New letter to contractor" else "Letter to contractor") },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(label = "Date of letter", value = dateSent, onValueChange = { dateSent = it })
                OutlinedTextField(
                    value = subject, onValueChange = { subject = it }, label = { Text("Subject") },
                    modifier = Modifier.fillMaxWidth().testTag("contractor_letter_subject")
                )
                OutlinedTextField(
                    value = action, onValueChange = { action = it }, label = { Text("Action taken by contractor") }, minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("contractor_letter_action")
                )
                Text("Contractor's reply", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                DateField(label = "Date of reply (blank: no reply yet)", value = dateReplied, onValueChange = { dateReplied = it }, clearable = true)
                LetterFileField("reply", replyFile, "contractor_reply") { ref ->
                    replyFile = ref
                    // A reply letter in hand means the contractor replied.
                    if (ref.isNotBlank() && dateReplied.isBlank()) dateReplied = today.toString()
                }
                if (existing != null && onDelete != null) {
                    TextButton(onClick = { onDelete(existing) }) { Text("Delete letter", color = LocalNHAColors.current.behind) }
                }
            }
        },
        confirmButton = {
            Button(shape = MaterialTheme.shapes.small, 
                enabled = dateSent.isNotBlank() && subject.isNotBlank(),
                onClick = {
                    val base = existing ?: ContractorLetter(projectId = projectId, dateSent = dateSent, subject = subject)
                    onSave(base.copy(dateSent = dateSent, subject = subject.trim(), actionTaken = action.trim(), dateReplied = dateReplied, replyFileUrl = replyFile))
                },
                modifier = Modifier.testTag("save_contractor_letter_button")
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = close) { Text("Cancel") } }
    )
}
