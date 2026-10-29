package com.example.gastos

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gastos.data.Expense
import com.example.gastos.data.ExpenseCsv
import com.example.gastos.data.amountCents
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val Brazil = Locale.forLanguageTag("pt-BR")
private fun money(cents: Long) = NumberFormat.getCurrencyInstance(Brazil).format(cents / 100.0)
private fun dateLabel(date: Long) = SimpleDateFormat("dd/MM/yyyy", Brazil).format(Date(date))
private fun monthKey(date: Long) = SimpleDateFormat("yyyy-MM", Brazil).format(Date(date))
private fun searchable(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT)
private fun transactionCount(count: Int) = "$count ${if (count == 1) "lançamento" else "lançamentos"}"
private fun groupCount(count: Int) = "$count ${if (count == 1) "grupo" else "grupos"}"
private fun transactionDescription(expense: Expense): String {
    val raw = expense.description
    return when {
        raw.startsWith("Transferência enviada pelo Pix - ") -> raw.removePrefix("Transferência enviada pelo Pix - ")
            .substringBefore(" - ").removeSuffix(" (Transferência enviada)")
        raw.startsWith("Compra no débito via NuPay - ") -> raw.removePrefix("Compra no débito via NuPay - ")
        raw.startsWith("Compra no débito - ") -> raw.removePrefix("Compra no débito - ")
        else -> raw.ifBlank { expense.category }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseScreen(viewModel: ExpenseViewModel = viewModel()) {
    val expenses by viewModel.allExpenses.collectAsState()
    val pendingImport by viewModel.pendingImport.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var search by rememberSaveable { mutableStateOf("") }
    var selectedMonth by rememberSaveable { mutableStateOf<String?>(null) }
    var grouped by rememberSaveable { mutableStateOf(true) }
    var sort by rememberSaveable { mutableStateOf("value") }
    var menu by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    var deleting by remember { mutableStateOf<Expense?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selectedIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var moveDialog by rememberSaveable { mutableStateOf(false) }
    var renamingGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedExpenses = expenses.filter { it.id in selectedIds }
    fun finishSelection() { selecting = false; selectedIds = emptyList(); moveDialog = false }
    fun toggleSelection(expense: Expense) {
        selectedIds = if (expense.id in selectedIds) selectedIds - expense.id else selectedIds + expense.id
    }
    BackHandler(enabled = selecting) { if (!busy) finishSelection() }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.prepareImport(uri)
    }
    val months = remember(expenses) { expenses.map { monthKey(it.date) }.distinct().sortedDescending() }
    LaunchedEffect(months) { selectedMonth?.let { if (it !in months) selectedMonth = null } }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }
    val filtered = remember(expenses, selectedMonth, search) {
        val query = searchable(search.trim())
        expenses.filter { expense ->
            (selectedMonth == null || monthKey(expense.date) == selectedMonth) &&
                (query.isBlank() || searchable("${expense.category} ${expense.description} ${expense.paymentMethod}").contains(query))
        }
    }
    val groups = remember(filtered, sort) {
        filtered.groupBy { it.category }.entries.let { entries ->
            if (sort == "name") entries.sortedBy { searchable(it.key) }
            else entries.sortedByDescending { entry -> entry.value.sumOf { it.amountCents } }
        }
    }
    val transactions = remember(filtered, sort) {
        when (sort) {
            "name" -> filtered.sortedBy { searchable(it.category) }
            "value" -> filtered.sortedByDescending { it.amountCents }
            else -> filtered.sortedByDescending { it.date }
        }
    }
    val total = filtered.sumOf { it.amountCents }
    fun openImport() = importPicker.launch(arrayOf("text/*", "application/csv", "application/octet-stream", "application/vnd.ms-excel"))
    fun edit(expense: Expense) { editing = expense; editor = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Gastos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Seu dinheiro, com clareza", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    if (selecting) IconButton(enabled = !busy, onClick = { finishSelection() }) { Icon(Icons.Default.Close, "Cancelar seleção") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Mais opções") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Selecionar gastos") },
                                enabled = !busy && expenses.isNotEmpty(),
                                leadingIcon = { Icon(Icons.Default.CheckBox, null) },
                                onClick = { menu = false; selecting = true; grouped = false })
                            DropdownMenuItem(text = { Text("Exportar todos os lançamentos") }, leadingIcon = { Icon(Icons.Default.FileUpload, null) }, onClick = {
                                menu = false
                                scope.launch {
                                    viewModel.exportToCsv(context)?.let { uri ->
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/csv"; putExtra(Intent.EXTRA_STREAM, uri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        try { context.startActivity(Intent.createChooser(intent, "Exportar gastos")) }
                                        catch (_: android.content.ActivityNotFoundException) { snackbar.showSnackbar("Nenhum aplicativo disponível para compartilhar.") }
                                    }
                                }
                            })
                            DropdownMenuItem(text = { Text("Excluir todos os lançamentos", color = MaterialTheme.colorScheme.error) },
                                enabled = !busy && !selecting && expenses.isNotEmpty(),
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
                                onClick = { menu = false; deleteAll = true })
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (selecting) Surface(tonalElevation = 3.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${transactionCount(selectedExpenses.size)} ${if (selectedExpenses.size == 1) "selecionado" else "selecionados"} · ${money(selectedExpenses.sumOf { it.amountCents })}",
                        style = MaterialTheme.typography.titleSmall)
                    Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && selectedExpenses.isNotEmpty(), onClick = { moveDialog = true }) {
                        Icon(Icons.Default.DriveFileMove, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp)); Text("Mover para grupo")
                    }
                }
            }
        },
        floatingActionButton = {
            if (!selecting) ExtendedFloatingActionButton(onClick = { editing = null; editor = true },
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("Novo gasto") })
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Visão geral", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Acompanhe seus gastos por destino", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalButton(enabled = !busy && !selecting, onClick = { openImport() }, contentPadding = PaddingValues(12.dp)) {
                        Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text("Importar")
                    }
                }
            }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            item {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedMonth == null, onClick = { selectedMonth = null }, label = { Text("Todos os meses") })
                    months.forEach { month ->
                        val matching = expenses.first { monthKey(it.date) == month }
                        FilterChip(selected = selectedMonth == month, onClick = { selectedMonth = month },
                            label = { Text(SimpleDateFormat("MMM yyyy", Brazil).format(Date(matching.date))) })
                    }
                }
            }
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (search.isBlank()) "TOTAL DE SAÍDAS" else "TOTAL DA BUSCA", style = MaterialTheme.typography.labelMedium)
                        Text(money(total), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text("${transactionCount(filtered.size)} · ${groupCount(groups.size)}", style = MaterialTheme.typography.bodyMedium)
                        if (filtered.isNotEmpty()) {
                            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .15f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("Pix", "Débito", "NuPay").forEach { method ->
                                    Column(Modifier.weight(1f)) {
                                        Text(method, style = MaterialTheme.typography.labelSmall)
                                        Text(money(filtered.filter { it.paymentMethod == method }.sumOf { it.amountCents }),
                                            style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    }
                                }
                            }
                            val other = filtered.filter { it.paymentMethod !in listOf("Pix", "Débito", "NuPay") }.sumOf { it.amountCents }
                            if (other > 0) Text("Demais formas: ${money(other)}", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            item {
                OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar grupo, loja ou pagamento") }, singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(16.dp),
                    trailingIcon = { if (search.isNotEmpty()) IconButton(onClick = { search = "" }) { Icon(Icons.Default.Close, "Limpar busca") } })
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = grouped, enabled = !selecting, onClick = { grouped = true }, label = { Text("Grupos") })
                    FilterChip(selected = !grouped, onClick = { grouped = false }, label = { Text("Lançamentos") })
                    Spacer(Modifier.weight(1f))
                    var sortMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Default.Sort, "Ordenar resultados") }
                        DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                            listOf("value" to "Maior valor", "name" to "Nome A–Z").plus(if (!grouped) listOf("date" to "Mais recentes") else emptyList()).forEach { (value, title) ->
                                DropdownMenuItem(text = { Text(title) }, onClick = { sort = value; sortMenu = false })
                            }
                        }
                    }
                }
            }
            if (expenses.isNotEmpty()) item {
                if (selecting) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Marque os gastos que deseja reunir. A seleção é mantida ao mudar o mês ou a busca.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(enabled = !busy && filtered.isNotEmpty(), onClick = { selectedIds = (selectedIds + filtered.map { it.id }).distinct() }) { Text("Marcar visíveis") }
                            TextButton(enabled = !busy && selectedIds.isNotEmpty(), onClick = { selectedIds = emptyList() }) { Text("Limpar seleção") }
                        }
                    }
                } else OutlinedButton(modifier = Modifier.fillMaxWidth(), enabled = !busy, onClick = { selecting = true; grouped = false }) {
                    Icon(Icons.Default.CheckBox, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Selecionar gastos")
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.ReceiptLong, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(if (expenses.isEmpty()) "Comece pelo seu extrato" else "Nenhum lançamento encontrado", style = MaterialTheme.typography.titleMedium)
                        Text(if (expenses.isEmpty()) "Importe o CSV detalhado ou adicione seu primeiro gasto." else "Tente outro nome ou selecione todos os meses.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (expenses.isEmpty()) OutlinedButton(enabled = !busy, onClick = { openImport() }) { Text("Importar CSV") }
                    }
                }
            } else if (grouped) {
                items(groups, key = { it.key }) { group ->
                    ExpenseGroupCard(group.key, group.value, total, ::edit, { deleting = it },
                        actionsEnabled = !busy,
                        onRename = { renamingGroup = group.key },
                        onSelect = { selecting = true; grouped = false; selectedIds = group.value.map { it.id } })
                }
            } else {
                items(transactions, key = { it.id }) { expense ->
                    OutlinedCard {
                        TransactionRow(expense, ::edit, { deleting = it }, showGroup = true,
                            selecting = selecting, selected = expense.id in selectedIds,
                            enabled = !busy, onToggle = { toggleSelection(expense) })
                    }
                }
            }
        }
    }
    renamingGroup?.let { oldName ->
        val groupExpenses = expenses.filter { it.category == oldName }
        GroupDestinationDialog(
            initialName = oldName, groups = expenses.map { it.category }.distinct().sorted(),
            oldName = oldName, count = groupExpenses.size, total = groupExpenses.sumOf { it.amountCents }, busy = busy,
            onDismiss = { if (!busy) renamingGroup = null },
            onConfirm = { name -> viewModel.renameGroup(oldName, name) { renamingGroup = null } }
        )
    }
    if (moveDialog) GroupDestinationDialog(
        initialName = "", groups = expenses.map { it.category }.distinct().sorted(),
        count = selectedExpenses.size, total = selectedExpenses.sumOf { it.amountCents }, busy = busy,
        onDismiss = { if (!busy) moveDialog = false },
        onConfirm = { name -> viewModel.moveExpenses(selectedExpenses.map { it.id }, name) { finishSelection() } }
    )
    pendingImport?.let { preview ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelImport() },
            title = { Text("Revisar importação") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${transactionCount(preview.expenses.size)} · ${groupCount(preview.expenses.map { it.category }.distinct().size)}", fontWeight = FontWeight.SemiBold)
                    Text(money(preview.totalCents), style = MaterialTheme.typography.headlineSmall)
                    Text("${dateLabel(preview.expenses.minOf { it.date })} a ${dateLabel(preview.expenses.maxOf { it.date })}")
                    Text("Grupos, datas e formas de pagamento serão preservados. Lançamentos já importados serão ignorados.")
                    if (preview.repeatedRows > 0) Text("${preview.repeatedRows} linhas repetidas no arquivo serão ignoradas.")
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = { Button(enabled = !busy, onClick = viewModel::confirmImport) { Text("Importar lançamentos") } },
            dismissButton = { TextButton(enabled = !busy, onClick = viewModel::cancelImport) { Text("Cancelar") } }
        )
    }
    if (editor) {
        ExpenseEditor(editing, onDismiss = { editor = false; editing = null }, onSave = {
            viewModel.saveExpense(it); editor = false; editing = null
        })
    }
    if (deleteAll || deleting != null) {
        AlertDialog(onDismissRequest = { deleteAll = false; deleting = null },
            title = { Text(if (deleteAll) "Excluir todos os lançamentos?" else "Excluir lançamento?") },
            text = { Text(if (deleteAll) "Todos os gastos salvos serão removidos. Esta ação não pode ser desfeita."
                else "${deleting!!.category} · ${money(deleting!!.amountCents)} · ${dateLabel(deleting!!.date)}") },
            confirmButton = { TextButton(onClick = {
                if (deleteAll) viewModel.deleteAllExpenses() else deleting?.let(viewModel::deleteExpense)
                deleteAll = false; deleting = null
            }) { Text("Excluir", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleteAll = false; deleting = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun ExpenseGroupCard(name: String, expenses: List<Expense>, total: Long, onEdit: (Expense) -> Unit, onDelete: (Expense) -> Unit,
    actionsEnabled: Boolean, onRename: () -> Unit, onSelect: () -> Unit) {
    var expanded by rememberSaveable(name) { mutableStateOf(false) }
    var groupMenu by remember { mutableStateOf(false) }
    val cents = expenses.sumOf { it.amountCents }
    OutlinedCard(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(transactionCount(expenses.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(money(cents), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "Recolher $name" else "Ver lançamentos de $name", Modifier.size(20.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinearProgressIndicator(progress = { if (total == 0L) 0f else (cents.toDouble() / total).toFloat() }, modifier = Modifier.weight(1f).height(4.dp))
                Box {
                    IconButton(modifier = Modifier.size(40.dp), enabled = actionsEnabled, onClick = { groupMenu = true }) { Icon(Icons.Default.MoreVert, "Opções do grupo $name", Modifier.size(20.dp)) }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(text = { Text("Alterar nome do grupo") }, onClick = { groupMenu = false; onRename() })
                        DropdownMenuItem(text = { Text("Selecionar gastos visíveis deste grupo") }, onClick = { groupMenu = false; onSelect() })
                    }
                }
            }
        }
        if (expanded) {
            HorizontalDivider()
            expenses.sortedByDescending { it.date }.forEach { TransactionRow(it, onEdit, onDelete) }
        }
    }
}

@Composable
private fun TransactionRow(expense: Expense, onEdit: (Expense) -> Unit, onDelete: (Expense) -> Unit, showGroup: Boolean = false,
    selecting: Boolean = false, selected: Boolean = false, enabled: Boolean = true, onToggle: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().then(if (selecting) Modifier.clickable(enabled = enabled, onClick = onToggle) else Modifier)
        .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) Checkbox(checked = selected, enabled = enabled, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(if (showGroup) expense.category else transactionDescription(expense), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${dateLabel(expense.date)} · ${expense.paymentMethod}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (showGroup && expense.description.isNotBlank()) Text(transactionDescription(expense), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (expense.origin == "Manual") Text("Adicionado manualmente", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            Text(money(expense.amountCents), style = MaterialTheme.typography.titleSmall)
        }
        if (!selecting) Row(Modifier.align(Alignment.End)) {
            IconButton(onClick = { onEdit(expense) }) { Icon(Icons.Default.Edit, "Editar ${expense.description.ifBlank { expense.category }}", Modifier.size(18.dp)) }
            IconButton(onClick = { onDelete(expense) }) { Icon(Icons.Default.DeleteOutline, "Excluir ${expense.description.ifBlank { expense.category }}", Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDestinationDialog(initialName: String, groups: List<String>, oldName: String? = null,
    count: Int, total: Long, busy: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val destination = name.trim()
    val joinsExisting = destination in groups && destination != oldName
    val suggestions = groups.filter { it != oldName && (destination.isBlank() || searchable(it).contains(searchable(destination))) }.take(8)
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (oldName == null) "Mover gastos para grupo" else "Alterar nome do grupo") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${transactionCount(count)} · ${money(total)}", fontWeight = FontWeight.SemiBold)
                Text(if (oldName != null) "Todos os gastos de “$oldName”, em todos os meses, receberão o nome escolhido."
                    else "Somente os gastos marcados serão movidos. Escolha um grupo existente ou digite um nome novo, como LAZER.")
                OutlinedTextField(value = name, onValueChange = { name = it }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(if (oldName == null) "Grupo de destino" else "Nome do grupo") })
                if (joinsExisting) Text("O grupo “$destination” já existe. Os gastos serão reunidos nele, sem alterar valores ou datas.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                else if (destination.isNotBlank() && oldName == null) Text("Será criado o grupo “$destination”.", style = MaterialTheme.typography.bodySmall)
                if (suggestions.isNotEmpty()) {
                    Text("Grupos existentes", style = MaterialTheme.typography.labelMedium)
                    suggestions.forEach { suggestion ->
                        TextButton(enabled = !busy, onClick = { name = suggestion }, modifier = Modifier.fillMaxWidth()) { Text(suggestion) }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = !busy && count > 0 && destination.isNotBlank() && destination != oldName,
                onClick = { onConfirm(destination) }) { Text(if (oldName == null) "Mover gastos" else if (joinsExisting) "Reunir grupos" else "Renomear") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancelar") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseEditor(expense: Expense?, onDismiss: () -> Unit, onSave: (Expense) -> Unit) {
    var category by remember(expense) { mutableStateOf(expense?.category.orEmpty()) }
    var amount by remember(expense) { mutableStateOf(expense?.let { String.format(Locale.ROOT, "%.2f", it.amount).replace('.', ',') }.orEmpty()) }
    var description by remember(expense) { mutableStateOf(expense?.description.orEmpty()) }
    var date by remember(expense) { mutableLongStateOf(expense?.date ?: System.currentTimeMillis()) }
    var method by remember(expense) { mutableStateOf(expense?.paymentMethod ?: "Pix") }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (expense == null) "Novo gasto" else "Editar gasto") }, text = {
        Column(Modifier.fillMaxWidth()) {
            // The form itself scrolls vertically on small screens and with the keyboard open.
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text("Grupo ou destinatário") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor em reais") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { picker = true }) { Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(dateLabel(date)) }
                Text("Forma de pagamento", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (listOf("Pix", "Débito", "NuPay", "Crédito", "Dinheiro", "Não informado") + method).distinct().forEach { value ->
                        FilterChip(selected = method == value, onClick = { method = value }, label = { Text(value) })
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }, confirmButton = {
        Button(onClick = {
            val cents = runCatching { ExpenseCsv.parseAmount(amount) }.getOrNull()
            if (category.isBlank() || cents == null) error = "Informe o grupo e um valor positivo com até duas casas decimais."
            else onSave((expense ?: Expense(category = category.trim(), amount = cents / 100.0, description = description.trim(), importKey = "manual:${UUID.randomUUID()}"))
                .copy(category = category.trim(), amount = cents / 100.0, description = description.trim(), date = date, paymentMethod = method))
        }) { Text("Salvar") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
    if (picker) {
        // Material DatePicker uses UTC; convert calendar fields rather than shifting the selected day.
        val local = Calendar.getInstance().apply { timeInMillis = date }
        val initialUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
        val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
        DatePickerDialog(onDismissRequest = { picker = false }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { selected ->
                    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selected }
                    date = Calendar.getInstance().apply { clear(); set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 12, 0) }.timeInMillis
                }
                picker = false
            }) { Text("Confirmar") }
        }, dismissButton = { TextButton(onClick = { picker = false }) { Text("Cancelar") } }) { DatePicker(state) }
    }
}
