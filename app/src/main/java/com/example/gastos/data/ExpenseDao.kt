package com.example.gastos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<Expense>>

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY date DESC")
    fun getExpensesByCategory(category: String): Flow<List<Expense>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: Expense)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertImportedExpenses(expenses: List<Expense>): List<Long>

    @Transaction
    suspend fun importExpenses(expenses: List<Expense>): Int =
        insertImportedExpenses(expenses).count { it != -1L }

    @Query("DELETE FROM expenses")
    suspend fun deleteAllExpenses()

    @Query("UPDATE expenses SET category = :newName WHERE category = :oldName")
    suspend fun renameGroup(oldName: String, newName: String): Int

    @Query("UPDATE expenses SET category = :groupName WHERE id IN (:ids)")
    suspend fun moveExpenseBatch(ids: List<Long>, groupName: String): Int

    @Transaction
    suspend fun moveExpenses(ids: List<Long>, groupName: String): Int {
        var updated = 0
        // Stay below SQLite's bind limit, including the destination parameter.
        ids.distinct().chunked(900).forEach { updated += moveExpenseBatch(it, groupName) }
        return updated
    }

    @Delete
    suspend fun deleteExpense(expense: Expense)

    @Query("SELECT DISTINCT category FROM expenses")
    fun getCategories(): Flow<List<String>>
}
