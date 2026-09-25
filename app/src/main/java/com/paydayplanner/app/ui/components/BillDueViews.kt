package com.paydayplanner.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.paydayplanner.app.data.Expense
import com.paydayplanner.app.domain.BillDue
import com.paydayplanner.app.domain.Dates
import com.paydayplanner.app.domain.Money
import java.time.LocalDate

/**
 * A bill occurrence. The checkbox is ticked when fully paid and half-ticked when partly paid.
 * Tapping the checkbox pays (or shows details if already paid); tapping the row shows details.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillDueRow(due: BillDue, currency: String, onPay: () -> Unit, onOpen: () -> Unit) {
    val today = LocalDate.now()
    val overdue = due.isOverdue(today)
    val status = when {
        due.settled -> "Paid ${Dates.short(due.lastPaidOn!!)} · due ${Dates.short(due.dueDate)}"
        due.partial -> "${Money.format(due.paidCents, currency)} paid · " +
            if (overdue) "was due ${Dates.long(due.dueDate)}" else "due ${Dates.short(due.dueDate)}"
        overdue -> "Was due ${Dates.long(due.dueDate)}"
        due.dueDate == today -> "Due today"
        else -> "Due ${Dates.short(due.dueDate)}"
    }
    val subtitle = due.installment?.let { "$status · payment $it" } ?: status
    val strike = if (due.settled) TextDecoration.LineThrough else null

    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(
            containerColor = if (due.settled) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                TriStateCheckbox(
                    state = when {
                        due.settled -> ToggleableState.On
                        due.partial -> ToggleableState.Indeterminate
                        else -> ToggleableState.Off
                    },
                    onClick = { if (due.settled) onOpen() else onPay() },
                )
            },
            headlineContent = { Text(due.bill.name, textDecoration = strike) },
            supportingContent = {
                Text(subtitle, color = if (overdue) MaterialTheme.colorScheme.error else Color.Unspecified)
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    if (due.partial) {
                        Text(Money.format(due.remainingCents, currency), style = MaterialTheme.typography.titleSmall)
                        Text(
                            "left of ${Money.format(due.bill.amountCents, currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            Money.format(due.amountCents, currency),
                            style = MaterialTheme.typography.titleSmall,
                            textDecoration = strike,
                        )
                    }
                }
            },
        )
    }
}

/** Which bill occurrence is open in the details dialog or the pay dialog. */
class BillDueSelection {
    var opened by mutableStateOf<Pair<Long, LocalDate>?>(null)
    var paying by mutableStateOf<Pair<Long, LocalDate>?>(null)

    fun open(due: BillDue) { opened = due.key }
    fun pay(due: BillDue) { paying = due.key }
}

private val BillDue.key get() = bill.id to dueDate

@Composable
fun rememberBillDueSelection() = remember { BillDueSelection() }

/**
 * Shows the details / pay dialogs for [selection]. Looks the occurrence up in [dues] each time,
 * so the details dialog updates live as payments are added or removed.
 */
@Composable
fun BillDueDialogs(
    selection: BillDueSelection,
    dues: List<BillDue>,
    currency: String,
    onPay: (due: BillDue, amountCents: Long, paidOn: LocalDate, settle: Boolean) -> Unit,
    onRemovePayment: (Expense) -> Unit,
) {
    fun find(key: Pair<Long, LocalDate>?) = key?.let { k -> dues.firstOrNull { it.key == k } }

    find(selection.opened)?.let { due ->
        BillDueDialog(
            due = due,
            currency = currency,
            onPay = { selection.paying = due.key },
            onRemovePayment = onRemovePayment,
            onDismiss = { selection.opened = null },
        )
    }
    find(selection.paying)?.let { due ->
        PayBillDialog(
            due = due,
            currency = currency,
            onConfirm = { amount, date, settle -> onPay(due, amount, date, settle); selection.paying = null },
            onDismiss = { selection.paying = null },
        )
    }
}

/** Details of one bill occurrence: amounts, payment history, and a Pay button. */
@Composable
fun BillDueDialog(
    due: BillDue,
    currency: String,
    onPay: () -> Unit,
    onRemovePayment: (Expense) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(due.bill.name) },
        text = {
            FormColumn {
                val installment = due.installment?.let { " · payment $it" }.orEmpty()
                Text("Due ${Dates.long(due.dueDate)}$installment", style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AmountRow("Bill amount", Money.format(due.bill.amountCents, currency))
                    AmountRow("Paid", Money.format(due.paidCents, currency))
                    AmountRow(
                        if (due.settled) "Status" else "Remaining",
                        if (due.settled) "Paid in full" else Money.format(due.remainingCents, currency),
                        emphasize = true,
                    )
                }
                if (due.payments.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Payments", style = MaterialTheme.typography.titleSmall)
                    due.payments.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${Dates.long(LocalDate.ofEpochDay(p.epochDay))} · ${Money.format(p.amountCents, currency)}",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            TextButton(onClick = { onRemovePayment(p) }) {
                                Text("Remove", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!due.settled) TextButton(onClick = onPay) { Text(if (due.partial) "Pay more" else "Pay") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun AmountRow(label: String, value: String, emphasize: Boolean = false) {
    val style = if (emphasize) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f), style = style)
        Text(value, style = style)
    }
}

/** Pay all or part of a bill occurrence. Paying less than what's left keeps the rest as remaining. */
@Composable
fun PayBillDialog(
    due: BillDue,
    currency: String,
    onConfirm: (amountCents: Long, paidOn: LocalDate, settle: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val owed = due.remainingCents
    var amount by remember { mutableStateOf(Money.toInput(owed)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var settle by remember { mutableStateOf(false) }
    val cents = Money.parse(amount)
    val short = cents != null && cents in 1 until owed

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay ${due.bill.name}") },
        text = {
            FormColumn {
                val left = if (due.partial) " · ${Money.format(owed, currency)} left" else ""
                Text("Due ${Dates.long(due.dueDate)}$left", style = MaterialTheme.typography.bodyMedium)
                MoneyField(amount, { amount = it }, "Amount paid", currency)
                DateField("Paid on", date) { date = it }
                if (short) {
                    LabeledSwitch("Mark as fully paid", settle) { settle = it }
                    Text(
                        if (settle) {
                            "Use this when the bill came out lower, e.g. electricity. Nothing will be carried over."
                        } else {
                            "Partial payment: ${Money.format(owed - cents!!, currency)} will stay as remaining " +
                                "and is flagged until it's paid."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 0.dp),
                    )
                } else {
                    Text(
                        "This will be recorded as an expense on the date you paid it.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = { onConfirm(cents!!, date, settle) }) {
                Text(if (short && !settle) "Pay part" else "Mark paid")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
