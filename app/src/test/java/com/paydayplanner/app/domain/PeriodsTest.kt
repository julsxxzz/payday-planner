package com.paydayplanner.app.domain

import com.paydayplanner.app.data.Bill
import com.paydayplanner.app.data.Expense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun paymentIsMatchedToItsOccurrence() {
        val loan = monthly("2026-10-05", totalPayments = 12)
        val payment = Expense(
            id = 7, amountCents = 500_000, note = "Loan", category = "Loans",
            epochDay = d("2026-10-03").toEpochDay(), billId = 1, billDueEpochDay = d("2026-10-05").toEpochDay(),
        )
        val dues = buildBillDues(listOf(loan), d("2026-10-01"), d("2026-11-30"), listOf(payment))
        assertEquals(listOf(true, false), dues.map { it.paid })
    }
}
