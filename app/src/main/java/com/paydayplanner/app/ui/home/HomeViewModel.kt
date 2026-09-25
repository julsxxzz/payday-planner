package com.paydayplanner.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paydayplanner.app.AppContainer
import com.paydayplanner.app.data.Expense
import com.paydayplanner.app.data.PeriodBudget
import com.paydayplanner.app.data.Settings
import com.paydayplanner.app.domain.BillDue
import com.paydayplanner.app.domain.OVERDUE_LOOKBACK_DAYS
import com.paydayplanner.app.domain.Period
import com.paydayplanner.app.domain.Periods
import com.paydayplanner.app.domain.buildBillDues
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeState(
    val settings: Settings,
    val period: Period,
    val isCurrent: Boolean,
    val incomeCents: Long,
    val capCents: Long,
    val hasBudgetOverride: Boolean,
    /** Bills due within this period (paid and unpaid). */
    val dues: List<BillDue>,
    /** Bills from earlier periods that aren't fully paid (only shown for the current period). */
    val overdue: List<BillDue>,
    val expenses: List<Expense>,
) {
    val spentTotal: Long = expenses.sumOf { it.amountCents }
    /** Everyday spending, i.e. expenses that aren't bill payments. Counts against the cap. */
    val everydaySpent: Long = expenses.filter { it.billId == null }.sumOf { it.amountCents }
    val unpaidInPeriod: Long = dues.sumOf { it.remainingCents }
    val unpaidOverdue: Long = overdue.sumOf { it.remainingCents }
    val billsTotal: Long = dues.sumOf { it.amountCents }
    /** Income minus everything spent minus every bill still waiting to be paid. */
    val safeToSpend: Long = incomeCents - spentTotal - unpaidInPeriod - unpaidOverdue
    val defaultExpenseDate: LocalDate get() = if (isCurrent) LocalDate.now() else period.start
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(container: AppContainer) : ViewModel() {
    private val expenseDao = container.db.expenseDao()
    private val billDao = container.db.billDao()
    private val budgetDao = container.db.periodBudgetDao()

    /** Any date inside the period being viewed. */
    private val anchor = MutableStateFlow(LocalDate.now())

    val state: StateFlow<HomeState?> = combine(container.settings.settings, anchor) { s, a -> s to a }
        .flatMapLatest { (settings, anchorDate) ->
            val period = Periods.periodFor(anchorDate, settings.payday1, settings.payday2)
            val isCurrent = LocalDate.now() in period
            val lookback = if (isCurrent) period.start.minusDays(OVERDUE_LOOKBACK_DAYS) else period.start
            combine(
                expenseDao.between(period.start.toEpochDay(), period.end.toEpochDay()),
                billDao.all(),
                expenseDao.billPaymentsDueBetween(lookback.toEpochDay(), period.end.toEpochDay()),
                budgetDao.get(period.start.toEpochDay()),
            ) { expenses, bills, payments, budget ->
                val dues = buildBillDues(bills, period.start, period.end, payments)
                    .filter { it.bill.active || it.payments.isNotEmpty() }
                val overdue = if (isCurrent) {
                    buildBillDues(bills.filter { it.active }, lookback, period.start.minusDays(1), payments)
                        .filter { !it.settled }
                } else {
                    emptyList()
                }
                HomeState(
                    settings = settings,
                    period = period,
                    isCurrent = isCurrent,
                    incomeCents = budget?.incomeCents ?: Periods.defaultIncome(period, settings),
                    capCents = budget?.capCents ?: settings.capCents,
                    hasBudgetOverride = budget != null,
                    dues = dues,
                    overdue = overdue,
                    expenses = expenses,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousPeriod() {
        state.value?.let { anchor.value = it.period.start.minusDays(1) }
    }

    fun nextPeriod() {
        state.value?.let { anchor.value = it.period.end.plusDays(1) }
    }

    fun goToToday() {
        anchor.value = LocalDate.now()
    }

    fun saveExpense(expense: Expense) {
        viewModelScope.launch { expenseDao.upsert(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { expenseDao.delete(expense) }
    }

    fun payBill(due: BillDue, amountCents: Long, paidOn: LocalDate, settle: Boolean) {
        viewModelScope.launch { expenseDao.upsert(due.payment(amountCents, paidOn, settle)) }
    }

    fun saveBudget(incomeCents: Long, capCents: Long) {
        val start = state.value?.period?.start ?: return
        viewModelScope.launch { budgetDao.upsert(PeriodBudget(start.toEpochDay(), incomeCents, capCents)) }
    }

    fun resetBudget() {
        val start = state.value?.period?.start ?: return
        viewModelScope.launch { budgetDao.clear(start.toEpochDay()) }
    }
}
