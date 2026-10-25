package com.finanzas.inteligentes.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import com.finanzas.inteligentes.data.DebtEntity
import com.finanzas.inteligentes.data.SubEntity
import com.finanzas.inteligentes.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val tools = listOf(
    Triple("debts", "Deudas", "Registra deudas y compara avalancha vs. bola de nieve."),
    Triple("subs", "Suscripciones", "Costo mensual y anual de pagos periódicos."),
    Triple("tax", "Impuestos de Quebec", "TPS y TVQ: agrega o quita impuestos de un precio."),
    Triple("invest", "Simulador de inversión", "Escenarios hipotéticos con comisiones e inflación."),
    Triple("unit", "Precio por unidad", "Compara productos por 100 g, 100 ml o unidad.")
)

@Composable
fun MoreScreen(vm: MainViewModel) {
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = sel != null) { sel = null }
    if (sel == null) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Herramientas", style = MaterialTheme.typography.titleLarge) }
            items(tools) { (key, title, desc) ->
                Card(Modifier.fillMaxWidth().clickable { sel = key }) {
                    Column(Modifier.padding(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(desc)
                    }
                }
            }
        }
    } else {
        Column(Modifier.fillMaxSize()) {
            TextButton(onClick = { sel = null }, modifier = Modifier.padding(horizontal = 8.dp)) { Text("‹ Volver") }
            when (sel) {
                "debts" -> DebtScreen(vm)
                "subs" -> SubsScreen(vm)
                "tax" -> TaxScreen()
                "invest" -> InvestScreen()
                else -> UnitScreen()
            }
        }
    }
}

private fun ratePct(bp: Int) = BigDecimal(bp).movePointLeft(2).toPlainString() + " %"
private fun duration(months: Int): String {
    val y = months / 12; val m = months % 12
    return when {
        y == 0 -> "$m meses"
        m == 0 -> "$y años"
        else -> "$y años y $m meses"
    }
}

// ---------------- Deudas ----------------
@Composable
fun DebtScreen(vm: MainViewModel) {
    val debts by vm.debts.collectAsState()
    var add by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<DebtEntity?>(null) }
    var extra by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(onClick = { add = true }) { Text("+ Deuda") } }
        if (debts.isEmpty()) item {
            EmptyState("Sin deudas registradas", "Agrega cada deuda con su saldo, tasa de interés anual y pago mínimo para simular cómo pagarlas.")
        }
        items(debts, key = { it.id }) { d ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { del = d }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                    }
                    Text("Saldo ${Money.format(d.balanceCents)} · tasa ${ratePct(d.rateBp)} anual")
                    Text("Pago mínimo mensual ${Money.format(d.minPaymentCents)}")
                }
            }
        }
        if (debts.isNotEmpty()) item {
            val extraC = if (extra.isBlank()) 0L else Money.parseToCents(extra, allowZero = true)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Simulador de pago", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(extra, { extra = it }, label = { Text("Pago extra mensual (CAD, opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    if (extraC == null) Text("Importe inválido.", color = MaterialTheme.colorScheme.error)
                    else {
                        val inputs = debts.map { DebtInput(it.name, it.balanceCents, it.rateBp, it.minPaymentCents) }
                        PlanText("Avalancha (mayor interés primero)", DebtSim.simulate(inputs, extraC, Strategy.AVALANCHE))
                        PlanText("Bola de nieve (menor saldo primero)", DebtSim.simulate(inputs, extraC, Strategy.SNOWBALL))
                        Text(
                            "Supuestos: tasa fija, interés mensual = saldo × tasa ÷ 12, pagos al final de cada mes, sin nuevos cargos. " +
                                "Los pagos mínimos de deudas ya liquidadas se reutilizan. Es una estimación: ninguna estrategia es la mejor en todos los casos.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
    if (add) DebtDialog(vm) { add = false }
    del?.let { d -> ConfirmDialog("¿Eliminar ${d.name}?", "Esta acción no se puede deshacer.", { vm.deleteDebt(d.id); del = null }) { del = null } }
}

@Composable
private fun PlanText(title: String, p: DebtPlan) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (!p.feasible) Text("No se liquida en 100 años con estos pagos (el pago no cubre los intereses). Aumenta el pago.", color = MaterialTheme.colorScheme.error)
        else {
            Text("Tiempo: ${duration(p.months)} · Intereses totales: ${Money.format(p.totalInterestCents)}")
            Text("Orden de liquidación: ${p.payoffOrder.joinToString(" → ")}")
        }
    }
}

@Composable
private fun DebtDialog(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var balance by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var min by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Nueva deuda") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre (ej. Tarjeta de crédito)") }, singleLine = true)
                OutlinedTextField(balance, { balance = it }, label = { Text("Saldo pendiente (CAD)") }, singleLine = true)
                OutlinedTextField(rate, { rate = it }, label = { Text("Tasa de interés anual (%)") }, singleLine = true)
                OutlinedTextField(min, { min = it }, label = { Text("Pago mínimo mensual (CAD)") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = Money.parseToCents(balance)
                val r = Money.parsePercentToBp(rate, 0, 100)
                val m = Money.parseToCents(min, allowZero = true)
                when {
                    name.isBlank() -> error = "Indique un nombre."
                    b == null -> error = "Saldo inválido: use un número mayor que 0."
                    r == null -> error = "Tasa inválida: entre 0 y 100 con hasta 2 decimales."
                    m == null -> error = "Pago mínimo inválido."
                    else -> { vm.addDebt(DebtEntity(name = name.trim(), balanceCents = b, rateBp = r, minPaymentCents = m)); onClose() }
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

// ---------------- Suscripciones ----------------
@Composable
fun SubsScreen(vm: MainViewModel) {
    val subs by vm.subs.collectAsState()
    var add by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf<SubEntity?>(null) }
    val monthly = subs.sumOf { SubCost.monthly(it.amountCents, Freq.valueOf(it.frequency)) }
    val annual = subs.sumOf { SubCost.annual(it.amountCents, Freq.valueOf(it.frequency)) }
    val dups = SubCost.duplicateNames(subs.map { it.name })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Button(onClick = { add = true }) { Text("+ Suscripción") } }
        if (subs.isEmpty()) item {
            EmptyState("Sin suscripciones", "Registra tus pagos periódicos (streaming, teléfono, etc.) para ver cuánto cuestan al mes y al año. La app solo los registra: no cancela nada.")
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Costo mensual estimado: ${Money.format(monthly)}", style = MaterialTheme.typography.titleMedium)
                        Text("Costo anual estimado: ${Money.format(annual)}")
                        Text("Los pagos semanales se convierten usando 52 semanas al año.", style = MaterialTheme.typography.bodySmall)
                        if (dups.isNotEmpty()) Text("Posibles duplicados: ${dups.joinToString(", ")}", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
        items(subs, key = { it.id }) { s ->
            val f = Freq.valueOf(s.frequency)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(s.name, style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { del = s }) { Icon(Icons.Filled.Delete, contentDescription = "Eliminar") }
                    }
                    Text("${Money.format(s.amountCents)} · ${f.label.lowercase()} · ${s.category}")
                    Text("Próximo cobro: ${LocalDate.ofEpochDay(s.nextEpochDay)}")
                    if (s.notes.isNotBlank()) Text(s.notes, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (add) SubDialog(vm) { add = false }
    del?.let { s -> ConfirmDialog("¿Quitar ${s.name} del registro?", "Esto solo lo borra de la app; no cancela el servicio.", { vm.deleteSub(s.id); del = null }) { del = null } }
}

@Composable
private fun SubDialog(vm: MainViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var freq by remember { mutableStateOf(Freq.MONTHLY) }
    var date by remember { mutableStateOf(LocalDate.now().plusMonths(1).toString()) }
    var cat by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Nueva suscripción") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Servicio") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Importe (CAD)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Freq.values().forEach { f -> FilterChip(freq == f, { freq = f }, { Text(f.label) }) }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Próximo cobro (AAAA-MM-DD)") }, singleLine = true)
                OutlinedTextField(cat, { cat = it }, label = { Text("Categoría (opcional)") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notas (opcional)") })
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val c = Money.parseToCents(amount)
                val d = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
                when {
                    name.isBlank() -> error = "Indique el servicio."
                    c == null -> error = "Importe inválido: use un número mayor que 0."
                    d == null -> error = "Fecha inválida: use AAAA-MM-DD."
                    else -> {
                        vm.addSub(SubEntity(name = name.trim(), amountCents = c, frequency = freq.name, nextEpochDay = d.toEpochDay(), category = cat.trim().ifBlank { "Suscripciones" }, notes = notes.trim()))
                        onClose()
                    }
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

// ---------------- Impuestos Quebec ----------------
@Composable
fun TaxScreen() {
    var amount by remember { mutableStateOf("") }
    var mode by remember { mutableIntStateOf(0) }
    val c = Money.parseToCents(amount)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == 0, { mode = 0 }, { Text("Precio sin impuestos") })
            FilterChip(mode == 1, { mode = 1 }, { Text("Total con impuestos") })
        }
        OutlinedTextField(amount, { amount = it }, label = { Text("Importe (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (amount.isNotBlank() && c == null) Text("Importe inválido.", color = MaterialTheme.colorScheme.error)
        if (c != null) {
            val r = if (mode == 0) QcTax.addTax(c) else QcTax.removeTax(c)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Antes de impuestos: ${Money.format(r.preCents)}")
                    Text("TPS (${QcTax.GST_PCT} %): ${Money.format(r.gstCents)}")
                    Text("TVQ (${QcTax.QST_PCT} %): ${Money.format(r.qstCents)}")
                    Text("Total con impuestos: ${Money.format(r.totalCents)}", style = MaterialTheme.typography.titleMedium)
                    if (!r.exact) Text("Aviso: ningún precio exacto produce ese total por redondeo; el desglose es aproximado (±1 centavo).", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        Text(
            "TPS ${QcTax.GST_PCT} % y TVQ ${QcTax.QST_PCT} %, calculadas por separado sobre el precio sin impuestos y redondeadas al centavo. " +
                "Tasas confirmadas el ${QcTax.VERIFIED} en guías que citan a Revenu Québec; confirme en revenuquebec.ca. " +
                "Aplica a ventas totalmente gravadas (no a productos exentos o de tasa cero). Es una estimación, no asesoría fiscal.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------------- Simulador de inversión ----------------
@Composable
fun InvestScreen() {
    var initial by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var years by remember { mutableStateOf("10") }
    var ret by remember { mutableStateOf("5") }
    var fee by remember { mutableStateOf("1") }
    var infl by remember { mutableStateOf("2") }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<SimResult>?>(null) }

    fun calc() {
        val i = if (initial.isBlank()) 0L else Money.parseToCents(initial, true)
        val m = if (monthly.isBlank()) 0L else Money.parseToCents(monthly, true)
        val y = years.trim().toIntOrNull()
        val r = Money.parsePercentToBp(ret, -50, 50)
        val f = Money.parsePercentToBp(fee, 0, 10)
        val n = Money.parsePercentToBp(infl, 0, 20)
        results = null
        when {
            i == null || m == null -> error = "Capital o aportación inválidos."
            i == 0L && m == 0L -> error = "Indique un capital inicial o una aportación mensual."
            y == null || y !in 1..60 -> error = "Años: entre 1 y 60."
            r == null -> error = "Rendimiento: entre -50 y 50 %."
            f == null -> error = "Comisión: entre 0 y 10 %."
            n == null -> error = "Inflación: entre 0 y 20 %."
            else -> { error = null; results = InvestSim.scenarios(i, m, y, r, f, n) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(initial, { initial = it }, label = { Text("Capital inicial (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(monthly, { monthly = it }, label = { Text("Aportación mensual (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(years, { years = it }, label = { Text("Duración (años)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ret, { ret = it }, label = { Text("Rendimiento anual hipotético (%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(fee, { fee = it }, label = { Text("Comisión anual (%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(infl, { infl = it }, label = { Text("Inflación anual estimada (%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { calc() }) { Text("Calcular") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        results?.forEach { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("${s.label} (rendimiento ${ratePct(s.returnBp)})", style = MaterialTheme.typography.titleMedium)
                    Text("Aportado: ${Money.format(s.contributedCents)}")
                    Text("Valor final nominal: ${Money.format(s.finalNominalCents)}")
                    Text("Valor final ajustado por inflación: ${Money.format(s.finalRealCents)}")
                    Text("Ganancia/pérdida nominal: ${Money.format(s.finalNominalCents - s.contributedCents)}")
                }
            }
        }
        if (results != null) Text(
            "Escenarios: desfavorable = rendimiento −3 puntos, favorable = +3 puntos (puede dar resultados negativos). " +
                "Capitalización mensual, aportaciones al final de cada mes, comisión restada del rendimiento. " +
                "Son proyecciones hipotéticas, no predicciones ni garantías; los rendimientos pasados no aseguran resultados futuros. No es asesoría de inversión.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

// ---------------- Precio por unidad ----------------
private data class UnitEntry(val name: String, val priceCents: Long, val qty: BigDecimal, val measure: Measure)

@Composable
fun UnitScreen() {
    var entries by remember { mutableStateOf(listOf<UnitEntry>()) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("") }
    var measure by remember { mutableStateOf(Measure.G) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Escribe tú los precios que ves en la tienda; la app no trae precios de ningún comercio.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it }, label = { Text("Producto / tienda") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(price, { price = it }, label = { Text("Precio (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(qty, { qty = it }, label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Measure.values().forEach { m -> FilterChip(measure == m, { measure = m }, { Text(m.label) }) }
        }
        Button(onClick = {
            val p = Money.parseToCents(price)
            val q = runCatching { BigDecimal(qty.trim().replace(',', '.')) }.getOrNull()
            when {
                p == null -> error = "Precio inválido."
                q == null || q.signum() <= 0 || q > BigDecimal(1_000_000) -> error = "Cantidad inválida."
                else -> {
                    entries = entries + UnitEntry(name.trim().ifBlank { "Producto ${entries.size + 1}" }, p, q, measure)
                    name = ""; price = ""; qty = ""; error = null
                }
            }
        }) { Text("Agregar a la comparación") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        val groups = entries.groupBy { it.measure.dim }
        if (groups.size > 1) Text("Hay productos de tipos distintos (masa, volumen o unidades): solo se comparan dentro de cada grupo.", color = MaterialTheme.colorScheme.error)
        groups.forEach { (_, list) ->
            val sorted = list.sortedBy { UnitPrice.normalized(it.priceCents, it.qty, it.measure) }
            sorted.forEachIndexed { idx, e ->
                val v = UnitPrice.normalized(e.priceCents, e.qty, e.measure).setScale(3, RoundingMode.HALF_UP).toPlainString()
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(e.name + if (idx == 0 && sorted.size > 1) "  ★ más barato" else "", style = MaterialTheme.typography.titleSmall)
                        Text("${Money.format(e.priceCents)} por ${e.qty.stripTrailingZeros().toPlainString()} ${e.measure.label}")
                        Text("$$v CAD ${e.measure.perLabel}")
                    }
                }
            }
        }
        if (entries.isNotEmpty()) OutlinedButton(onClick = { entries = emptyList() }) { Text("Limpiar comparación") }
    }
}
