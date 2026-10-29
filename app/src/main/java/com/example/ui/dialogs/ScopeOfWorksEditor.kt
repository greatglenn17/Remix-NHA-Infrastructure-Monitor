package com.example.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ScopeItem
import com.example.ui.theme.LocalNHAColors
import com.example.util.FormValidation
import com.example.utils.CurrencyFormatter

/** Each item's weight is its share of the total amount; amounts of zero keep the weights they had. */
fun withAutoWeights(items: List<ScopeItem>): List<ScopeItem> {
    val total = items.sumOf { it.amount }
    if (total <= 0) return items
    return items.map { it.copy(weightPct = it.amount / total * 100) }
}

/** Scope of Works list for the project forms: add, edit and remove items; Wt(%) is worked out from the amounts. */
@Composable
fun ScopeOfWorksEditor(items: SnapshotStateList<ScopeItem>) {
    val cs = MaterialTheme.colorScheme
    val nha = LocalNHAColors.current
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Int?>(null) }
    val amountError = FormValidation.amountError(amount, required = false)
    val parsed = FormValidation.parseAmount(amount)
    val canSave = name.isNotBlank() && parsed != null && parsed > 0

    fun save() {
        val item = ScopeItem(name.trim(), parsed ?: return, 0.0)
        val updated = items.toMutableList().apply { editing?.let { set(it, item) } ?: add(item) }
        items.clear(); items.addAll(withAutoWeights(updated))
        name = ""; amount = ""; editing = null
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Scope of Works", style = MaterialTheme.typography.titleSmall.copy(color = cs.primary, fontWeight = FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (editing != null) {
                TextButton(onClick = { name = ""; amount = ""; editing = null }) { Text("Cancel", fontSize = 12.sp) }
            }
            Button(
                onClick = ::save,
                enabled = canSave,
                modifier = Modifier.heightIn(min = 48.dp).testTag("scope_save_button"),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            ) {
                Text(if (editing != null) "Update item" else "Add Scope of Works", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Item", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            modifier = Modifier.weight(1.8f),
            singleLine = true
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("Amount", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountError != null,
            modifier = Modifier.weight(1.2f),
            singleLine = true
        )
    }
    if (items.isEmpty()) return
    Surface(color = cs.surfaceVariant, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEachIndexed { index, item ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.name, color = if (editing == index) cs.primary else cs.onSurface, style = MaterialTheme.typography.bodySmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.6f)
                    )
                    Text(
                        CurrencyFormatter.formatPhp(item.amount), color = cs.primary, style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.2f)
                    )
                    Text(
                        "%.2f%%".format(item.weightPct), color = cs.onSurface, style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.End, modifier = Modifier.weight(0.8f)
                    )
                    IconButton(onClick = {
                        name = item.name
                        amount = if (item.amount > 0) "%.2f".format(item.amount) else ""
                        editing = index
                    }) { Icon(Icons.Default.Edit, contentDescription = "Edit ${item.name}") }
                    IconButton(onClick = {
                        val updated = items.toMutableList().apply { removeAt(index) }
                        items.clear(); items.addAll(withAutoWeights(updated))
                        if (editing == index) { name = ""; amount = ""; editing = null }
                        else editing = editing?.let { if (it > index) it - 1 else it }
                    }) { Icon(Icons.Default.Delete, contentDescription = "Remove ${item.name}", tint = nha.behind) }
                }
            }
            HorizontalDivider(color = cs.outlineVariant, modifier = Modifier.padding(end = 12.dp, top = 4.dp, bottom = 4.dp))
            val totalWeight = items.sumOf { it.weightPct }
            Row(modifier = Modifier.fillMaxWidth().padding(end = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Total", color = cs.onSurface, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.6f))
                Text(
                    CurrencyFormatter.formatPhp(items.sumOf { it.amount }), color = cs.primary, style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1.2f)
                )
                Text(
                    "%.2f%%".format(totalWeight),
                    color = if (kotlin.math.abs(totalWeight - 100.0) < 0.01) nha.onTrack else nha.behind,
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End,
                    modifier = Modifier.weight(0.8f)
                )
                Spacer(Modifier.width(96.dp))
            }
        }
    }
}
