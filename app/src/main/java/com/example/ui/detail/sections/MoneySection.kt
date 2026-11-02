package com.example.ui.detail.sections

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ProjectPayment
import com.example.data.model.Permission
import com.example.data.model.UserRole
import com.example.data.model.hasPermission
import com.example.ui.dashboard.ProgressLine
import com.example.ui.detail.tabs.AddPaymentDialog
import com.example.ui.detail.tabs.ViewDvScanDialog
import com.example.util.BillingProgress
import com.example.utils.CurrencyFormatter

/** Money: billing summary and payments as cards (2 columns from 600dp); variation orders are under Documents. */
@Composable
fun MoneySection(
    payments: List<ProjectPayment>,
    // Cost billings are measured against; see ContractCost.current.
    contractCost: Double,
    actualPercent: Double,
    currentUserRole: UserRole,
    onAddPayment: (String, String, String, String, Double, Double, Double, Double, String) -> Unit,
    onUpdatePayment: (ProjectPayment) -> Unit,
    onDeletePayment: (ProjectPayment) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var viewingDvPayment by remember { mutableStateOf<ProjectPayment?>(null) }
    var paymentToAttachScan by remember { mutableStateOf<ProjectPayment?>(null) }
    var paymentToRename by remember { mutableStateOf<ProjectPayment?>(null) }
    val canManagePayments = currentUserRole.hasPermission(Permission.MANAGE_PAYMENTS)

    // Uploads a scanned copy straight to an existing payment
    val attachToPayment = com.example.ui.detail.tabs.rememberDvAttacher { ref ->
        paymentToAttachScan?.let { payment ->
            val updated = payment.copy(fileUrl = ref)
            onUpdatePayment(updated)
            if (viewingDvPayment?.id == updated.id) viewingDvPayment = updated
            paymentToAttachScan = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val perRow = if (maxWidth >= 600.dp) 2 else 1
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item { BillingSummary(payments, contractCost, actualPercent) }
            payments.chunked(perRow).forEach { row ->
                item(key = row.first().id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        row.forEach { payment ->
                            PaymentCard(
                                payment = payment,
                                onViewScan = { viewingDvPayment = payment },
                                // The DV dialog offers Scan, Take photo and Upload.
                                onAttachScan = { viewingDvPayment = payment },
                                onDelete = { onDeletePayment(payment) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                canEdit = canManagePayments,
                                onEditName = { paymentToRename = payment }
                            )
                        }
                        if (row.size < perRow) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        if (canManagePayments) ExtendedFloatingActionButton(
            onClick = { showAddDialog = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add billing") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag("add_billing_button")
        )
    }

    if (showAddDialog) {
        AddPaymentDialog(
            baseCost = contractCost,
            currentTotalPaid = payments.sumOf { it.grossAmount },
            onDismiss = { showAddDialog = false },
            onSubmit = { name, dvNo, date, period, gross, fileUrl ->
                val pct = if (contractCost > 0) (gross / contractCost) * 100 else 0.0
                val newBal = contractCost - (payments.sumOf { it.grossAmount } + gross)
                val balPct = if (contractCost > 0) (newBal / contractCost) * 100 else 0.0
                onAddPayment(name, dvNo, date, period, gross, pct, newBal, balPct, fileUrl)
                showAddDialog = false
            }
        )
    }

    paymentToRename?.let { payment ->
        EditBillingNameDialog(payment.name, onDismiss = { paymentToRename = null }) { name ->
            onUpdatePayment(payment.copy(name = name))
            paymentToRename = null
        }
    }

    if (viewingDvPayment != null) {
        ViewDvScanDialog(
            payment = viewingDvPayment!!,
            onDismiss = { viewingDvPayment = null },
            onReplaceScan = if (canManagePayments) {
                { source ->
                    paymentToAttachScan = viewingDvPayment
                    attachToPayment(source)
                }
            } else null
        )
    }
}

/** The old table's grand-total row: cost, billed, billed vs work done, balance. */
@Composable
fun BillingSummary(payments: List<ProjectPayment>, contractCost: Double, actualPercent: Double) {
    val scheme = MaterialTheme.colorScheme
    val totalBilled = payments.sumOf { it.grossAmount }
    SectionCard(
        "Billing summary",
        Icons.Default.Payments,
    ) {
        Field("Current contract cost", CurrencyFormatter.formatPhp(contractCost))
        Field("Total billed", if (payments.isEmpty()) "No billings yet" else CurrencyFormatter.formatPhp(totalBilled))
        Field("Balance", CurrencyFormatter.formatPhp(contractCost - totalBilled))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val billedPct = if (contractCost > 0 && totalBilled > 0) totalBilled / contractCost * 100 else null
            ProgressLine(
                label = "Billed",
                value = BillingProgress.label(totalBilled, contractCost),
                fraction = ((billedPct ?: 0.0) / 100).toFloat(),
                color = scheme.primary,
                modifier = Modifier.weight(1f)
            )
            ProgressLine(
                label = "Work done",
                value = "%.2f%%".format(actualPercent),
                fraction = (actualPercent / 100).toFloat(),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** The trimmed new billing name, or null when it is blank or unchanged (Save stays off). */
internal fun renamedBilling(current: String, typed: String): String? = typed.trim().takeIf { it.isNotEmpty() && it != current }

/** Renames a billing; the amounts, DV and scan stay as they are. */
@Composable
private fun EditBillingNameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit billing name") },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, label = { Text("Billing name") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("billing_name_field")
            )
        },
        confirmButton = {
            val renamed = renamedBilling(current, name)
            TextButton(onClick = { renamed?.let(onSave) }, enabled = renamed != null) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
