package com.example.gastos.data

import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Calendar

class ExpenseCsvTest {
    private val header = "Data;Grupo;Tipo;Saida (R$);Descricao original;ID;Origem\n"
    private fun row(id: String = "manual-test", amount: String = "137,40", date: String = "24/08/2026") =
        "$date;COMBUSTIVEL;Pix enviado;$amount;Posto de teste;$id;Manual\n"

    @Test fun importsChatGroupsDatesPaymentAndOrigin() {
        val result = ExpenseCsv.parse("\uFEFF" + header + row())
        val expense = result.expenses.single()
        assertEquals("COMBUSTIVEL", expense.category)
        assertEquals(13740L, result.totalCents)
        assertEquals("Pix", expense.paymentMethod)
        assertEquals("Manual", expense.origin)
        assertEquals("manual-test", expense.importKey)
        val day = Calendar.getInstance().apply { timeInMillis = expense.date }
        assertEquals(24, day.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.AUGUST, day.get(Calendar.MONTH))
        assertEquals(2026, day.get(Calendar.YEAR))
    }

    @Test fun acceptsQuotedSeparatorsNewlinesAndEscapedQuotes() {
        val text = header + "24/08/2026;Zig;Compra no débito;70,00;\"Loja; \"\"Centro\"\"\nsegunda linha\";test-2;Extrato OFX\r\n"
        val expense = ExpenseCsv.parse(text).expenses.single()
        assertEquals("Loja; \"Centro\"\nsegunda linha", expense.description)
        assertEquals("Débito", expense.paymentMethod)
    }

    @Test fun importsOldCommaSeparatedAppExports() {
        val result = ExpenseCsv.parse("ID,Categoria,Valor,Descricao,Data\n1,Transporte,75.06,Posto,02/10/2026 12:00\n")
        assertEquals(7506L, result.totalCents)
        assertEquals("Não informado", result.expenses.single().paymentMethod)
    }

    @Test fun preservesLegitimateIdenticalLegacyTransactions() {
        val text = "ID,Categoria,Valor,Descricao,Data\n1,Loja,10.00,Cafe,01/08/2026 12:00\n2,Loja,10.00,Cafe,01/08/2026 12:00\n"
        val first = ExpenseCsv.parse(text)
        assertEquals(2, first.expenses.size)
        assertNotEquals(first.expenses[0].importKey, first.expenses[1].importKey)
        assertEquals(first, ExpenseCsv.parse(text))
    }

    @Test fun skipsRepeatedSourceIdsButRejectsConflictingIds() {
        val result = ExpenseCsv.parse(header + row() + row())
        assertEquals(1, result.expenses.size)
        assertEquals(1, result.repeatedRows)
        rejects(header + row() + row(amount = "200,00"), "dados diferentes")
    }

    @Test fun rejectsInvalidAmountsWithoutInventingZero() {
        for (amount in listOf("", "abc", "0", "-1,00", "NaN", "1,234", "1.234", "12.34,56")) {
            rejects(header + row(amount = amount), "Linha 2")
        }
    }

    @Test fun parsesBrazilianThousandsAndAddsInCents() {
        assertEquals(123456L, ExpenseCsv.parseAmount("R$ 1.234,56"))
        val result = ExpenseCsv.parse(header + row("a", "0,10") + row("b", "0,20"))
        assertEquals(30L, result.totalCents)
    }

    @Test fun rejectsInvalidDatesAndDoesNotPartiallyImport() {
        for (date in listOf("31/02/2026", "", "24/08/2026suffix", "2026-08-24")) {
            rejects(header + row("valid") + row("invalid", date = date), "Linha 3")
        }
    }

    @Test fun rejectsGroupSummaryInsteadOfLosingTransactionDates() {
        rejects("Destinatario ou estabelecimento;Quantidade;Total (R$)\nZig;8;1001,04\n", "transações detalhadas")
    }

    @Test fun rejectsMalformedQuotesColumnsAndEmptyFiles() {
        rejects(header + "\"unfinished", "aspas")
        rejects(header + "24/08/2026;Zig\n", "colunas")
        rejects(header, "vazio")
    }

    @Test fun exportRoundTripPreservesIdentityAndMetadata() {
        val first = ExpenseCsv.parse(header + row()).expenses
        val restored = ExpenseCsv.parse(ExpenseCsv.export(first)).expenses
        assertEquals(first, restored)
    }

    @Test fun validatesCurrentChatFileWhenProvided() {
        val path = System.getenv("GASTOS_IMPORT_FIXTURE")
        assumeTrue("Optional private fixture is supplied locally, never bundled with the app", !path.isNullOrBlank())
        val result = ExpenseCsv.parse(File(path!!).readText())
        assertEquals(228, result.expenses.size)
        assertEquals(95, result.expenses.map { it.category }.distinct().size)
        assertEquals(1145146L, result.totalCents)
        assertEquals(719022L, result.expenses.filter { it.paymentMethod == "Pix" }.sumOf { it.amountCents })
        assertEquals(118896L, result.expenses.filter { it.category == "COMBUSTIVEL" }.sumOf { it.amountCents })
        assertEquals(100104L, result.expenses.filter { it.category == "Zig" }.sumOf { it.amountCents })
        assertEquals(3, result.expenses.count { it.origin == "Manual" })
    }

    private fun rejects(text: String, message: String) {
        try { ExpenseCsv.parse(text); fail("Invalid CSV should have been rejected") }
        catch (error: IllegalArgumentException) { assertTrue(error.message.orEmpty(), error.message.orEmpty().contains(message, ignoreCase = true)) }
    }
}
