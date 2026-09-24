package com.paydayplanner.app.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay DESC, id DESC")
    fun between(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE billId IS NOT NULL AND billDueEpochDay BETWEEN :from AND :to")
    fun billPaymentsDueBetween(from: Long, to: Long): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE billId IS NOT NULL AND billDueEpochDay BETWEEN :from AND :to")
    suspend fun billPaymentsDueBetweenOnce(from: Long, to: Long): List<Expense>

    @Query("SELECT billId, COUNT(*) AS paid FROM expenses WHERE billId IS NOT NULL GROUP BY billId")
    fun paidCountsPerBill(): Flow<List<BillPaidCount>>

    @Upsert
    suspend fun upsert(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY active DESC, name COLLATE NOCASE")
    fun all(): Flow<List<Bill>>

    @Query("SELECT * FROM bills WHERE active = 1")
    suspend fun activeOnce(): List<Bill>

    @Upsert
    suspend fun upsert(bill: Bill)

    @Delete
    suspend fun delete(bill: Bill)
}

@Dao
interface PeriodBudgetDao {
    @Query("SELECT * FROM period_budgets WHERE startEpochDay = :start")
    fun get(start: Long): Flow<PeriodBudget?>

    @Upsert
    suspend fun upsert(budget: PeriodBudget)

    @Query("DELETE FROM period_budgets WHERE startEpochDay = :start")
    suspend fun clear(start: Long)
}

data class BillPaidCount(val billId: Long, val paid: Int)

@Database(entities = [Expense::class, Bill::class, PeriodBudget::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun billDao(): BillDao
    abstract fun periodBudgetDao(): PeriodBudgetDao

    companion object {
        /** v2: optional number of payments for recurring bills (loans, installments). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bills ADD COLUMN totalPayments INTEGER")
            }
        }
    }
}
