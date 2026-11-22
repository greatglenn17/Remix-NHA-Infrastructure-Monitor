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
import com.example.data.model.DeveloperRequest
import com.example.data.model.billingText
import com.example.data.model.netAmount
import com.example.data.model.netBillings
import com.example.data.model.ProjectPayment
import com.example.data.model.Permission
import com.example.data.model.UserRole
import com.example.data.model.hasPermission
import com.example.ui.dashboard.ProgressLine
import com.example.ui.detail.tabs.AddPaymentDialog
import com.example.ui.detail.tabs.ViewDvScanDialog
import com.example.ui.theme.LocalNHAColors
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
    // Housing projects bill land and housing units by count; see HOUSING_BILLING_TYPES.
    isHousing: Boolean = false,
    // The contract amount of land development per lot on a billing date; see AddPaymentDialog.
    landPerLot: (date: String) -> Double = { 0.0 },
    // Why a billing (in place of the old one when editing) cannot be saved and what to do; see BillingAbstract.aheadErrors and aheadHint.
    billingCheck: (ProjectPayment, ProjectPayment?) -> List<String> = { _, _ -> emptyList() },
    onAddPayment: (ProjectPayment) -> Unit,
    onUpdatePayment: (ProjectPayment) -> Unit,
    onDeletePayment: (ProjectPayment) -> Unit,
    // Requests for billing no billing came from yet (pendingBillingRequests); [onBilledRequest] saves one with its billing's uuid.
    billingRequests: List<DeveloperRequest> = emptyList(),
    onBilledRequest: (DeveloperRequest) -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var billingRequest by remember { mutableStateOf<DeveloperRequest?>(null) }
    var viewingDvPayment by remember { mutableStateOf<ProjectPayment?>(null) }
    var paymentToAttachScan by remember { mutableStateOf<ProjectPayment?>(null) }
    var paymentToEdit by remember { mutableStateOf<ProjectPayment?>(null) }
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
            if (billingRequests.isNotEmpty()) item(key = "billing_requests") {
                SectionCard("Billing requests (${billingRequests.size})", Icons.Default.MoveToInbox) {
                    billingRequests.forEach { r ->
                        LogEntry(
                            r.billingText, LocalNHAColors.current.watch,
                            listOf(
                                "Requested: ${r.dateRequested.ifBlank { "-" }}  ·  Received: ${r.dateReceived.ifBlank { "-" }}",
                                if (r.datePackaged.isNotBlank()) "Packaged: ${r.datePackaged}" else "Not yet packaged"
                            ),
                            trailing = if (canManagePayments) { { TextButton(onClick = { billingRequest = r }, modifier = Modifier.testTag("mark_billed_${r.uuid}")) { Text("Mark billed") } } } else null
                        )
                    }
                    Text(
                        "Not yet billed. One counts in the Status of billing once marked billed with its DV No. and scanned DV form.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val net = netBillings(payments, contractCost).associateBy { it.id }
            payments.chunked(perRow).forEach { row ->
                item(key = row.first().id) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.height(IntrinsicSize.Min)) {
                        row.forEach { payment ->
                            PaymentCard(
                                payment = net.getValue(payment.id), // shown on the net; edits start from the saved one
                                onViewScan = { viewingDvPayment = payment },
                                // The DV dialog offers Scan, Take photo and Upload.
                                onAttachScan = { viewingDvPayment = payment },
                                onDelete = {
                                    // The later billings get the deleted amount back in their balances.
                                    billingsAfterDelete(payments, payment, contractCost).forEach(onUpdatePayment)
                                    onDeletePayment(payment)
                                },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                canEdit = canManagePayments,
                                onEdit = { paymentToEdit = payment }
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

    // A new billing's share and balance after it, with the contract cost of today and every billing before it.
    fun added(p: ProjectPayment): ProjectPayment {
        val newBal = contractCost - (payments.sumOf { it.grossAmount } + p.grossAmount)
        return p.copy(percentage = share(p.grossAmount, contractCost), balanceAmount = newBal, balancePercentage = share(newBal, contractCost))
    }
    billingRequest?.let { request ->
        AddPaymentDialog(
            baseCost = contractCost,
            currentTotalPaid = payments.sumOf { it.grossAmount },
            onDismiss = { billingRequest = null },
            isHousing = true,
            previous = payments,
            landPerLot = landPerLot,
            check = { billingCheck(it, null) },
            request = request,
            onSubmit = { p ->
                onAddPayment(added(p))
                onBilledRequest(request.copy(paymentUuid = p.uuid))
                billingRequest = null
            }
        )
    }

    if (showAddDialog) {
        AddPaymentDialog(
            baseCost = contractCost,
            currentTotalPaid = payments.sumOf { it.grossAmount },
            onDismiss = { showAddDialog = false },
            isHousing = isHousing,
            previous = payments,
            landPerLot = landPerLot,
            check = { billingCheck(it, null) },
            onSubmit = { p ->
                onAddPayment(added(p))
                showAddDialog = false
            }
        )
    }

    paymentToEdit?.let { old ->
        AddPaymentDialog(
            baseCost = contractCost,
            currentTotalPaid = payments.sumOf { it.grossAmount } - old.grossAmount,
            existing = old,
            onDismiss = { paymentToEdit = null },
            isHousing = isHousing,
            previous = payments,
            landPerLot = landPerLot,
            check = { billingCheck(it, old) },
            onSubmit = { new ->
                editedBillings(payments, old, new, contractCost).forEach(onUpdatePayment)
                paymentToEdit = null
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
    val totalBilled = payments.sumOf { it.netAmount }
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
                modifier = Modifier.weight(1f),
                big = true
            )
            ProgressLine(
                label = "Work done",
                value = "%.2f%%".format(actualPercent),
                fraction = (actualPercent / 100).toFloat(),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                big = true
            )
        }
    }
}

/**
 * The billings to save when [old] is edited into [new]. Each billing keeps the balance left after it, in the
 * order they were added, so a changed amount recomputes its share and moves its own and every later balance
 * by the difference; earlier billings stay as they are.
 */
internal fun editedBillings(payments: List<ProjectPayment>, old: ProjectPayment, new: ProjectPayment, contractCost: Double): List<ProjectPayment> {
    val delta = new.grossAmount - old.grossAmount
    if (delta == 0.0) return listOf(new)
    return listOf(new.shifted(delta, contractCost).copy(percentage = share(new.grossAmount, contractCost))) +
        payments.filter { it.id > old.id }.map { it.shifted(delta, contractCost) }
}

/** The billings to save when [old] is deleted: every later one, with the deleted amount back in its balance. */
internal fun billingsAfterDelete(payments: List<ProjectPayment>, old: ProjectPayment, contractCost: Double): List<ProjectPayment> =
    payments.filter { it.id > old.id }.map { it.shifted(-old.grossAmount, contractCost) }

private fun share(amount: Double, contractCost: Double) = if (contractCost > 0) amount / contractCost * 100 else 0.0

/** This billing's balance once the gross billed up to it has grown by [delta]. */
private fun ProjectPayment.shifted(delta: Double, contractCost: Double) =
    copy(balanceAmount = balanceAmount - delta, balancePercentage = balancePercentage - share(delta, contractCost))
