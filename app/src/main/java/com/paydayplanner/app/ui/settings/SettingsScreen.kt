package com.paydayplanner.app.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.paydayplanner.app.domain.Dates
import com.paydayplanner.app.domain.Money
import com.paydayplanner.app.reminders.BillReminderWorker
import com.paydayplanner.app.ui.components.LabeledSwitch
import com.paydayplanner.app.ui.components.MoneyField
import com.paydayplanner.app.ui.components.NumberField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val loaded by vm.settings.collectAsStateWithLifecycle()
    val s = loaded
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val context = LocalContext.current

    var payday1 by remember(s) { mutableStateOf(s.payday1.toString()) }
    var payday2 by remember(s) { mutableStateOf(s.payday2.toString()) }
    var income1 by remember(s) { mutableStateOf(Money.toInput(s.income1)) }
    var income2 by remember(s) { mutableStateOf(Money.toInput(s.income2)) }
    var cap by remember(s) { mutableStateOf(Money.toInput(s.capCents)) }
    var currency by remember(s) { mutableStateOf(s.currency) }
    var reminders by remember(s) { mutableStateOf(s.remindersEnabled) }
    var remindDays by remember(s) { mutableStateOf(s.remindDaysBefore.toString()) }

    val d1 = payday1.toIntOrNull()?.takeIf { it in 1..31 }
    val d2 = payday2.toIntOrNull()?.takeIf { it in 1..31 }
    val days = remindDays.toIntOrNull()?.takeIf { it in 0..14 }
    val valid = d1 != null && d2 != null && days != null && currency.isNotBlank()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Paydays", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(payday1, { payday1 = it }, "1st payday (day)", Modifier.weight(1f))
                NumberField(payday2, { payday2 = it }, "2nd payday (day)", Modifier.weight(1f))
            }
            Text(
                if (d1 != null && d2 != null) {
                    "Periods run from the ${Dates.ordinal(d1)} and the ${Dates.ordinal(d2)} to the day before the next payday. " +
                        "Days past the end of a month (like 30 in February) fall on its last day."
                } else {
                    "Enter days between 1 and 31."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (d1 != null && d2 != null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )

            Text("Default budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            MoneyField(income1, { income1 = it }, "Income on 1st payday", currency)
            MoneyField(income2, { income2 = it }, "Income on 2nd payday", currency)
            MoneyField(cap, { cap = it }, "Spending cap per period (optional)", currency)
            Text(
                "You can change these for a single period from the Budget tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = currency,
                onValueChange = { if (it.length <= 4) currency = it },
                label = { Text("Currency symbol") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Reminders", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            LabeledSwitch("Remind me about bills", reminders) { reminders = it }
            if (reminders) {
                NumberField(remindDays, { remindDays = it }, "Days before due date (0–14)", Modifier.fillMaxWidth())
                OutlinedButton(
                    onClick = {
                        WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<BillReminderWorker>().build())
                        Toast.makeText(context, "Checking bills now…", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Check reminders now") }
            }

            Button(
                enabled = valid,
                onClick = {
                    vm.save(
                        s.copy(
                            payday1 = d1!!,
                            payday2 = d2!!,
                            income1 = Money.parse(income1) ?: 0,
                            income2 = Money.parse(income2) ?: 0,
                            capCents = Money.parse(cap) ?: 0,
                            currency = currency.trim(),
                            remindersEnabled = reminders,
                            remindDaysBefore = days!!,
                        ),
                    )
                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Save") }
        }
    }
}
