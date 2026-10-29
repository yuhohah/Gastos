package com.example.gastos

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.gastos.data.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class ExpenseImportUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun previewsImportsAndSkipsDuplicateTransactions() {
        val activity = compose.activity
        val id = UUID.randomUUID().toString()
        val file = File(activity.cacheDir, "import-ui-$id.csv")
        file.writeText("Data;Grupo;Tipo;Saida (R$);Descricao original;ID;Origem\n24/08/2026;Teste Fuel;Pix enviado;137,40;Test station;test-$id-a;Manual\n02/10/2026;Teste Zig;Compra no débito;70,00;Test venue;test-$id-b;Extrato OFX\n")
        val dao = AppDatabase.getDatabase(activity).expenseDao()
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", file)
        lateinit var model: ExpenseViewModel
        compose.runOnUiThread { model = ViewModelProvider(activity)[ExpenseViewModel::class.java]; model.prepareImport(uri) }
        try {
            compose.waitUntil(10_000) { model.pendingImport.value != null && !model.busy.value }
            compose.onNodeWithText("Revisar importação").assertIsDisplayed()
            compose.onNodeWithText("2 lançamentos · 2 grupos").assertIsDisplayed()
            compose.onNodeWithText("Importar lançamentos").performClick()
            compose.waitUntil(10_000) { model.pendingImport.value == null && !model.busy.value }
            assertEquals(2, runBlocking { dao.getAllExpenses().first().count { it.importKey?.startsWith("test-$id-") == true } })
            compose.runOnUiThread { model.prepareImport(uri) }
            compose.waitUntil(10_000) { model.pendingImport.value != null && !model.busy.value }
            compose.onNodeWithText("Importar lançamentos").performClick()
            compose.waitUntil(10_000) { model.pendingImport.value == null && !model.busy.value }
            assertEquals(2, runBlocking { dao.getAllExpenses().first().count { it.importKey?.startsWith("test-$id-") == true } })
            compose.onNodeWithText("Buscar grupo, loja ou pagamento").assertIsDisplayed()
        } finally {
            runBlocking { dao.getAllExpenses().first().filter { it.importKey?.startsWith("test-$id-") == true }.forEach { dao.deleteExpense(it) } }
            file.delete()
        }
    }
}
