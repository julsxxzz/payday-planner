package com.paydayplanner.app.ui.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
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
            items(list, key = { it.id }) { bill ->
                ListItem(
                    modifier = Modifier.clickable { editing = bill },
                    headlineContent = { Text(bill.name) },
                    supportingContent = {
                        val schedule = if (bill.recurring) {
                            "Every month on the ${Dates.ordinal(bill.dueDay)}"
                        } else {
                            "Once, on ${Dates.long(LocalDate.ofEpochDay(bill.startEpochDay))}"
                        }
                        Text(if (bill.active) "$schedule · ${bill.category}" else "Inactive · $schedule")
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
            onDelete = { vm.delete(it); editing = null },
            onDismiss = { adding = false; editing = null },
        )
    }
}
