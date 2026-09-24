package com.paydayplanner.app.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import com.paydayplanner.app.domain.lastDueDate
import com.paydayplanner.app.domain.limitedPayments
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.paydayplanner.app.data.Bill
import com.paydayplanner.app.domain.Dates
import com.paydayplanner.app.domain.Money
import com.paydayplanner.app.ui.components.BillDialog
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(vm: BillsViewModel) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val currency by vm.currency.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Bill?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<Bill?>(null) }

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
        val list = bills.orEmpty()
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            if (bills != null && list.isEmpty()) {
                item {
                    Text(
                        "No bills yet. Add things you need to pay, like rent, electricity, internet or loan payments. " +
                            "They'll show up under \"To be paid\" in the period they're due.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(list, key = { it.bill.id }) { row ->
                val bill = row.bill
                val total = bill.limitedPayments
                val paidOff = total != null && row.paidCount >= total
                ListItem(
                    modifier = Modifier.clickable { editing = bill },
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
                                    "${minOf(row.paidCount, total)} of $total paid$last",
                                    color = if (paidOff) MaterialTheme.colorScheme.primary else Color.Unspecified,
                                )
                                LinearProgressIndicator(
                                    progress = { minOf(row.paidCount, total).toFloat() / total },
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                )
                            }
                        }
                    },
                    trailingContent = {
                        Text(Money.format(bill.amountCents, currency), style = MaterialTheme.typography.titleSmall)
                    },
                )
                HorizontalDivider()
            }
        }
    }

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
                        "past periods' \"To be paid\" lists.\n\nIf it's just finished (like a paid-off loan), " +
                        "turn off \"Active\" instead to keep its history.",
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
