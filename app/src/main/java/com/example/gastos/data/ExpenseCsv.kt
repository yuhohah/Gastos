package com.example.gastos.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.security.MessageDigest
import java.text.Normalizer
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExpenseImport(val expenses: List<Expense>, val repeatedRows: Int = 0) {
    val totalCents: Long get() = expenses.sumOf { it.amountCents }
}

val Expense.amountCents: Long
    get() = BigDecimal.valueOf(amount).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()

/** CSV boundary shared by the document picker and export; never guesses missing amounts or dates. */
object ExpenseCsv {
    const val MAX_CHARACTERS = 5_000_000

    fun parse(text: String): ExpenseImport {
        require(text.length <= MAX_CHARACTERS) { "Arquivo muito grande. O limite é de 5 milhões de caracteres." }
        val clean = text.removePrefix("\uFEFF")
        val firstLine = clean.lineSequence().firstOrNull().orEmpty()
        val separator = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val records = readRecords(clean, separator).filterNot { row -> row.all { it.isBlank() } }
        require(records.size > 1) { "O CSV está vazio ou não contém lançamentos." }
        val headers = records.first().map(::normalize)
        val detailed = listOf("data", "grupo", "saida (r$)", "descricao original", "id").all { it in headers }
        val legacy = listOf("categoria", "valor", "descricao", "data").all { it in headers }
        require(detailed || legacy) {
            "Use o CSV de transações detalhadas do chat ou um CSV exportado pelo Gastos. A tabela de totais por grupo não contém as datas de cada lançamento."
        }
        require(headers.distinct().size == headers.size) { "O CSV contém colunas repetidas." }
        val unique = linkedMapOf<String, Expense>()
        val unkeyedOccurrences = mutableMapOf<String, Int>()
        var repeated = 0
        records.drop(1).forEachIndexed { index, cells ->
            val line = index + 2
            require(cells.size == headers.size) { "Linha $line: quantidade de colunas inválida." }
            fun field(name: String): String = headers.indexOf(name).let { if (it < 0) "" else cells[it].trim() }
            try {
                val category = field(if (detailed) "grupo" else "categoria")
                require(category.isNotBlank()) { "informe o grupo ou categoria" }
                val amount = parseAmount(field(if (detailed) "saida (r$)" else "valor"))
                val date = parseDate(field("data"))
                val description = field(if (detailed) "descricao original" else "descricao")
                val method = paymentMethod(field(if (detailed) "tipo" else "forma de pagamento"))
                val origin = field("origem").ifBlank { "CSV" }
                val suppliedKey = field(if (detailed) "id" else "id externo")
                // Legacy numeric database IDs are not stable across installations.
                val key = if (suppliedKey.isNotBlank()) suppliedKey else {
                    val canonical = listOf(category, amount.toString(), description, date.toString(), method).joinToString("\u001F")
                    val hash = "csv:" + MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
                        .joinToString("") { "%02x".format(it) }
                    val occurrence = (unkeyedOccurrences[hash] ?: 0) + 1
                    unkeyedOccurrences[hash] = occurrence
                    "$hash:$occurrence"
                }
                val expense = Expense(category = category, amount = amount / 100.0,
                    description = description, date = date, paymentMethod = method, origin = origin, importKey = key)
                val existing = unique[key]
                require(existing == null || existing == expense) { "o mesmo ID aparece com dados diferentes" }
                if (existing == null) unique[key] = expense else repeated++
            } catch (error: IllegalArgumentException) {
                throw IllegalArgumentException("Linha $line: ${error.message}.", error)
            } catch (error: ArithmeticException) {
                throw IllegalArgumentException("Linha $line: valor fora do limite permitido.", error)
            }
        }
        require(unique.isNotEmpty()) { "Nenhum lançamento encontrado." }
        require(unique.size <= 50_000) { "Importe no máximo 50 mil lançamentos por arquivo." }
        return ExpenseImport(unique.values.toList(), repeated)
    }

    fun parseAmount(raw: String): Long {
        val value = raw.trim().removePrefix("R$").trim()
        val normalized = if (',' in value) {
            require(Regex("(?:\\d+|\\d{1,3}(?:\\.\\d{3})+),\\d{1,2}").matches(value)) { "valor monetário inválido" }
            value.replace(".", "").replace(',', '.')
        } else {
            require(Regex("\\d+(?:\\.\\d{1,2})?").matches(value)) { "valor monetário inválido" }
            value
        }
        val cents = normalized.toBigDecimal().movePointRight(2).longValueExact()
        require(cents in 1..999_999_999_999L) { "o valor deve ser positivo e ter no máximo duas casas decimais" }
        return cents
    }

    private fun parseDate(raw: String): Long {
        val pattern = when {
            Regex("\\d{2}/\\d{2}/\\d{4}").matches(raw) -> "dd/MM/yyyy"
            Regex("\\d{2}/\\d{2}/\\d{4} \\d{2}:\\d{2}").matches(raw) -> "dd/MM/yyyy HH:mm"
            else -> throw IllegalArgumentException("data inválida; use dd/MM/aaaa")
        }
        val format = SimpleDateFormat(pattern, Locale.ROOT).apply { isLenient = false }
        val position = ParsePosition(0)
        val date = format.parse(raw, position)
        require(date != null && position.index == raw.length) { "data inválida; use dd/MM/aaaa" }
        return date.time
    }

    private fun paymentMethod(raw: String): String = when {
        normalize(raw).contains("pix") -> "Pix"
        normalize(raw).contains("nupay") -> "NuPay"
        normalize(raw).contains("debito") -> "Débito"
        normalize(raw).contains("credito") -> "Crédito"
        normalize(raw).contains("dinheiro") -> "Dinheiro"
        raw.isBlank() -> "Não informado"
        else -> raw
    }

    fun export(expenses: List<Expense>): String {
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT)
        val headers = listOf("ID", "Categoria", "Valor", "Descricao", "Data", "Forma de pagamento", "Origem", "ID externo")
        val rows = expenses.map { expense ->
            listOf(expense.id.toString(), expense.category,
                BigDecimal.valueOf(expense.amountCents, 2).toPlainString().replace('.', ','),
                expense.description, format.format(Date(expense.date)), expense.paymentMethod,
                expense.origin, expense.importKey.orEmpty())
        }
        return "\uFEFF" + (listOf(headers) + rows).joinToString("\r\n", postfix = "\r\n") { row ->
            row.joinToString(";") { "\"${it.replace("\"", "\"\"")}\"" }
        }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").trim().lowercase(Locale.ROOT)

    private fun readRecords(text: String, separator: Char): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var closedQuote = false
        var i = 0
        fun endField() { row.add(field.toString()); field.setLength(0); closedQuote = false }
        while (i < text.length) {
            val c = text[i]
            when {
                quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                quoted && c == '"' -> { quoted = false; closedQuote = true }
                quoted -> field.append(c)
                c == '"' -> { require(field.isEmpty() && !closedQuote) { "Aspas inválidas no CSV." }; quoted = true }
                c == separator -> endField()
                c == '\n' || c == '\r' -> {
                    endField(); rows.add(row.toList()); row.clear()
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                }
                else -> { require(!closedQuote) { "Texto inválido após aspas no CSV." }; field.append(c) }
            }
            i++
        }
        require(!quoted) { "O CSV contém aspas não fechadas." }
        if (field.isNotEmpty() || row.isNotEmpty() || closedQuote) { endField(); rows.add(row.toList()) }
        return rows
    }
}
