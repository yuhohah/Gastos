package com.example.gastos.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExpenseRepositoryTest {
    @Test fun trimsDestinationNamesAndDeduplicatesSelectedIds() = runBlocking {
        val dao = RecordingDao()
        val repository = ExpenseRepository(dao)
        assertEquals(2, repository.moveExpenses(listOf(1, 2, 1), "  LAZER  "))
        assertEquals(listOf(1L, 2L), dao.movedIds)
        assertEquals("LAZER", dao.destination)
        repository.renameGroup("Zig", "  Diversão  ")
        assertEquals("Zig", dao.oldName)
        assertEquals("Diversão", dao.destination)
    }

    @Test fun rejectsEmptyNamesAndInvalidSelectionsBeforeWriting() = runBlocking {
        val dao = RecordingDao()
        val repository = ExpenseRepository(dao)
        for (ids in listOf(emptyList(), listOf(0L), listOf(-1L))) {
            try { repository.moveExpenses(ids, "LAZER"); fail("Invalid selection") }
            catch (_: IllegalArgumentException) { }
        }
        try { repository.moveExpenses(listOf(1), "  "); fail("Blank group") }
        catch (_: IllegalArgumentException) { }
        try { repository.renameGroup("Zig", "\n "); fail("Blank group") }
        catch (_: IllegalArgumentException) { }
        assertNull(dao.destination)
        assertNull(dao.movedIds)
    }

    private class RecordingDao : ExpenseDao {
        var movedIds: List<Long>? = null
        var destination: String? = null
        var oldName: String? = null
        override suspend fun moveExpenses(ids: List<Long>, groupName: String): Int {
            movedIds = ids; destination = groupName; return ids.size
        }
        override suspend fun renameGroup(oldName: String, newName: String): Int {
            this.oldName = oldName; destination = newName; return 1
        }
        override fun getAllExpenses(): Flow<List<Expense>> = flowOf(emptyList())
        override fun getExpensesByCategory(category: String): Flow<List<Expense>> = flowOf(emptyList())
        override fun getCategories(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun insertExpense(expense: Expense) = Unit
        override suspend fun insertImportedExpenses(expenses: List<Expense>): List<Long> = emptyList()
        override suspend fun deleteAllExpenses() = Unit
        override suspend fun deleteExpense(expense: Expense) = Unit
        override suspend fun moveExpenseBatch(ids: List<Long>, groupName: String): Int = error("Unexpected batch call")
    }
}
