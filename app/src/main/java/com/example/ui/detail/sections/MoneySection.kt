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
import com.example.data.model.PendingDocument
import com.example.data.model.ProjectPayment
import com.example.data.model.Permission
import com.example.data.model.UserRole
import com.example.data.model.hasPermission
import com.example.ui.dashboard.ProgressLine
import com.example.ui.detail.tabs.AddPaymentDialog
import com.example.ui.detail.tabs.ViewDvScanDialog
import com.example.util.BillingProgress
import com.example.utils.CurrencyFormatter

/** Money: billing summary, payments as cards (2 columns from 600dp) and the variation orders log. */
@Composable
fun MoneySection(
    payments: List<ProjectPayment>,
    // Cost billings are measured against; see ContractCost.current.
    contractCost: Double,
    actualPercent: Double,
    documents: List<PendingDocument>,
    canAddVariationOrder: Boolean,
    onAddVariationOrder: () -> Unit,
    currentUserRole: UserRole,
    onUpdateOrderStatus: (PendingDocument, String, String) -> Unit,
    onDeleteOrder: (Long) -> Unit,
    onAddPayment: (String, String, String, String, Double, Double, Double, Double, String) -> Unit,
    onUpdatePayment: (ProjectPayment) -> Unit,
    onDeletePayment: (ProjectPayment) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var viewingDvPayment by remember { mutableStateOf<ProjectPayment?>(null) }
    var paymentToAttachScan by remember { mutableStateOf<ProjectPayment?>(null) }
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
                                canEdit = canManagePayments
                            )
                        }
                        if (row.size < perRow) Spacer(Modifier.weight(1f))
                    }
                }
            }
            item { VariationOrdersLog(documents, canAddVariationOrder, onAddVariationOrder, currentUserRole, onUpdateOrderStatus, onDeleteOrder) }
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

/** Money › Variation orders: every order, pending too, so a new one shows; only approved ones change the cost. */
@Composable
fun VariationOrdersLog(
    documents: List<PendingDocument>,
    canAddVariationOrder: Boolean,
    onAddVariationOrder: () -> Unit,
    currentUserRole: UserRole,
    onUpdateStatus: (PendingDocument, String, String) -> Unit,
    onDeleteDocument: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var openOrder by remember { mutableStateOf<PendingDocument?>(null) }
    val voLogs = documents.filter { it.documentName.contains("Variation Order", ignoreCase = true) }
    SectionCard(
        "Variation orders (${voLogs.size})",
        Icons.Default.ReceiptLong,
        modifier,
        action = if (canAddVariationOrder) {
            {
                IconButton(onClick = onAddVariationOrder, modifier = Modifier.testTag("add_variation_button")) {
                    Icon(Icons.Default.AddCircle, contentDescription = "Add variation order", tint = MaterialTheme.colorScheme.primary)
                }
            }
        } else null
    ) {
        if (voLogs.isEmpty()) EmptyLine("No variation orders recorded.")
        voLogs.forEach { vo ->
            val dateApproved = Regex("\\|\\s*Date Approved:\\s*([^|]+)").find(vo.remarks)?.groupValues?.get(1)?.trim()
            val cleanRemarks = vo.remarks.replace("^Remarks:\\s*".toRegex(RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\|\\s*Date Approved:[^|]*"), "").trim()
            val displayText = if (cleanRemarks.startsWith("Reason:", ignoreCase = true)) {
                cleanRemarks
            } else if (cleanRemarks.isNotBlank()) {
                "Reason: $cleanRemarks"
            } else ""
            val type = when {
                vo.documentName.contains("Additive", ignoreCase = true) -> "Additive"
                vo.documentName.contains("Deductive", ignoreCase = true) -> "Deductive"
                else -> ""
            }
            LogEntry(
                vo.documentName,
                MaterialTheme.colorScheme.onSurface,
                listOf(if (type.isNotEmpty()) "Type: $type" else "", "Status: ${vo.status}", "Date Approved: ${dateApproved ?: "-"}", displayText),
                onClick = if (currentUserRole.hasPermission(Permission.UPLOAD_DOCUMENT)) { { openOrder = vo } } else null
            )
        }
    }
    openOrder?.let { OrderStatusDialog(it, currentUserRole, onUpdateStatus, onDeleteDocument) { openOrder = null } }
}
