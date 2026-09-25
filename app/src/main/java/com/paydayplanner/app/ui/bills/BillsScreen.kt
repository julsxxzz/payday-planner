package com.paydayplanner.app.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paydayplanner.app.data.Bill
import com.paydayplanner.app.domain.BillDue
import com.paydayplanner.app.domain.BillSort
import com.paydayplanner.app.domain.Dates
import com.paydayplanner.app.domain.Money
import com.paydayplanner.app.domain.lastDueDate
import com.paydayplanner.app.domain.limitedPayments
import com.paydayplanner.app.ui.components.BillDialog
import com.paydayplanner.app.ui.components.BillDueDialogs
import com.paydayplanner.app.ui.components.BillDueRow
import com.paydayplanner.app.ui.components.rememberBillDueSelection
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(vm: BillsViewModel) {
    val loaded by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Bill?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Bill?>(null) }
    val selection = rememberBillDueSelection()

    val s = loaded
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val currency = s.currency

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Bills") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Bill") },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("By month") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("All bills") })
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (s.bills.isEmpty()) {
                    item {
                        Text(
                            "No bills yet. Add things you need to pay, like rent, electricity, internet or " +
                                "loan payments. They'll show up in the month they're due.",
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (tab == 0) {
                    monthContent(s, vm, onPay = selection::pay, onOpen = selection::open)
                } else {
                    items(s.bills, key = { it.bill.id }) { row ->
                        BillDefinitionRow(row, currency, onClick = { editing = row.bill })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    BillDueDialogs(
        selection = selection,
        dues = s.carriedOver + s.dues,
        currency = currency,
        onPay = vm::payBill,
        onRemovePayment = vm::removePayment,
    )
    if (adding || editing != null) {
        BillDialog(
            initial = editing,
            currency = currency,
            onSave = { vm.save(it); adding = false; editing = null },
            onDelete = { confirmDelete = it; editing = null },
            onDismiss = { adding = false; editing = null },
        )
    }
    confirmDelete?.let { bill ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${bill.name}?") },
            text = {
                Text(
                    "Payments you already made stay in your expenses, but this bill will disappear from " +
                        "past months.\n\nIf it's just finished (like a paid-off loan), turn off \"Active\" " +
                        "instead to keep its history.",
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.delete(bill); confirmDelete = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = bill; confirmDelete = null }) { Text("Keep") }
            },
        )
    }
}

private fun LazyListScope.monthContent(
    s: BillsState,
    vm: BillsViewModel,
    onPay: (BillDue) -> Unit,
    onOpen: (BillDue) -> Unit,
) {
    item { MonthSwitcher(s, vm::previousMonth, vm::nextMonth, vm::thisMonth) }
    item { MonthSummary(s) }

    if (s.carriedOver.isNotEmpty()) {
        item {
            SectionHeader(
                "Needs to be paid",
                "${Money.format(s.carriedOverCents, s.currency)} from earlier months",
                MaterialTheme.colorScheme.error,
            )
        }
        items(s.carriedOver, key = { "c-${it.bill.id}-${it.dueDate}" }) { due ->
            BillDueRow(due, s.currency, onPay = { onPay(due) }, onOpen = { onOpen(due) })
        }
    }

    item {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Due in ${Dates.monthName(s.month)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            SortMenu(s.sort, vm::setSort)
        }
    }
    if (s.dues.isEmpty()) {
        item {
            Text(
                "Nothing due this month.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    items(s.dues, key = { "d-${it.bill.id}-${it.dueDate}" }) { due ->
        BillDueRow(due, s.currency, onPay = { onPay(due) }, onOpen = { onOpen(due) })
    }
}

@Composable
private fun MonthSwitcher(s: BillsState, onPrev: () -> Unit, onNext: () -> Unit, onThisMonth: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Dates.month(s.month), style = MaterialTheme.typography.titleMedium)
            if (s.isCurrentMonth) {
                Text("This month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onThisMonth) { Text("Back to this month") }
            }
        }
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun MonthSummary(s: BillsState) {
    val c = s.currency
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AmountLine("Bills this month", Money.format(s.totalCents, c))
            AmountLine("Paid", Money.format(s.paidCents, c))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Left to pay", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    Money.format(s.remainingCents, c),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (s.remainingCents == 0L) MaterialTheme.colorScheme.primary else Color.Unspecified,
                )
            }
            if (s.totalCents > 0) {
                LinearProgressIndicator(
                    progress = { (s.paidCents.toFloat() / s.totalCents).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun SortMenu(current: BillSort, onSelect: (BillSort) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text("Sort: ${current.label}")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            BillSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(sort.label) },
                    onClick = { onSelect(sort); expanded = false },
                    trailingIcon = { if (sort == current) Icon(Icons.Filled.Check, contentDescription = null) },
                )
            }
        }
    }
}

@Composable
private fun BillDefinitionRow(row: BillRow, currency: String, onClick: () -> Unit) {
    val bill = row.bill
    val total = bill.limitedPayments
    val paidOff = total != null && row.settledCount >= total
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(bill.name) },
        supportingContent = {
            val schedule = if (bill.recurring) {
                "Every month on the ${Dates.ordinal(bill.dueDay)}"
            } else {
                "Once, on ${Dates.long(LocalDate.ofEpochDay(bill.startEpochDay))}"
            }
            val status = when {
                paidOff -> "Paid off"
                !bill.active -> "Inactive"
                else -> null
            }
            Column {
                Text(listOfNotNull(status, schedule, bill.category).joinToString(" · "))
                if (total != null) {
                    val last = bill.lastDueDate()?.let { " · last payment ${Dates.long(it)}" }.orEmpty()
                    Text(
                        "${minOf(row.settledCount, total)} of $total paid$last",
                        color = if (paidOff) MaterialTheme.colorScheme.primary else Color.Unspecified,
                    )
                    LinearProgressIndicator(
                        progress = { minOf(row.settledCount, total).toFloat() / total },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        },
        trailingContent = { Text(Money.format(bill.amountCents, currency), style = MaterialTheme.typography.titleSmall) },
    )
}

@Composable
private fun AmountLine(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SectionHeader(title: String, trailing: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = color, modifier = Modifier.weight(1f))
        Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
