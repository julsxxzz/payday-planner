package com.paydayplanner.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paydayplanner.app.data.Expense
import com.paydayplanner.app.domain.BillDue
import com.paydayplanner.app.domain.Dates
import com.paydayplanner.app.domain.Money
import com.paydayplanner.app.ui.components.ExpenseDialog
import com.paydayplanner.app.ui.components.PayBillDialog
import com.paydayplanner.app.ui.components.PeriodBudgetDialog
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    var addingExpense by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var paying by remember { mutableStateOf<BillDue?>(null) }
    var editingBudget by remember { mutableStateOf(false) }

    val s = loaded
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val currency = s.settings.currency
    val onToggleBill: (BillDue) -> Unit = { due -> if (due.paid) vm.markUnpaid(due) else paying = due }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Payday Planner") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addingExpense = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Expense") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PeriodSwitcher(s, vm::previousPeriod, vm::nextPeriod, vm::goToToday) }
            item { SummaryCard(s, onEditBudget = { editingBudget = true }) }

            if (s.overdue.isNotEmpty()) {
                item { SectionHeader("Overdue", Money.format(s.unpaidOverdue, currency), MaterialTheme.colorScheme.error) }
                items(s.overdue, key = { "o-${it.bill.id}-${it.dueDate}" }) { due ->
                    BillDueRow(due, currency, overdue = true, onToggle = onToggleBill)
                }
            }

            item {
                SectionHeader(
                    "To be paid",
                    "${Money.format(s.unpaidInPeriod, currency)} left of ${Money.format(s.billsTotal, currency)}",
                )
            }
            if (s.dues.isEmpty()) {
                item { EmptyHint("No bills due this period. Add bills in the Bills tab.") }
            }
            items(s.dues, key = { "d-${it.bill.id}-${it.dueDate}" }) { due ->
                BillDueRow(due, currency, overdue = false, onToggle = onToggleBill)
            }

            if (s.expenses.isNotEmpty()) {
                item { CategoryBreakdown(s.expenses, currency) }
            }

            item { SectionHeader("Expenses", Money.format(s.spentTotal, currency)) }
            if (s.expenses.isEmpty()) {
                item { EmptyHint("Nothing spent yet this period.") }
            }
            items(s.expenses, key = { "e-${it.id}" }) { e ->
                ExpenseRow(e, currency, onClick = { editingExpense = e })
            }
        }
    }

    if (addingExpense || editingExpense != null) {
        ExpenseDialog(
            initial = editingExpense,
            defaultDate = s.defaultExpenseDate,
            currency = currency,
            onSave = { vm.saveExpense(it); addingExpense = false; editingExpense = null },
            onDelete = { vm.deleteExpense(it); editingExpense = null },
            onDismiss = { addingExpense = false; editingExpense = null },
        )
    }
    paying?.let { due ->
        PayBillDialog(
            due = due,
            currency = currency,
            onConfirm = { amount, date -> vm.markPaid(due, amount, date); paying = null },
            onDismiss = { paying = null },
        )
    }
    if (editingBudget) {
        PeriodBudgetDialog(
            periodLabel = Dates.range(s.period),
            incomeCents = s.incomeCents,
            capCents = s.capCents,
            hasOverride = s.hasBudgetOverride,
            currency = currency,
            onSave = { income, cap -> vm.saveBudget(income, cap); editingBudget = false },
            onReset = { vm.resetBudget(); editingBudget = false },
            onDismiss = { editingBudget = false },
        )
    }
}

@Composable
private fun PeriodSwitcher(s: HomeState, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Dates.range(s.period), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (s.isCurrent) {
                val daysLeft = s.period.end.toEpochDay() - LocalDate.now().toEpochDay() + 1
                Text(
                    "Current period · $daysLeft day${if (daysLeft == 1L) "" else "s"} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                TextButton(onClick = onToday) { Text("Back to current period") }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
        }
    }
}

@Composable
private fun SummaryCard(s: HomeState, onEditBudget: () -> Unit) {
    val c = s.settings.currency
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onEditBudget) { Text("Edit") }
            }
            AmountLine("Income", Money.format(s.incomeCents, c))
            AmountLine("Bills still to pay", "−" + Money.format(s.unpaidInPeriod + s.unpaidOverdue, c))
            AmountLine("Spent so far", "−" + Money.format(s.spentTotal, c))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Safe to spend", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    Money.format(s.safeToSpend, c),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (s.safeToSpend < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            if (s.incomeCents == 0L) {
                Text(
                    "Tip: tap Edit to enter this payday's income, or set defaults in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (s.capCents > 0) {
                Spacer(Modifier.height(4.dp))
                val fraction = (s.everydaySpent.toFloat() / s.capCents).coerceIn(0f, 1f)
                val over = s.everydaySpent > s.capCents
                AmountLine(
                    "Spending cap",
                    "${Money.format(s.everydaySpent, c)} of ${Money.format(s.capCents, c)}",
                )
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Text(
                    if (over) "Over the cap by ${Money.format(s.everydaySpent - s.capCents, c)}"
                    else "${Money.format(s.capCents - s.everydaySpent, c)} left under the cap",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AmountLine(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillDueRow(due: BillDue, currency: String, overdue: Boolean, onToggle: (BillDue) -> Unit) {
    val errorColor = MaterialTheme.colorScheme.error
    val subtitle = when {
        due.paid -> "Paid ${Dates.short(LocalDate.ofEpochDay(due.payment!!.epochDay))} · due ${Dates.short(due.dueDate)}"
        overdue -> "Was due ${Dates.long(due.dueDate)}"
        due.dueDate == LocalDate.now() -> "Due today"
        else -> "Due ${Dates.short(due.dueDate)}"
    }
    Card(
        onClick = { onToggle(due) },
        colors = CardDefaults.cardColors(
            containerColor = if (due.paid) MaterialTheme.colorScheme.surfaceContainerLow
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Checkbox(checked = due.paid, onCheckedChange = { onToggle(due) }) },
            headlineContent = {
                Text(due.bill.name, textDecoration = if (due.paid) TextDecoration.LineThrough else null)
            },
            supportingContent = {
                Text(subtitle, color = if (overdue && !due.paid) errorColor else Color.Unspecified)
            },
            trailingContent = {
                Text(
                    Money.format(due.amountCents, currency),
                    style = MaterialTheme.typography.titleSmall,
                    textDecoration = if (due.paid) TextDecoration.LineThrough else null,
                )
            },
        )
    }
}

@Composable
private fun ExpenseRow(e: Expense, currency: String, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(e.note.ifBlank { e.category }) },
        supportingContent = {
            val tag = if (e.billId != null) " · bill" else ""
            Text("${e.category} · ${Dates.short(LocalDate.ofEpochDay(e.epochDay))}$tag")
        },
        trailingContent = { Text(Money.format(e.amountCents, currency), style = MaterialTheme.typography.titleSmall) },
    )
}

@Composable
private fun CategoryBreakdown(expenses: List<Expense>, currency: String) {
    val totals = expenses.groupBy { it.category }
        .mapValues { (_, list) -> list.sumOf { it.amountCents } }
        .toList()
        .sortedByDescending { it.second }
    val max = totals.first().second.coerceAtLeast(1)
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Where it went", style = MaterialTheme.typography.titleMedium)
            totals.forEach { (category, cents) ->
                Column {
                    AmountLine(category, Money.format(cents, currency))
                    LinearProgressIndicator(
                        progress = { cents.toFloat() / max },
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    )
                }
            }
        }
    }
}
