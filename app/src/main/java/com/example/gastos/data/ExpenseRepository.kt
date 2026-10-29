package com.example.gastos.data

import kotlinx.coroutines.flow.Flow

class ExpenseRepository(private val expenseDao: ExpenseDao) {
    fun getAllExpenses(): Flow<List<Expense>> = expenseDao.getAllExpenses()
    
    fun getExpensesByCategory(category: String): Flow<List<Expense>> = 
        expenseDao.getExpensesByCategory(category)
        
    fun getCategories(): Flow<List<String>> = expenseDao.getCategories()

    suspend fun insertExpense(expense: Expense) = expenseDao.insertExpense(expense)

    suspend fun deleteExpense(expense: Expense) = expenseDao.deleteExpense(expense)

    suspend fun importExpenses(expenses: List<Expense>): Int = expenseDao.importExpenses(expenses)

    suspend fun deleteAllExpenses() = expenseDao.deleteAllExpenses()

    suspend fun renameGroup(oldName: String, newName: String): Int {
        require(oldName.isNotBlank()) { "Selecione um grupo válido." }
        require(newName.isNotBlank()) { "Informe o nome do grupo." }
        return expenseDao.renameGroup(oldName, newName.trim())
    }

    suspend fun moveExpenses(ids: List<Long>, groupName: String): Int {
        require(ids.isNotEmpty() && ids.all { it > 0 }) { "Selecione pelo menos um gasto." }
        require(groupName.isNotBlank()) { "Informe o nome do grupo." }
        return expenseDao.moveExpenses(ids.distinct(), groupName.trim())
    }
}
