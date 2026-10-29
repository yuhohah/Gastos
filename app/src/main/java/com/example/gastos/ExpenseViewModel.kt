package com.example.gastos

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.gastos.data.AppDatabase
import com.example.gastos.data.Expense
import com.example.gastos.data.ExpenseCsv
import com.example.gastos.data.ExpenseImport
import com.example.gastos.data.ExpenseRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ExpenseRepository(AppDatabase.getDatabase(application).expenseDao())
    val allExpenses = repository.getAllExpenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _pendingImport = MutableStateFlow<ExpenseImport?>(null)
    val pendingImport: StateFlow<ExpenseImport?> = _pendingImport.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun clearMessage() { _message.value = null }
    fun cancelImport() { if (!_busy.value) _pendingImport.value = null }

    fun renameGroup(oldName: String, newName: String, onSuccess: () -> Unit) {
        changeGroups({ repository.renameGroup(oldName, newName) }, "Grupo atualizado", onSuccess)
    }

    fun moveExpenses(ids: List<Long>, groupName: String, onSuccess: () -> Unit) {
        changeGroups({ repository.moveExpenses(ids, groupName) }, "Gastos movidos", onSuccess)
    }

    private fun changeGroups(action: suspend () -> Int, success: String, onSuccess: () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val count = action()
                _message.value = "$success. $count lançamentos atualizados."
                onSuccess()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = "Não foi possível alterar os grupos. Tente novamente."
            } finally { _busy.value = false }
        }
    }

    fun saveExpense(expense: Expense) {
        viewModelScope.launch {
            try {
                repository.insertExpense(expense)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = "Não foi possível salvar o lançamento."
            }
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            try { repository.deleteExpense(expense) }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = "Não foi possível excluir o lançamento."
            }
        }
    }

    fun deleteAllExpenses() {
        viewModelScope.launch {
            try { repository.deleteAllExpenses() }
            catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = "Não foi possível excluir os lançamentos."
            }
        }
    }

    fun prepareImport(uri: Uri) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _pendingImport.value = withContext(Dispatchers.IO) {
                    val context = getApplication<Application>()
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalArgumentException("Não foi possível abrir o arquivo.")
                    val text = stream.bufferedReader(Charsets.UTF_8).use { reader ->
                        val content = StringBuilder()
                        val buffer = CharArray(8192)
                        while (true) {
                            val count = reader.read(buffer)
                            if (count < 0) break
                            require(content.length + count <= ExpenseCsv.MAX_CHARACTERS) { "Arquivo muito grande." }
                            content.append(buffer, 0, count)
                        }
                        content.toString()
                    }
                    ExpenseCsv.parse(text)
                }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = error.message ?: "Não foi possível ler o CSV."
            } finally { _busy.value = false }
        }
    }

    fun confirmImport() {
        val pending = _pendingImport.value ?: return
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val inserted = repository.importExpenses(pending.expenses)
                val skipped = pending.expenses.size - inserted + pending.repeatedRows
                _pendingImport.value = null
                _message.value = "$inserted lançamentos importados. $skipped duplicados ignorados."
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _message.value = "Importação não concluída. Nenhum lançamento foi adicionado."
            } finally { _busy.value = false }
        }
    }

    suspend fun exportToCsv(context: Context): Uri? {
        val expenses = allExpenses.value
        if (expenses.isEmpty()) {
            _message.value = "Não há lançamentos para exportar."
            return null
        }
        return try {
            withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "gastos_${System.currentTimeMillis()}.csv")
                file.writeText(ExpenseCsv.export(expenses), Charsets.UTF_8)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _message.value = "Não foi possível exportar o CSV."
            null
        }
    }
}
