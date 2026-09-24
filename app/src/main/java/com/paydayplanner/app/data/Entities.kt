package com.paydayplanner.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Money you spent. If [billId] is set, this expense is the payment of a bill
 * occurrence that was due on [billDueEpochDay].
 * Amounts are stored in cents to avoid floating point rounding.
 */
@Entity(
    tableName = "expenses",
    indices = [
        Index("epochDay"),
        Index(value = ["billId", "billDueEpochDay"], unique = true),
    ],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amountCents: Long,
    val note: String,
    val category: String,
    val epochDay: Long,
    val billId: Long? = null,
    val billDueEpochDay: Long? = null,
)

/**
 * A "to be paid" bill.
 * - One-time: due on [startEpochDay].
 * - Recurring: due every month on [dueDay] (clamped to the month's last day),
 *   starting with the month of [startEpochDay].
 */
@Entity(tableName = "bills")
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val category: String,
    val recurring: Boolean,
    val dueDay: Int,
    val startEpochDay: Long,
    val active: Boolean = true,
)

/** Per-period override of income and spending cap (defaults come from Settings). */
@Entity(tableName = "period_budgets")
data class PeriodBudget(
    @PrimaryKey val startEpochDay: Long,
    val incomeCents: Long,
    val capCents: Long,
)

object Categories {
    val all = listOf(
        "Food",
        "Groceries",
        "Transport",
        "Bills & Utilities",
        "Rent / Housing",
        "Loans",
        "Shopping",
        "Health",
        "Entertainment",
        "Savings",
        "Other",
    )
}
