package com.finanzas.inteligentes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.finanzas.inteligentes.data.*
import com.finanzas.inteligentes.domain.Finance
import com.finanzas.inteligentes.domain.Money
import java.time.LocalDate

data class TxDialogState(val type: TxType, val editing: TxEntity? = null)

private val tabs: List<Pair<String, ImageVector>> = listOf(
    "Inicio" to Icons.Filled.Home,
    "Movimientos" to Icons.AutoMirrored.Filled.List,
    "Presupuestos" to Icons.Filled.DateRange,
    "Metas" to Icons.Filled.Star,
    "Más" to Icons.Filled.Menu
)

@Composable
fun AppRoot(vm: MainViewModel) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var txDialog by remember { mutableStateOf<TxDialogState?>(null) }
    var accountDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val accounts by vm.accounts.collectAsState()

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.message = null }
    }

    fun openTx(type: TxType) {
        when {
            accounts.isEmpty() -> vm.message = "Primero cree una cuenta."
            type == TxType.TRANSFER && accounts.size < 2 -> vm.message = "Necesita al menos dos cuentas para transferir."
            else -> txDialog = TxDialogState(type)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> HomeScreen(vm, ::openTx) { accountDialog = true }
                1 -> TxScreen(vm, ::openTx) { txDialog = TxDialogState(TxType.valueOf(it.type), it) }
                2 -> BudgetScreen(vm)
                3 -> GoalScreen(vm)
                else -> MoreScreen(vm)
            }
        }
    }
    txDialog?.let { TxDialog(vm, it) { txDialog = null } }
    if (accountDialog) AccountDialog(vm) { accountDialog = false }
}

@Composable
private fun MonthSelector(vm: MainViewModel) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { vm.month = vm.month.minusMonths(1) }) { Text("‹ Anterior") }
        Text("${vm.month.month.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale("es"))} ${vm.month.year}", style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { vm.month = vm.month.plusMonths(1) }) { Text("Siguiente ›") }
    }
}

@Composable
private fun StatCard(title: String, cents: Long, modifier: Modifier = Modifier, big: Boolean = false) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(Money.format(cents), style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
internal fun EmptyState(title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body)
            if (action != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
fun HomeScreen(vm: MainViewModel, onTx: (TxType) -> Unit, onAccount: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val txs by vm.txs.collectAsState()
    val goals by vm.goals.collectAsState()
    val subs by vm.subs.collectAsState()
    val m = vm.month

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSelector(vm) }
        if (accounts.isEmpty()) {
            item {
                EmptyState(
                    "Aún no tienes cuentas",
                    "Crea una cuenta (cuenta corriente, ahorro o efectivo) con su saldo actual para empezar a registrar movimientos. Todo se guarda solo en este teléfono.",
                    "Crear cuenta", onAccount
                )
            }
        } else {
            val balance = Finance.totalBalance(accounts, txs)
            val assigned = goals.sumOf { it.savedCents }
            val inc = Finance.sumByType(txs, TxType.INCOME, m)
            val exp = Finance.sumByType(txs, TxType.EXPENSE, m)
            val byCat = Finance.expensesByCategory(txs, m).toList().sortedByDescending { it.second }
            item { StatCard("Disponible (saldo − asignado a metas)", balance - assigned, Modifier.fillMaxWidth(), big = true) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Ingresos del mes", inc, Modifier.weight(1f))
                    StatCard("Gastos del mes", exp, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard("Saldo de cuentas", balance, Modifier.weight(1f))
                    StatCard("Asignado a metas", assigned, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onTx(TxType.INCOME) }) { Text("+ Ingreso") }
                    Button(onClick = { onTx(TxType.EXPENSE) }) { Text("+ Gasto") }
                    OutlinedButton(onClick = { onTx(TxType.TRANSFER) }) { Text("Transferir") }
                }
            }
            val today = LocalDate.now().toEpochDay()
            val upcoming = subs.filter { it.nextEpochDay in today..(today + 30) }.sortedBy { it.nextEpochDay }
            item { Text("Próximos pagos (30 días)", style = MaterialTheme.typography.titleMedium) }
            if (upcoming.isEmpty()) item { Text("Sin pagos previstos. Se toman de sus suscripciones registradas.") }
            else {
                items(upcoming) { u ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${LocalDate.ofEpochDay(u.nextEpochDay)} · ${u.name}"); Text(Money.format(u.amountCents))
                    }
                }
                item { Text("Son pagos previstos: no se descuentan hasta que usted los registre como gasto.", style = MaterialTheme.typography.bodySmall) }
            }
            item { Text("Gastos por categoría", style = MaterialTheme.typography.titleMedium) }
            if (byCat.isEmpty()) item { Text("Sin gastos registrados en este mes.") }
            else {
                val maxV = byCat.first().second.toFloat()
                items(byCat) { (cat, v) ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat); Text(Money.format(v))
                        }
                        LinearProgressIndicator(progress = { v.toFloat() / maxV }, Modifier.fillMaxWidth())
                    }
                }
            }
            item { Text("Cuentas", style = MaterialTheme.typography.titleMedium) }
            items(accounts) { a ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(a.name); Text(Money.format(Finance.accountBalance(a, txs)))
                }
            }
            item { OutlinedButton(onClick = onAccount) { Text("Nueva cuenta") } }
        }
    }
}

@Composable
fun TxScreen(vm: MainViewModel, onNew: (TxType) -> Unit, onEdit: (TxEntity) -> Unit) {
    val txs by vm.txs.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<TxType?>(null) }
    var toDelete by remember { mutableStateOf<TxEntity?>(null) }
    val shown = txs.filter {
        (filter == null || it.type == filter!!.name) &&
            (query.isBlank() || it.description.contains(query, true) || it.category.contains(query, true))
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onNew(TxType.INCOME) }) { Text("+ Ingreso") }
            Button(onClick = { onNew(TxType.EXPENSE) }) { Text("+ Gasto") }
            OutlinedButton(onClick = { onNew(TxType.TRANSFER) }) { Text("Transferir") }
        }
        OutlinedTextField(query, { query = it }, label = { Text("Buscar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(filter == null, { filter = null }, { Text("Todos") })
            FilterChip(filter == TxType.INCOME, { filter = TxType.INCOME }, { Text("Ingresos") })
            FilterChip(filter == TxType.EXPENSE, { filter = TxType.EXPENSE }, { Text("Gastos") })
            FilterChip(filter == TxType.TRANSFER, { filter = TxType.TRANSFER }, { Text("Transf.") })
        }
        if (shown.isEmpty()) {
            Spacer(Modifier.height(12.dp))
            EmptyState("Sin movimientos", "Registra tu primer ingreso o gasto con los botones de arriba. Toca un movimiento para editarlo.")
        } else LazyColumn {
            items(shown, key = { it.id }) { t ->
                val sign = when (TxType.valueOf(t.type)) { TxType.INCOME -> "+"; TxType.EXPENSE -> "−"; TxType.TRANSFER -> "" }
                val acc = accounts.firstOrNull { it.id == t.accountId }?.name ?: "?"
                val to = t.toAccountId?.let { id -> accounts.firstOrNull { it.id == id }?.name }
                ListItem(
                    modifier = Modifier.clickable { onEdit(t) },
                    headlineContent = { Text(t.description.ifBlank { t.category }) },
                    supportingContent = { Text("${LocalDate.ofEpochDay(t.epochDay)} · ${t.category} · $acc" + (to?.let { " → $it" } ?: "")) },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sign + Money.format(t.amountCents))
                            IconButton(onClick = { toDelete = t }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                        }
                    }
                )
                HorizontalDivider()
            }
        }
    }
    toDelete?.let { t ->
        ConfirmDialog("¿Eliminar este movimiento?", "Esta acción no se puede deshacer.", { vm.deleteTx(t.id); toDelete = null }) { toDelete = null }
    }
}

@Composable
internal fun ConfirmDialog(title: String, body: String, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo, title = { Text(title) }, text = { Text(body) },
        confirmButton = { TextButton(onClick = onYes) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = onNo) { Text("Cancelar") } }
    )
}

@Composable
private fun AccountPicker(label: String, accounts: List<AccountEntity>, selected: Long?, onSelect: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: ${accounts.firstOrNull { it.id == selected }?.name ?: "elegir"}")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { a -> DropdownMenuItem(text = { Text(a.name) }, onClick = { onSelect(a.id); open = false }) }
        }
    }
}

@Composable
private fun CategoryField(value: String, options: List<String>, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text("Categoría") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(options) { c -> AssistChip(onClick = { onChange(c) }, label = { Text(c) }) }
    }
}

@Composable
fun TxDialog(vm: MainViewModel, st: TxDialogState, onClose: () -> Unit) {
    val accounts by vm.accounts.collectAsState()
    val e = st.editing
    val isTransfer = st.type == TxType.TRANSFER
    var amount by remember { mutableStateOf(e?.let { Money.toPlain(it.amountCents) } ?: "") }
    var category by remember { mutableStateOf(if (isTransfer) "" else e?.category ?: "") }
    var desc by remember { mutableStateOf(e?.description ?: "") }
    var notes by remember { mutableStateOf(e?.notes ?: "") }
    var date by remember { mutableStateOf(LocalDate.ofEpochDay(e?.epochDay ?: LocalDate.now().toEpochDay()).toString()) }
    var from by remember { mutableStateOf(e?.accountId ?: accounts.firstOrNull()?.id) }
    var to by remember { mutableStateOf(e?.toAccountId) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val c = Money.parseToCents(amount)
        if (c == null) { error = "Importe inválido: use un número mayor que 0 con hasta 2 decimales."; return }
        val day = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        if (day == null) { error = "Fecha inválida: use el formato AAAA-MM-DD."; return }
        val acc = from
        if (acc == null) { error = "Elija una cuenta."; return }
        if (isTransfer) {
            if (to == null || to == acc) { error = "Elija una cuenta de destino distinta."; return }
        } else if (category.isBlank()) { error = "Indique una categoría."; return }
        vm.saveTx(
            TxEntity(
                id = e?.id ?: 0, type = st.type.name, amountCents = c,
                category = if (isTransfer) Categories.TRANSFER else category.trim(),
                epochDay = day.toEpochDay(), description = desc.trim(), notes = notes.trim(),
                accountId = acc, toAccountId = if (isTransfer) to else null
            )
        )
        onClose()
    }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(when (st.type) { TxType.INCOME -> "Ingreso"; TxType.EXPENSE -> "Gasto"; TxType.TRANSFER -> "Transferencia" } + " (CAD)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(amount, { amount = it }, label = { Text("Importe") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!isTransfer) CategoryField(category, if (st.type == TxType.INCOME) Categories.income else Categories.expense) { category = it }
                AccountPicker(if (isTransfer) "Desde" else "Cuenta", accounts, from) { from = it }
                if (isTransfer) AccountPicker("Hacia", accounts, to) { to = it }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(desc, { desc = it }, label = { Text("Descripción") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas (opcional)") }, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { submit() }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
fun AccountDialog(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var opening by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Nueva cuenta (CAD)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (ej. Cuenta corriente, Efectivo)") }, singleLine = true)
                OutlinedTextField(opening, { opening = it }, label = { Text("Saldo actual (puede ser 0)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = if (opening.isBlank()) 0L else Money.parseToCents(opening, allowZero = true)
                when {
                    name.isBlank() -> error = "Indique un nombre."
                    c == null -> error = "Saldo inválido: use un número con hasta 2 decimales."
                    else -> { vm.addAccount(name.trim(), c); onClose() }
                }
            }) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
fun BudgetScreen(vm: MainViewModel) {
    val budgets by vm.budgets.collectAsState()
    val txs by vm.txs.collectAsState()
    var add by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<BudgetEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { MonthSelector(vm) }
        item { Button(onClick = { add = true }) { Text("+ Presupuesto mensual") } }
        if (budgets.isEmpty()) item {
            EmptyState("Sin presupuestos", "Fija un límite mensual por categoría (por ejemplo, Alimentación) y verás cuánto llevas gastado.")
        }
        items(budgets, key = { it.id }) { b ->
            val spent = Finance.budgetSpent(txs, b.category, vm.month)
            val pct = Finance.percentUsed(spent, b.limitCents)
            val over = spent > b.limitCents
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(b.category, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { toDelete = b }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                    }
                    LinearProgressIndicator(progress = { (spent.toFloat() / b.limitCents).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Text("Gastado ${Money.format(spent)} de ${Money.format(b.limitCents)} ($pct %)")
                    Text(
                        if (over) "Excedido por ${Money.format(spent - b.limitCents)}" else "Restante ${Money.format(b.limitCents - spent)}",
                        color = if (over || pct >= 80) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    if (add) BudgetDialog(vm) { add = false }
    toDelete?.let { b -> ConfirmDialog("¿Eliminar presupuesto de ${b.category}?", "Los movimientos no se borran.", { vm.deleteBudget(b.id); toDelete = null }) { toDelete = null } }
}

@Composable
private fun BudgetDialog(vm: MainViewModel, onClose: () -> Unit) {
    var category by remember { mutableStateOf("") }
    var limit by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Nuevo presupuesto mensual") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CategoryField(category, Categories.expense) { category = it }
                OutlinedTextField(limit, { limit = it }, label = { Text("Límite mensual (CAD)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = Money.parseToCents(limit)
                when {
                    category.isBlank() -> error = "Indique una categoría."
                    c == null -> error = "Límite inválido: use un número mayor que 0."
                    else -> { vm.addBudget(category.trim(), c); onClose() }
                }
            }) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
fun GoalScreen(vm: MainViewModel) {
    val goals by vm.goals.collectAsState()
    var add by remember { mutableStateOf(false) }
    var contribute by remember { mutableStateOf<GoalEntity?>(null) }
    var toDelete by remember { mutableStateOf<GoalEntity?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(onClick = { add = true }) { Text("+ Meta de ahorro") } }
        if (goals.isEmpty()) item {
            EmptyState("Sin metas", "Crea una meta (por ejemplo, fondo de emergencia) con una cantidad y una fecha opcional para saber cuánto ahorrar al mes.")
        }
        items(goals, key = { it.id }) { g ->
            val need = Finance.monthlyContributionNeeded(g, LocalDate.now())
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { toDelete = g }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                    }
                    LinearProgressIndicator(progress = { (g.savedCents.toFloat() / g.targetCents).coerceIn(0f, 1f) }, Modifier.fillMaxWidth())
                    Text("Asignado ${Money.format(g.savedCents)} de ${Money.format(g.targetCents)}")
                    g.targetEpochDay?.let { Text("Fecha objetivo: ${LocalDate.ofEpochDay(it)}") }
                    if (need != null && need > 0) Text("Aportación mensual necesaria: ${Money.format(need)} (cálculo: restante ÷ meses hasta la fecha, redondeado hacia arriba)")
                    if (g.savedCents < g.targetCents) OutlinedButton(onClick = { contribute = g }) { Text("Asignar dinero") }
                }
            }
        }
    }
    if (add) GoalDialog(vm) { add = false }
    contribute?.let { g -> ContributeDialog(vm, g) { contribute = null } }
    toDelete?.let { g -> ConfirmDialog("¿Eliminar la meta ${g.name}?", "El dinero asignado vuelve a quedar disponible.", { vm.deleteGoal(g.id); toDelete = null }) { toDelete = null } }
}

@Composable
private fun GoalDialog(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Nueva meta de ahorro") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(target, { target = it }, label = { Text("Cantidad deseada (CAD)") }, singleLine = true)
                OutlinedTextField(date, { date = it }, label = { Text("Fecha objetivo AAAA-MM-DD (opcional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = Money.parseToCents(target)
                val d = if (date.isBlank()) null else runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                when {
                    name.isBlank() -> error = "Indique un nombre."
                    c == null -> error = "Cantidad inválida: use un número mayor que 0."
                    date.isNotBlank() && d == null -> error = "Fecha inválida: use AAAA-MM-DD."
                    d != null && !d.isAfter(LocalDate.now()) -> error = "La fecha debe ser futura."
                    else -> { vm.addGoal(name.trim(), c, d?.toEpochDay()); onClose() }
                }
            }) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
private fun ContributeDialog(vm: MainViewModel, g: GoalEntity, onClose: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Asignar a ${g.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Disponible: ${Money.format(vm.available())}. Esto reserva dinero que ya tiene; no es un gasto.")
                OutlinedTextField(amount, { amount = it }, label = { Text("Importe (CAD)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = Money.parseToCents(amount)
                if (c == null) error = "Importe inválido." else { vm.contribute(g, c); onClose() }
            }) { Text("Asignar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}
