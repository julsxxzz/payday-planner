package com.paydayplanner.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paydayplanner.app.data.Bill
import com.paydayplanner.app.data.Categories
import com.paydayplanner.app.data.Expense
import com.paydayplanner.app.domain.BillDue
import com.paydayplanner.app.domain.Dates
import com.paydayplanner.app.domain.Money
import java.time.LocalDate

@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun DeleteAndCancel(onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    Row {
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        }
        TextButton(onClick = onDismiss) { Text("Cancel") }
    }
}

@Composable
fun ExpenseDialog(
    initial: Expense?,
    defaultDate: LocalDate,
    currency: String,
    onSave: (Expense) -> Unit,
    onDelete: ((Expense) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(initial?.let { Money.toInput(it.amountCents) } ?: "") }
    var note by remember { mutableStateOf(initial?.note ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: Categories.all.first()) }
    var date by remember { mutableStateOf(initial?.let { LocalDate.ofEpochDay(it.epochDay) } ?: defaultDate) }
    val cents = Money.parse(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add expense" else "Edit expense") },
        text = {
            FormColumn {
                MoneyField(amount, { amount = it }, "Amount", currency)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("What for?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                CategoryPicker(category) { category = it }
                DateField("Date", date) { date = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && cents > 0,
                onClick = {
                    val base = initial ?: Expense(amountCents = 0, note = "", category = category, epochDay = 0)
                    onSave(base.copy(amountCents = cents!!, note = note.trim(), category = category, epochDay = date.toEpochDay()))
                },
            ) { Text("Save") }
        },
        dismissButton = {
            DeleteAndCancel(
                onDelete = if (initial != null && onDelete != null) ({ onDelete(initial) }) else null,
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
fun BillDialog(
    initial: Bill?,
    currency: String,
    onSave: (Bill) -> Unit,
    onDelete: ((Bill) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var amount by remember { mutableStateOf(initial?.let { Money.toInput(it.amountCents) } ?: "") }
    var category by remember { mutableStateOf(initial?.category ?: "Bills & Utilities") }
    var recurring by remember { mutableStateOf(initial?.recurring ?: true) }
    var active by remember { mutableStateOf(initial?.active ?: true) }
    var date by remember {
        mutableStateOf(
            initial?.let {
                val start = LocalDate.ofEpochDay(it.startEpochDay)
                // Show the real due day for recurring bills (start may be clamped to a shorter month).
                if (it.recurring) start.withDayOfMonth(minOf(it.dueDay, start.lengthOfMonth())) else start
            } ?: LocalDate.now(),
        )
    }
    val cents = Money.parse(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New bill" else "Edit bill") },
        text = {
            FormColumn {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (e.g. Rent, Electricity)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                MoneyField(amount, { amount = it }, "Amount", currency)
                CategoryPicker(category) { category = it }
                LabeledSwitch("Repeats every month", recurring) { recurring = it }
                DateField(if (recurring) "First due date" else "Due date", date) { date = it }
                if (recurring) {
                    Text(
                        "Due on the ${Dates.ordinal(date.dayOfMonth)} of every month, starting ${Dates.long(date)}.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (initial != null) LabeledSwitch("Active", active) { active = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && cents != null && cents > 0,
                onClick = {
                    onSave(
                        Bill(
                            id = initial?.id ?: 0,
                            name = name.trim(),
                            amountCents = cents!!,
                            category = category,
                            recurring = recurring,
                            dueDay = date.dayOfMonth,
                            startEpochDay = date.toEpochDay(),
                            active = active,
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = {
            DeleteAndCancel(
                onDelete = if (initial != null && onDelete != null) ({ onDelete(initial) }) else null,
                onDismiss = onDismiss,
            )
        },
    )
}

@Composable
fun PayBillDialog(
    due: BillDue,
    currency: String,
    onConfirm: (amountCents: Long, paidOn: LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(Money.toInput(due.bill.amountCents)) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    val cents = Money.parse(amount)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pay ${due.bill.name}") },
        text = {
            FormColumn {
                Text("Due ${Dates.long(due.dueDate)}", style = MaterialTheme.typography.bodyMedium)
                MoneyField(amount, { amount = it }, "Amount paid", currency)
                DateField("Paid on", date) { date = it }
                Text(
                    "This will be recorded as an expense on the date you paid it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(enabled = cents != null && cents > 0, onClick = { onConfirm(cents!!, date) }) {
                Text("Mark paid")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun PeriodBudgetDialog(
    periodLabel: String,
    incomeCents: Long,
    capCents: Long,
    hasOverride: Boolean,
    currency: String,
    onSave: (income: Long, cap: Long) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    var income by remember { mutableStateOf(Money.toInput(incomeCents)) }
    var cap by remember { mutableStateOf(Money.toInput(capCents)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Budget for this period") },
        text = {
            FormColumn {
                Text(periodLabel, style = MaterialTheme.typography.bodyMedium)
                MoneyField(income, { income = it }, "Income this payday", currency)
                MoneyField(cap, { cap = it }, "Spending cap (leave empty for none)", currency)
                Text(
                    "The cap is for everyday spending. Bills are tracked separately.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Money.parse(income) ?: 0, Money.parse(cap) ?: 0) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (hasOverride) TextButton(onClick = onReset) { Text("Use defaults") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
