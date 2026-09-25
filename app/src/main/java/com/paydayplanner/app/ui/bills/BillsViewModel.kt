package com.paydayplanner.app.ui.bills

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paydayplanner.app.AppContainer
import com.paydayplanner.app.data.Bill
import com.paydayplanner.app.data.Expense
import com.paydayplanner.app.domain.BillDue
import com.paydayplanner.app.domain.BillSort
import com.paydayplanner.app.domain.OVERDUE_LOOKBACK_DAYS
import com.paydayplanner.app.domain.buildBillDues
import com.paydayplanner.app.domain.settledCounts
import com.paydayplanner.app.domain.sortedFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class BillRow(val bill: Bill, val settledCount: Int)

data class BillsState(
    val month: YearMonth,
    val isCurrentMonth: Boolean,
    /** Bills due in [month], sorted by [sort]. */
    val dues: List<BillDue>,
    /** Earlier bills that still aren't fully paid (shown for the current month only). */
    val carriedOver: List<BillDue>,
    val sort: BillSort,
    val currency: String,
    /** Every bill, for the "All bills" list. */
    val bills: List<BillRow>,
) {
    val totalCents: Long = dues.sumOf { it.amountCents }
    val paidCents: Long = dues.sumOf { it.paidCents }
    val remainingCents: Long = dues.sumOf { it.remainingCents }
    val carriedOverCents: Long = carriedOver.sumOf { it.remainingCents }
}

class BillsViewModel(private val container: AppContainer) : ViewModel() {
    private val billDao = container.db.billDao()
    private val expenseDao = container.db.expenseDao()
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<BillsState?> = combine(
        container.settings.settings,
        month,
        billDao.all(),
        expenseDao.allBillPayments(),
    ) { settings, month, bills, payments ->
        val sort = BillSort.from(settings.billSort)
        val today = LocalDate.now()
        val isCurrentMonth = month == YearMonth.from(today)
        val dues = buildBillDues(bills, month.atDay(1), month.atEndOfMonth(), payments)
            .filter { it.bill.active || it.payments.isNotEmpty() }
            .sortedFor(sort)
        val carriedOver = if (isCurrentMonth) {
            buildBillDues(
                bills.filter { it.active },
                month.atDay(1).minusDays(OVERDUE_LOOKBACK_DAYS),
                month.atDay(1).minusDays(1),
                payments,
            ).filter { !it.settled }.sortedFor(sort)
        } else {
            emptyList()
        }
        val counts = settledCounts(bills, payments)
        BillsState(
            month = month,
            isCurrentMonth = isCurrentMonth,
            dues = dues,
            carriedOver = carriedOver,
            sort = sort,
            currency = settings.currency,
            bills = bills.map { BillRow(it, counts[it.id] ?: 0) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun previousMonth() { month.value = month.value.minusMonths(1) }
    fun nextMonth() { month.value = month.value.plusMonths(1) }
    fun thisMonth() { month.value = YearMonth.now() }

    fun setSort(sort: BillSort) {
        viewModelScope.launch {
            val current = container.settings.settings.first()
            container.settings.save(current.copy(billSort = sort.name))
        }
    }

    fun save(bill: Bill) {
        viewModelScope.launch { billDao.upsert(bill) }
    }

    /** Past payments stay in your expenses; only the bill itself is removed. */
    fun delete(bill: Bill) {
        viewModelScope.launch { billDao.delete(bill) }
    }

    fun payBill(due: BillDue, amountCents: Long, paidOn: LocalDate, settle: Boolean) {
        viewModelScope.launch { expenseDao.upsert(due.payment(amountCents, paidOn, settle)) }
    }

    fun removePayment(payment: Expense) {
        viewModelScope.launch { expenseDao.delete(payment) }
    }
}
