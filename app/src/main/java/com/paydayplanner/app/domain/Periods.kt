package com.paydayplanner.app.domain

import com.paydayplanner.app.data.Bill
import com.paydayplanner.app.data.Expense
import com.paydayplanner.app.data.Settings
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.min

/** How far back bills that aren't fully paid are still flagged as needing payment. */
const val OVERDUE_LOOKBACK_DAYS = 366L

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

/** One occurrence of a bill, plus any payments made toward it (oldest first). */
data class BillDue(val bill: Bill, val dueDate: LocalDate, val payments: List<Expense> = emptyList()) {
    val paidCents: Long get() = payments.sumOf { it.amountCents }

    /** Fully paid: payments cover the bill amount, or one was marked as settling it. */
    val settled: Boolean
        get() = payments.isNotEmpty() && (paidCents >= bill.amountCents || payments.any { it.billSettled })

    /** Some money paid, but not all of it. */
    val partial: Boolean get() = payments.isNotEmpty() && !settled

    val remainingCents: Long get() = if (settled) 0 else (bill.amountCents - paidCents).coerceAtLeast(0)

    /** What this occurrence costs: the actual total once settled, otherwise the bill amount. */
    val amountCents: Long get() = if (settled) paidCents else bill.amountCents

    val lastPaidOn: LocalDate? get() = payments.maxOfOrNull { it.epochDay }?.let(LocalDate::ofEpochDay)

    /** "4 of 12" for bills with a fixed number of payments, otherwise null. */
    val installment: String?
        get() = bill.limitedPayments?.let { "${bill.paymentNumber(dueDate)} of $it" }

    fun isOverdue(today: LocalDate): Boolean = !settled && dueDate.isBefore(today)

    /** A payment toward this occurrence. [settle] marks it fully paid even if short of the amount. */
    fun payment(amountCents: Long, paidOn: LocalDate, settle: Boolean) = Expense(
        amountCents = amountCents,
        note = bill.name,
        category = bill.category,
        epochDay = paidOn.toEpochDay(),
        billId = bill.id,
        billDueEpochDay = dueDate.toEpochDay(),
        billSettled = settle || amountCents >= remainingCents,
    )
}

enum class BillSort(val label: String) {
    DueDate("Due date"),
    Remaining("Amount left"),
    Name("Name"),
    UnpaidFirst("Unpaid first"),
    ;

    companion object {
        fun from(name: String) = entries.firstOrNull { it.name == name } ?: DueDate
    }
}

fun List<BillDue>.sortedFor(sort: BillSort): List<BillDue> = when (sort) {
    BillSort.DueDate -> sortedWith(compareBy({ it.dueDate }, { it.bill.name.lowercase() }))
    BillSort.Remaining -> sortedWith(compareByDescending<BillDue> { it.remainingCents }.thenBy { it.dueDate })
    BillSort.Name -> sortedWith(compareBy({ it.bill.name.lowercase() }, { it.dueDate }))
    BillSort.UnpaidFirst -> sortedWith(compareBy({ it.settled }, { it.partial }, { it.dueDate }))
}

/** Number of fully paid occurrences per bill id. */
fun settledCounts(bills: List<Bill>, payments: List<Expense>): Map<Long, Int> {
    val billsById = bills.associateBy { it.id }
    return payments
        .groupBy { it.billId to it.billDueEpochDay }
        .mapNotNull { (key, list) ->
            val bill = billsById[key.first] ?: return@mapNotNull null
            val due = key.second ?: return@mapNotNull null
            bill.id.takeIf { BillDue(bill, LocalDate.ofEpochDay(due), list).settled }
        }
        .groupingBy { it }
        .eachCount()
}

/** Number of payments for a recurring bill that ends (e.g. a loan), or null if it doesn't end. */
val Bill.limitedPayments: Int? get() = totalPayments?.takeIf { recurring && it > 0 }

private val Bill.firstMonth: YearMonth get() = YearMonth.from(LocalDate.ofEpochDay(startEpochDay))

private fun Bill.dueDateIn(month: YearMonth): LocalDate = month.atDay(min(dueDay, month.lengthOfMonth()))

/** 1-based payment number of the occurrence due in [date]'s month. */
fun Bill.paymentNumber(date: LocalDate): Int =
    ChronoUnit.MONTHS.between(firstMonth, YearMonth.from(date)).toInt() + 1

/** Due date of the final payment, for bills with a fixed number of payments. */
fun Bill.lastDueDate(): LocalDate? = limitedPayments?.let { dueDateIn(firstMonth.plusMonths(it - 1L)) }

fun Bill.dueDatesBetween(from: LocalDate, to: LocalDate): List<LocalDate> {
    if (from.isAfter(to)) return emptyList()
    val start = LocalDate.ofEpochDay(startEpochDay)
    if (!recurring) return if (start.isWithin(from, to)) listOf(start) else emptyList()

    val result = mutableListOf<LocalDate>()
    var month = maxOf(YearMonth.from(from), firstMonth)
    val last = lastDueDate()?.let { minOf(YearMonth.from(it), YearMonth.from(to)) } ?: YearMonth.from(to)
    while (!month.isAfter(last)) {
        val date = dueDateIn(month)
        if (date.isWithin(from, to)) result += date
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
    val paymentsByKey = payments.groupBy { it.billId to it.billDueEpochDay }
    return bills
        .flatMap { bill ->
            bill.dueDatesBetween(from, to).map { date ->
                BillDue(bill, date, paymentsByKey[bill.id to date.toEpochDay()].orEmpty().sortedBy { it.epochDay })
            }
        }
        .sortedWith(compareBy({ it.dueDate }, { it.bill.name }))
}

private fun LocalDate.isWithin(from: LocalDate, to: LocalDate) = !isBefore(from) && !isAfter(to)
