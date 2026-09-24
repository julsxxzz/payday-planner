package com.paydayplanner.app.domain

import com.paydayplanner.app.data.Bill
import com.paydayplanner.app.data.Expense
import com.paydayplanner.app.data.Settings
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min

/** How far back unpaid bills are still shown as overdue. */
const val OVERDUE_LOOKBACK_DAYS = 120L

/** A pay period: from one payday up to the day before the next payday. */
data class Period(val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)
}

object Periods {
    /** The payday dates in a month. A day past the month's end falls on its last day. */
    private fun paydaysIn(month: YearMonth, day1: Int, day2: Int): List<LocalDate> =
        listOf(day1, day2).map { month.atDay(min(it, month.lengthOfMonth())) }

    fun periodFor(date: LocalDate, day1: Int, day2: Int): Period {
        val month = YearMonth.from(date)
        val paydays = listOf(month.minusMonths(1), month, month.plusMonths(1))
            .flatMap { paydaysIn(it, day1, day2) }
            .distinct()
            .sorted()
        val start = paydays.last { !it.isAfter(date) }
        val nextPayday = paydays.first { it.isAfter(date) }
        return Period(start, nextPayday.minusDays(1))
    }

    /** Income for the period, based on which payday it starts on. */
    fun defaultIncome(period: Period, s: Settings): Long {
        val month = YearMonth.from(period.start)
        val payday1 = month.atDay(min(s.payday1, month.lengthOfMonth()))
        return if (period.start == payday1) s.income1 else s.income2
    }
}

/** One occurrence of a bill, plus its payment if it has been paid. */
data class BillDue(val bill: Bill, val dueDate: LocalDate, val payment: Expense?) {
    val paid: Boolean get() = payment != null
    val amountCents: Long get() = payment?.amountCents ?: bill.amountCents
}

fun Bill.dueDatesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
    if (from.isAfter(to)) return emptyList()
    val start = LocalDate.ofEpochDay(startEpochDay)
    if (!recurring) return if (start.isWithin(from, to)) listOf(start) else emptyList()

    val result = mutableListOf<LocalDate>()
    var month = YearMonth.from(from)
    val last = YearMonth.from(to)
    val firstMonth = YearMonth.from(start)
    while (!month.isAfter(last)) {
        val date = month.atDay(min(dueDay, month.lengthOfMonth()))
        if (date.isWithin(from, to) && !month.isBefore(firstMonth)) result += date
        month = month.plusMonths(1)
    }
    return result
}

fun buildBillDues(
    bills: List<Bill>,
    from: LocalDate,
    to: LocalDate,
    payments: List<Expense>,
): List<BillDue> {
    val paymentByKey = payments.associateBy { it.billId to it.billDueEpochDay }
    return bills
        .flatMap { bill ->
            bill.dueDatesBetween(from, to).map { date ->
                BillDue(bill, date, paymentByKey[bill.id to date.toEpochDay()])
            }
        }
        .sortedWith(compareBy({ it.dueDate }, { it.bill.name }))
}

private fun LocalDate.isWithin(from: LocalDate, to: LocalDate) = !isBefore(from) && !isAfter(to)
