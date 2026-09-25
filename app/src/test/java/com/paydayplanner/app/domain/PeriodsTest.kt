package com.paydayplanner.app.domain

import com.paydayplanner.app.data.Bill
import com.paydayplanner.app.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodsTest {
    private fun d(s: String) = LocalDate.parse(s)

    private fun monthly(first: String, totalPayments: Int? = null) = Bill(
        id = 1,
        name = "Loan",
        amountCents = 500_000,
        category = "Loans",
        recurring = true,
        dueDay = d(first).dayOfMonth,
        startEpochDay = d(first).toEpochDay(),
        totalPayments = totalPayments,
    )

    @Test
    fun periodsSplitOnCustomPaydays() {
        assertEquals(Period(d("2026-09-10"), d("2026-09-24")), Periods.periodFor(d("2026-09-20"), 10, 25))
        assertEquals(Period(d("2026-09-25"), d("2026-10-09")), Periods.periodFor(d("2026-10-01"), 10, 25))
    }

    @Test
    fun paydayPastMonthEndFallsOnLastDay() {
        // 30th payday in February 2026 falls on the 28th.
        assertEquals(Period(d("2026-02-15"), d("2026-02-27")), Periods.periodFor(d("2026-02-20"), 15, 30))
        assertEquals(Period(d("2026-02-28"), d("2026-03-14")), Periods.periodFor(d("2026-03-01"), 15, 30))
    }

    @Test
    fun recurringBillWithoutEndKeepsGoing() {
        val dates = monthly("2026-10-05").dueDatesBetween(d("2026-01-01"), d("2027-12-31"))
        assertEquals(15, dates.size)
        assertEquals(d("2026-10-05"), dates.first())
        assertNull(monthly("2026-10-05").lastDueDate())
    }

    @Test
    fun loanStopsAfterTotalPayments() {
        val loan = monthly("2026-10-05", totalPayments = 12)
        val dates = loan.dueDatesBetween(d("2026-01-01"), d("2030-01-01"))
        assertEquals(12, dates.size)
        assertEquals(d("2026-10-05"), dates.first())
        assertEquals(d("2027-09-05"), dates.last())
        assertEquals(d("2027-09-05"), loan.lastDueDate())
        assertEquals(emptyList<LocalDate>(), loan.dueDatesBetween(d("2027-09-06"), d("2028-12-31")))
    }

    @Test
    fun loanDueDayClampsInShortMonths() {
        val loan = monthly("2026-12-31", totalPayments = 3)
        assertEquals(
            listOf(d("2026-12-31"), d("2027-01-31"), d("2027-02-28")),
            loan.dueDatesBetween(d("2026-01-01"), d("2028-01-01")),
        )
    }

    @Test
    fun installmentLabelCountsFromFirstPayment() {
        val loan = monthly("2026-10-05", totalPayments = 12)
        val dues = buildBillDues(listOf(loan), d("2027-01-01"), d("2027-01-31"), emptyList())
        assertEquals("4 of 12", dues.single().installment)
        assertNull(buildBillDues(listOf(monthly("2026-10-05")), d("2027-01-01"), d("2027-01-31"), emptyList()).single().installment)
    }

    private fun pay(due: String, amount: Long, on: String = due, settled: Boolean = false, id: Long = 0) = Expense(
        id = id, amountCents = amount, note = "Loan", category = "Loans",
        epochDay = d(on).toEpochDay(), billId = 1, billDueEpochDay = d(due).toEpochDay(), billSettled = settled,
    )

    @Test
    fun paymentIsMatchedToItsOccurrence() {
        val loan = monthly("2026-10-05", totalPayments = 12)
        val dues = buildBillDues(listOf(loan), d("2026-10-01"), d("2026-11-30"), listOf(pay("2026-10-05", 500_000)))
        assertEquals(listOf(true, false), dues.map { it.settled })
    }

    @Test
    fun partialPaymentsLeaveRemainder() {
        val bill = monthly("2026-10-05")
        val half = pay("2026-10-05", 250_000, on = "2026-09-25", id = 1)
        val due = buildBillDues(listOf(bill), d("2026-10-01"), d("2026-10-31"), listOf(half)).single()
        assertTrue(due.partial)
        assertFalse(due.settled)
        assertEquals(250_000, due.paidCents)
        assertEquals(250_000, due.remainingCents)
        assertTrue(due.isOverdue(d("2026-10-06")))

        // Second half settles it automatically.
        val paidOff = due.copy(payments = listOf(half, pay("2026-10-05", 250_000, id = 2)))
        assertTrue(paidOff.settled)
        assertEquals(0, paidOff.remainingCents)
        assertFalse(paidOff.isOverdue(d("2026-10-06")))
    }

    @Test
    fun newPaymentSettlesWhenItCoversTheRemainder() {
        val due = BillDue(monthly("2026-10-05"), d("2026-10-05"), listOf(pay("2026-10-05", 300_000)))
        assertFalse(due.payment(100_000, d("2026-10-05"), settle = false).billSettled)
        assertTrue(due.payment(200_000, d("2026-10-05"), settle = false).billSettled)
        assertTrue(due.payment(50_000, d("2026-10-05"), settle = true).billSettled)
    }

    @Test
    fun settledFlagCountsAsPaidEvenWhenShort() {
        // e.g. electricity came out lower than the usual amount
        val due = BillDue(monthly("2026-10-05"), d("2026-10-05"), listOf(pay("2026-10-05", 420_000, settled = true)))
        assertTrue(due.settled)
        assertEquals(0, due.remainingCents)
        assertEquals(420_000, due.amountCents)
    }

    @Test
    fun sortingOptions() {
        val a = BillDue(monthly("2026-10-20").copy(id = 1, name = "Rent", amountCents = 1_000_000), d("2026-10-20"))
        val b = BillDue(monthly("2026-10-05").copy(id = 2, name = "Internet", amountCents = 150_000), d("2026-10-05"))
        val paid = BillDue(
            monthly("2026-10-01").copy(id = 3, name = "Water", amountCents = 50_000), d("2026-10-01"),
            listOf(pay("2026-10-01", 50_000).copy(billId = 3)),
        )
        val all = listOf(a, b, paid)
        assertEquals(listOf("Water", "Internet", "Rent"), all.sortedFor(BillSort.DueDate).map { it.bill.name })
        assertEquals(listOf("Rent", "Internet", "Water"), all.sortedFor(BillSort.Remaining).map { it.bill.name })
        assertEquals(listOf("Internet", "Rent", "Water"), all.sortedFor(BillSort.Name).map { it.bill.name })
        assertEquals(listOf("Internet", "Rent", "Water"), all.sortedFor(BillSort.UnpaidFirst).map { it.bill.name })
    }

    @Test
    fun settledCountsIgnorePartialOccurrences() {
        val loan = monthly("2026-10-05", totalPayments = 12)
        val payments = listOf(
            pay("2026-10-05", 500_000, id = 1),
            pay("2026-11-05", 200_000, id = 2),
            pay("2026-11-05", 300_000, id = 3),
            pay("2026-12-05", 100_000, id = 4),
        )
        assertEquals(mapOf(1L to 2), settledCounts(listOf(loan), payments))
    }
}
