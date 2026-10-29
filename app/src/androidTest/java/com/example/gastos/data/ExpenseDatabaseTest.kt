package com.example.gastos.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ExpenseDatabaseTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun migrationPreservesExistingExpenses() = runBlocking {
        val name = "migration-test-${UUID.randomUUID()}"
        val path = context.getDatabasePath(name)
        path.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, date INTEGER NOT NULL)")
            db.execSQL("INSERT INTO expenses VALUES (1, 'Existing group', 123.45, 'Keep this expense', 1787526000000)")
            db.version = 1
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            val expense = db.expenseDao().getAllExpenses().first().single()
            assertEquals(1L, expense.id)
            assertEquals("Existing group", expense.category)
            assertEquals(12345L, expense.amountCents)
            assertEquals("Não informado", expense.paymentMethod)
            assertNull(expense.importKey)
            val batch = ExpenseCsv.parse("Data;Grupo;Tipo;Saida (R$);Descricao original;ID;Origem\n24/08/2026;Fuel;Pix enviado;137,40;Test station;source-1;Manual\n")
            assertEquals(1, db.expenseDao().importExpenses(batch.expenses))
            assertEquals(0, db.expenseDao().importExpenses(batch.expenses))
            assertEquals(2, db.expenseDao().getAllExpenses().first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun failedBatchRollsBackAllInsertedRows() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_test_expense BEFORE INSERT ON expenses WHEN NEW.category = 'Reject' BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try {
                db.expenseDao().importExpenses(listOf(
                    Expense(category = "Valid", amount = 10.0, description = "", importKey = "first"),
                    Expense(category = "Reject", amount = 20.0, description = "", importKey = "second")
                ))
                fail("The test trigger should reject the second expense")
            } catch (_: android.database.sqlite.SQLiteException) {
                assertTrue(db.expenseDao().getAllExpenses().first().isEmpty())
            }
        } finally { db.close() }
    }

    @Test fun movesOnlySelectedExpensesAndPreservesImportIdentity() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.expenseDao()
            val source = listOf(
                Expense(category = "Zig", amount = 70.0, description = "First venue", date = 1000, paymentMethod = "Pix", importKey = "a"),
                Expense(category = "Food", amount = 20.0, description = "Lunch", date = 2000, paymentMethod = "Débito", importKey = "b"),
                Expense(category = "Zig", amount = 30.0, description = "Keep here", date = 3000, importKey = "c")
            )
            dao.importExpenses(source)
            val before = dao.getAllExpenses().first()
            val selected = before.filter { it.importKey in listOf("a", "b") }
            assertEquals(2, ExpenseRepository(dao).moveExpenses(selected.map { it.id }, "  LAZER  "))
            val after = dao.getAllExpenses().first()
            assertEquals(before.sumOf { it.amountCents }, after.sumOf { it.amountCents })
            before.forEach { original ->
                val updated = after.single { it.id == original.id }
                assertEquals(original.copy(category = if (original in selected) "LAZER" else original.category), updated)
            }
            assertEquals(0, dao.importExpenses(source))
            assertEquals(2, dao.getAllExpenses().first().count { it.category == "LAZER" })
        } finally { db.close() }
    }

    @Test fun renamingIntoExistingGroupMergesEveryMonthWithoutDeletingExpenses() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.expenseDao()
            dao.importExpenses(listOf(
                Expense(category = "Zig", amount = 10.0, description = "June", date = 1780272000000, importKey = "a"),
                Expense(category = "Zig", amount = 20.0, description = "October", date = 1790899200000, importKey = "b"),
                Expense(category = "LAZER", amount = 30.0, description = "Existing", importKey = "c"),
                Expense(category = "Fuel", amount = 40.0, description = "Unrelated", importKey = "d")
            ))
            val before = dao.getAllExpenses().first()
            assertEquals(2, ExpenseRepository(dao).renameGroup("Zig", " LAZER "))
            val after = dao.getAllExpenses().first()
            assertEquals(before.size, after.size)
            assertEquals(3, after.count { it.category == "LAZER" })
            before.forEach { original ->
                assertEquals(original.copy(category = if (original.category == "Zig") "LAZER" else original.category), after.single { it.id == original.id })
            }
        } finally { db.close() }
    }

    @Test fun largeMoveRollsBackEarlierChunksWhenLaterUpdateFails() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.expenseDao()
            dao.importExpenses((1..905).map { Expense(category = "Original", amount = 1.0, description = "", importKey = "batch-$it") })
            val ids = dao.getAllExpenses().first().map { it.id }.sorted()
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_late_move BEFORE UPDATE ON expenses WHEN NEW.id = ${ids.last()} BEGIN SELECT RAISE(ABORT, 'test failure'); END")
            try {
                dao.moveExpenses(ids, "LAZER")
                fail("The second update chunk should fail")
            } catch (_: android.database.sqlite.SQLiteException) {
                assertEquals(905, dao.getAllExpenses().first().count { it.category == "Original" })
            }
        } finally { db.close() }
    }
}
