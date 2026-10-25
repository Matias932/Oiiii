package com.finanzas.inteligentes.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.finanzas.inteligentes.data.*
import com.finanzas.inteligentes.domain.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private fun unitText(v: BigDecimal) = "$" + v.setScale(3, RoundingMode.HALF_UP).toPlainString() + " CAD"
private fun PriceObsEntity.toObs() = Obs(id, product, store, priceCents, BigDecimal(qty), Measure.valueOf(measure), epochDay, source)

// ---------------- Asistente (preguntas rápidas, 100 % local) ----------------
@Composable
fun AssistantScreen(vm: MainViewModel) {
    val txs by vm.txs.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val goals by vm.goals.collectAsState()
    val subs by vm.subs.collectAsState()
    var answer by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    val m = YearMonth.now()
    val balance = Finance.totalBalance(accounts, txs)
    val available = balance - goals.sumOf { it.savedCents }
    val today = LocalDate.now().toEpochDay()
    val upcoming = subs.filter { it.nextEpochDay in today..(today + 30) }.sortedBy { it.nextEpochDay }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text("Asistente con IA: no conectado", style = MaterialTheme.typography.titleSmall)
                Text("Una conversación con IA requiere un servidor seguro y credenciales que esta versión no incluye. Mientras tanto, estas preguntas se responden con cálculos locales sobre sus datos, sin Internet.")
            }
        }
        OutlinedButton(onClick = {
            answer = "En ${m.monthValue}/${m.year} ha gastado ${Money.format(Finance.sumByType(txs, TxType.EXPENSE, m))} y ha ingresado ${Money.format(Finance.sumByType(txs, TxType.INCOME, m))}."
        }, modifier = Modifier.fillMaxWidth()) { Text("¿Cuánto gasté este mes?") }
        OutlinedButton(onClick = {
            answer = "Disponible: ${Money.format(available)} (saldo de cuentas ${Money.format(balance)} menos ${Money.format(balance - available)} asignado a metas)."
        }, modifier = Modifier.fillMaxWidth()) { Text("¿Cuánto dinero tengo disponible?") }
        OutlinedButton(onClick = {
            val top = Finance.expensesByCategory(txs, m).maxByOrNull { it.value }
            answer = if (top == null) "No hay gastos registrados este mes." else "La categoría con más gasto este mes es ${top.key}: ${Money.format(top.value)}."
        }, modifier = Modifier.fillMaxWidth()) { Text("¿En qué categoría gasto más?") }
        OutlinedButton(onClick = {
            answer = if (upcoming.isEmpty()) "No tiene pagos previstos en los próximos 30 días (según sus suscripciones registradas)."
            else "Pagos previstos en 30 días: " + upcoming.joinToString("; ") { "${it.name} ${Money.format(it.amountCents)} el ${LocalDate.ofEpochDay(it.nextEpochDay)}" } +
                ". Total: ${Money.format(upcoming.sumOf { it.amountCents })}."
        }, modifier = Modifier.fillMaxWidth()) { Text("¿Qué pagos tengo próximos?") }
        OutlinedTextField(amount, { amount = it }, label = { Text("¿Puedo permitirme una compra de (CAD)?") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            val c = Money.parseToCents(amount)
            answer = if (c == null) "Escriba un importe válido."
            else {
                val after = available - c
                val due = upcoming.sumOf { it.amountCents }
                "Disponible hoy ${Money.format(available)}; tras la compra quedarían ${Money.format(after)}. Pagos previstos en 30 días: ${Money.format(due)}" +
                    (if (after - due < 0) ". Con esos pagos el dinero no alcanzaría." else ".") +
                    " Es un cálculo con sus datos registrados, no una recomendación."
            }
        }) { Text("Calcular") }
        answer?.let {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Resultado (cálculo con sus datos)", style = MaterialTheme.typography.titleSmall)
                    Text(it)
                }
            }
        }
    }
}

// ---------------- Listas de compras ----------------
@Composable
fun ShopScreen(vm: MainViewModel) {
    val lists by vm.shopLists.collectAsState()
    val items by vm.shopItems.collectAsState()
    val obs by vm.priceObs.collectAsState()
    var openId by rememberSaveable { mutableStateOf<Long?>(null) }
    var addList by remember { mutableStateOf(false) }
    var addItem by remember { mutableStateOf(false) }
    var delList by remember { mutableStateOf<ShopListEntity?>(null) }
    val open = lists.firstOrNull { it.id == openId }

    if (open == null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Button(onClick = { addList = true }) { Text("+ Lista") } }
            if (lists.isEmpty()) item { EmptyState("Sin listas", "Cree una lista (por ejemplo, Supermercado semanal), con presupuesto opcional, y agregue productos.") }
            items(lists, key = { it.id }) { l ->
                val its = items.filter { it.listId == l.id }
                Card(Modifier.fillMaxWidth().clickable { openId = l.id }) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(l.name, style = MaterialTheme.typography.titleMedium)
                            Text("${its.size} productos · ${its.count { it.checked }} comprados")
                        }
                        IconButton(onClick = { delList = l }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                    }
                }
            }
        }
    } else {
        val its = items.filter { it.listId == open.id }
        val known = its.filter { it.priceCents > 0 }
        val estimate = known.sumOf { it.quantity * it.priceCents }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { openId = null }) { Text("‹ Listas") }
                    Button(onClick = { addItem = true }) { Text("+ Producto") }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(open.name, style = MaterialTheme.typography.titleMedium)
                        Text("Costo estimado: ${Money.format(estimate)}" + if (known.size < its.size) " (${its.size - known.size} sin precio, no incluidos)" else "")
                        if (open.budgetCents > 0) {
                            Text("Presupuesto: ${Money.format(open.budgetCents)} · " + if (estimate > open.budgetCents) "excedido por ${Money.format(estimate - open.budgetCents)}" else "restan ${Money.format(open.budgetCents - estimate)}")
                        }
                        Text("Los precios son los que usted anotó; verifique que sigan vigentes.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (its.isEmpty()) item { Text("Aún no hay productos en esta lista.") }
            items(its, key = { it.id }) { i ->
                val o = obs.firstOrNull { it.product.trim().equals(i.name.trim(), ignoreCase = true) }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(i.checked, { vm.toggleItem(i) })
                    Column(Modifier.weight(1f)) {
                        Text("${i.quantity} × ${i.name}", style = MaterialTheme.typography.bodyLarge)
                        Text(if (i.priceCents > 0) "${Money.format(i.priceCents)} c/u = ${Money.format(i.priceCents * i.quantity)}" else "Precio sin registrar", style = MaterialTheme.typography.bodySmall)
                        if (o != null) Text("Último precio anotado: ${Money.format(o.priceCents)} por ${BigDecimal(o.qty).stripTrailingZeros().toPlainString()} ${Measure.valueOf(o.measure).label} en ${o.store} (${LocalDate.ofEpochDay(o.epochDay)})", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { vm.deleteShopItem(i.id) }) { Icon(Icons.Filled.Delete, contentDescription = "Quitar") }
                }
            }
        }
    }
    if (addList) ShopListDialog(vm) { addList = false }
    if (addItem && open != null) ShopItemDialog(vm, open.id) { addItem = false }
    delList?.let { l -> ConfirmDialog("¿Eliminar la lista ${l.name}?", "Se borran también sus productos.", { vm.deleteShopList(l.id); delList = null }) { delList = null } }
}

@Composable
private fun ShopListDialog(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var budget by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Nueva lista") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true)
                OutlinedTextField(budget, { budget = it }, label = { Text("Presupuesto (CAD, opcional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = if (budget.isBlank()) 0L else Money.parseToCents(budget)
                when {
                    name.isBlank() -> error = "Indique un nombre."
                    b == null -> error = "Presupuesto inválido."
                    else -> { vm.addShopList(name.trim(), b); onClose() }
                }
            }) { Text("Crear") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
private fun ShopItemDialog(vm: MainViewModel, listId: Long, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var price by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Nuevo producto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Producto") }, singleLine = true)
                OutlinedTextField(qty, { qty = it }, label = { Text("Cantidad") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Precio por unidad (CAD, opcional)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.trim().toIntOrNull()
                val p = if (price.isBlank()) 0L else Money.parseToCents(price)
                when {
                    name.isBlank() -> error = "Indique el producto."
                    q == null || q !in 1..999 -> error = "Cantidad entre 1 y 999."
                    p == null -> error = "Precio inválido."
                    else -> { vm.addShopItem(ShopItemEntity(listId = listId, name = name.trim(), quantity = q, priceCents = p, checked = false)); onClose() }
                }
            }) { Text("Agregar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

// ---------------- Historial de precios ----------------
@Composable
fun PricesScreen(vm: MainViewModel) {
    val obs by vm.priceObs.collectAsState()
    var add by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<PriceObsEntity?>(null) }
    val groups = obs.groupBy { it.product.trim().lowercase() }.values.toList()
    val today = LocalDate.now().toEpochDay()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(onClick = { add = true }) { Text("+ Precio observado") } }
        item { Text("Esta app no se conecta a ninguna tienda: solo guarda los precios que usted anota, con fecha. «Mínimo registrado» es el menor de sus anotaciones, no un mínimo histórico real.", style = MaterialTheme.typography.bodySmall) }
        if (groups.isEmpty()) item { EmptyState("Sin precios anotados", "Anote lo que ve en la tienda (producto, comercio, precio y cantidad) para comparar en el tiempo.") }
        items(groups) { g ->
            val s = PriceHistory.summary(g.map { it.toObs() })!!
            val age = ChronoUnit.DAYS.between(LocalDate.ofEpochDay(s.latest.epochDay), LocalDate.now())
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(s.latest.product, style = MaterialTheme.typography.titleMedium)
                    Text("Último: ${unitText(s.latest.normalized)} ${s.latest.measure.perLabel} · ${s.latest.store} · ${LocalDate.ofEpochDay(s.latest.epochDay)}")
                    if (age > 7) Text("Anotado hace $age días: puede estar desactualizado.", color = MaterialTheme.colorScheme.error)
                    if (s.previous != null) Text("Anterior: ${unitText(s.previous.normalized)} (${LocalDate.ofEpochDay(s.previous.epochDay)})" + (s.changePct?.let { " · variación ${it.toPlainString()} %" } ?: ""))
                    if (s.comparable > 1) Text("Mínimo registrado en la app: ${unitText(s.minObs.normalized)} en ${s.minObs.store}")
                    else Text("Hace falta más de una anotación comparable para calcular variación y mínimo.", style = MaterialTheme.typography.bodySmall)
                    g.take(5).forEach { o ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${LocalDate.ofEpochDay(o.epochDay)} · ${o.store} · ${Money.format(o.priceCents)}", style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = { del = o }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                        }
                    }
                }
            }
        }
    }
    if (add) PriceDialog(vm) { add = false }
    del?.let { o -> ConfirmDialog("¿Eliminar esta anotación?", "${o.product} · ${o.store}", { vm.deletePriceObs(o.id); del = null }) { del = null } }
}

@Composable
private fun PriceDialog(vm: MainViewModel, onClose: () -> Unit) {
    var product by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf(Measure.UNIT) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Precio observado") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(product, { product = it }, label = { Text("Producto") }, singleLine = true)
                OutlinedTextField(store, { store = it }, label = { Text("Comercio / sucursal") }, singleLine = true)
                OutlinedTextField(price, { price = it }, label = { Text("Precio (CAD)") }, singleLine = true)
                OutlinedTextField(qty, { qty = it }, label = { Text("Cantidad del paquete") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Measure.values().forEach { m -> FilterChip(measure == m, { measure = m }, { Text(m.label) }) }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val p = Money.parseToCents(price)
                val q = runCatching { BigDecimal(qty.trim().replace(',', '.')) }.getOrNull()
                val d = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                when {
                    product.isBlank() -> error = "Indique el producto."
                    store.isBlank() -> error = "Indique el comercio."
                    p == null -> error = "Precio inválido."
                    q == null || q.signum() <= 0 || q > BigDecimal(1_000_000) -> error = "Cantidad inválida."
                    d == null || d.isAfter(LocalDate.now()) -> error = "Fecha inválida (no puede ser futura)."
                    else -> {
                        vm.addPriceObs(PriceObsEntity(product = product.trim(), store = store.trim(), priceCents = p, qty = q.toPlainString(), measure = measure.name, epochDay = d.toEpochDay(), source = ManualSource.name))
                        onClose()
                    }
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

// ---------------- Escáner de recibos ----------------
private fun scan(ctx: Context, uri: Uri, ok: (String) -> Unit, fail: () -> Unit) {
    try {
        val img = InputImage.fromFilePath(ctx, uri)
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img)
            .addOnSuccessListener { ok(it.text) }
            .addOnFailureListener { fail() }
    } catch (e: Exception) {
        fail()
    }
}

@Composable
fun ReceiptScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val accounts by vm.accounts.collectAsState()
    var text by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf<TxDialogState?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true; error = null; text = null
            scan(ctx, uri, { t -> text = t; busy = false }, { error = "No se pudo leer la imagen. Pruebe con otra foto o registre el gasto a mano."; busy = false })
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Elija una foto de un recibo (tómela antes con la cámara). El texto se lee en el teléfono: la imagen no se envía a ningún servidor.")
        Button(onClick = { pick.launch("image/*") }, enabled = !busy) { Text(if (busy) "Leyendo…" else "Elegir foto del recibo") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        text?.let { t ->
            val d = ReceiptParser.parse(t)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Datos detectados (revíselos)", style = MaterialTheme.typography.titleSmall)
                    Text("Comercio: ${d.store ?: "no reconocido"}")
                    Text("Fecha: ${d.day?.toString() ?: "no reconocida (solo se lee AAAA-MM-DD)"}")
                    Text("Total: ${d.totalCents?.let { Money.format(it) } ?: "no reconocido"}")
                    Text("No se extraen productos ni impuestos por separado.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (d.totalCents == null) Text("No se reconoció un total con seguridad, así que no se crea ningún borrador. Registre el gasto a mano.", color = MaterialTheme.colorScheme.error)
            else if (accounts.isEmpty()) Text("Cree primero una cuenta para registrar el gasto.")
            else Button(onClick = {
                draft = TxDialogState(
                    TxType.EXPENSE,
                    TxEntity(0, TxType.EXPENSE.name, d.totalCents, "", (d.day ?: LocalDate.now()).toEpochDay(), d.store ?: "", "Desde recibo escaneado: verificar", accounts.first().id, null)
                )
            }) { Text("Crear borrador de gasto") }
            Text("Texto reconocido:", style = MaterialTheme.typography.titleSmall)
            Text(t, style = MaterialTheme.typography.bodySmall)
        }
    }
    draft?.let { TxDialog(vm, it) { draft = null } }
}
