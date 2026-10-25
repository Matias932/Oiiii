package com.finanzas.inteligentes.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import com.finanzas.inteligentes.data.Reminders
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.finanzas.inteligentes.data.AppState
import com.finanzas.inteligentes.data.Security
import com.finanzas.inteligentes.domain.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.YearMonth

// ---------------- Estadísticas ----------------
@Composable
fun StatsScreen(vm: MainViewModel) {
    val txs by vm.txs.collectAsState()
    val now = YearMonth.now()
    val stats = Stats.series(txs, now, 6)
    val maxV = stats.maxOf { maxOf(it.incomeCents, it.expenseCents) }.coerceAtLeast(1L)
    val avgExp = Stats.averageExpense(Stats.series(txs, now.minusMonths(1), 3))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (txs.isEmpty()) EmptyState("Sin datos todavía", "Registre ingresos y gastos para ver su evolución mensual.")
        stats.forEach { s ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${s.month.monthValue}/${s.month.year}", style = MaterialTheme.typography.titleSmall)
                    Text("Ingresos ${Money.format(s.incomeCents)}")
                    LinearProgressIndicator(progress = { s.incomeCents.toFloat() / maxV }, Modifier.fillMaxWidth())
                    Text("Gastos ${Money.format(s.expenseCents)}")
                    LinearProgressIndicator(progress = { s.expenseCents.toFloat() / maxV }, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error)
                    Text("Balance del mes: ${Money.format(s.netCents)}")
                }
            }
        }
        Text(
            if (avgExp == null) "Aún no hay suficientes meses completos con datos para estimar gastos futuros."
            else "Referencia: sus gastos promedio de los últimos 3 meses completos con datos son ${Money.format(avgExp)} al mes. Es un promedio simple, no una predicción.",
            style = MaterialTheme.typography.bodySmall
        )
        Text("Las transferencias entre cuentas no se cuentan como ingresos ni gastos.", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- Simulador de decisiones ----------------
@Composable
fun DecisionScreen(vm: MainViewModel) {
    val txs by vm.txs.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val goals by vm.goals.collectAsState()
    var purchase by remember { mutableStateOf("") }
    var monthly by remember { mutableStateOf("") }
    var months by remember { mutableStateOf("12") }
    val p = if (purchase.isBlank()) 0L else Money.parseToCents(purchase, true)
    val mc = if (monthly.isBlank()) 0L else Money.parseToCents(monthly, true)
    val n = months.trim().toIntOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Pruebe una compra o un pago mensual sin registrarlo: esta simulación no crea movimientos.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(purchase, { purchase = it }, label = { Text("Pago único hoy (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(monthly, { monthly = it }, label = { Text("Costo mensual adicional (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(months, { months = it }, label = { Text("Durante cuántos meses") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (p == null || mc == null || n == null || n !in 1..600) Text("Revise los importes y los meses (1 a 600).", color = MaterialTheme.colorScheme.error)
        else if (p == 0L && mc == 0L) Text("Escriba un importe para simular.")
        else {
            val available = Finance.totalBalance(accounts, txs) - goals.sumOf { it.savedCents }
            val avgNet = Stats.averageNet(Stats.series(txs, YearMonth.now().minusMonths(1), 3))
            val r = Decision.analyze(available, avgNet, p, mc, n)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Disponible hoy: ${Money.format(available)}")
                    Text("Disponible tras el pago único: ${Money.format(r.availableAfterCents)}")
                    Text("Costo total en $n meses: ${Money.format(r.totalCostCents)}")
                    if (r.monthlyNetAfterCents != null) Text("Balance mensual promedio con este costo: ${Money.format(r.monthlyNetAfterCents)} (promedio de los últimos 3 meses con datos)")
                    else Text("No hay historial suficiente para estimar su balance mensual.")
                    if (r.availableAfterCents < 0) Text("Advertencia: hoy no tiene suficiente dinero disponible para este pago.", color = MaterialTheme.colorScheme.error)
                    if ((r.monthlyNetAfterCents ?: 0L) < 0L) Text("Advertencia: con su promedio reciente, el balance mensual quedaría negativo.", color = MaterialTheme.colorScheme.error)
                    if (goals.isNotEmpty() && r.availableAfterCents < 0) Text("Podría afectar el dinero que tiene asignado a sus metas de ahorro.")
                }
            }
            Text("Supuestos: ingresos y gastos futuros iguales al promedio reciente; sin intereses ni impuestos adicionales. Es una estimación.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------- Gana más ----------------
@Composable
fun EarnScreen() {
    var gross by remember { mutableStateOf("") }
    var ops by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("") }
    var materials by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    fun c(s: String) = if (s.isBlank()) 0L else Money.parseToCents(s, true)
    val g = c(gross); val o = c(ops); val t = c(transport); val m = c(materials)
    val h = if (hours.isBlank()) null else hours.trim().replace(',', '.').toBigDecimalOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Calculadora de ganancia de una actividad extra (por mes)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(gross, { gross = it }, label = { Text("Ingresos brutos estimados (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(ops, { ops = it }, label = { Text("Gastos operativos (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(transport, { transport = it }, label = { Text("Transporte (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(materials, { materials = it }, label = { Text("Materiales (CAD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(hours, { hours = it }, label = { Text("Horas dedicadas (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (g == null || o == null || t == null || m == null || (hours.isNotBlank() && (h == null || h.signum() <= 0))) {
            Text("Revise los importes: use números positivos con hasta 2 decimales.", color = MaterialTheme.colorScheme.error)
        } else {
            val net = g - o - t - m
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Ingresos brutos: ${Money.format(g)}")
                    Text("Costos totales: ${Money.format(o + t + m)}")
                    Text("Ganancia neta antes de impuestos: ${Money.format(net)}", style = MaterialTheme.typography.titleMedium)
                    if (h != null) Text("Por hora trabajada: ${Money.format(BigDecimal(net).divide(h, 0, RoundingMode.HALF_UP).toLong())}")
                    Text("No se calculan impuestos: dependen de su situación y del tipo de ingreso. Consulte Revenu Québec o la ARC.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Señales de alerta", style = MaterialTheme.typography.titleMedium)
        Text("• Promesas de ganancias garantizadas o \"dinero fácil\".\n• Piden dinero por adelantado para darle trabajo o materiales.\n• Esquemas donde debe reclutar a otros.\n• Ofertas que llegan sin que usted las busque, con prisa para aceptar.\n• Piden sus datos bancarios o de identidad antes de un contrato claro.\nVerifique siempre la empresa y no pague por adelantado.")
        Text("Esta sección no incluye ofertas de trabajo: la app no tiene fuentes de empleo conectadas.", style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- Aprender ----------------
private val lessons = listOf(
    "Cuenta de ahorro con intereses" to "Guarda su dinero y paga un interés que puede cambiar. Es fácil de retirar (alta liquidez).",
    "GIC (certificado de inversión garantizado)" to "Usted deja el dinero un plazo fijo y recibe una tasa conocida. Suele estar bloqueado hasta el vencimiento. Verifique si tiene seguro de depósitos y hasta qué monto.",
    "Bonos" to "Son préstamos a gobiernos o empresas que pagan interés. Su precio puede subir o bajar si cambian las tasas, y el emisor podría no pagar.",
    "Acciones" to "Partes de una empresa. Pueden ganar o perder valor, incluso quedarse en cero.",
    "ETF y fondos de inversión" to "Agrupan muchas inversiones en una. Tienen comisiones anuales que reducen el rendimiento: compárelas.",
    "Diversificación" to "No concentrar todo en una sola inversión reduce el riesgo, pero no lo elimina.",
    "Inflación" to "Los precios suben con el tiempo; un rendimiento menor que la inflación hace perder poder de compra.",
    "Interés compuesto" to "Los intereses también generan intereses. Con el tiempo el efecto crece, y también cuando se trata de una deuda.",
    "Invertir vs. especular" to "Invertir busca crecer con un plan a largo plazo; especular apuesta a movimientos rápidos de precio y suele implicar mucho más riesgo.",
    "CELI (TFSA)" to "Cuenta registrada donde el crecimiento no se grava. Tiene un límite de aportación; verifique su espacio disponible y las reglas vigentes en su cuenta de la ARC.",
    "REER (RRSP)" to "Cuenta registrada para el retiro: las aportaciones pueden reducir el ingreso gravable, y los retiros normalmente se gravan. Límites y reglas cambian: verifíquelos en la ARC.",
    "Cuenta no registrada" to "Sin límite de aportación ni ventajas fiscales; los rendimientos suelen tributar. El trato fiscal depende del producto y de su situación."
)
private val flags = listOf(
    "Promete ganancias garantizadas o sin riesgo",
    "Presiona para decidir rápido",
    "Pide dinero por adelantado o por transferencia/cripto a un particular",
    "No puede mostrar su registro como empresa o asesor",
    "Lo contactó sin que usted lo buscara (redes, mensajes)",
    "No logra explicar con claridad cómo genera las ganancias"
)

@Composable
fun LearnScreen() {
    val uri = LocalUriHandler.current
    val checks = remember { mutableStateListOf(*Array(flags.size) { false }) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Contenido educativo general: no es asesoría financiera ni una recomendación de inversión. Ninguna inversión es segura ni tiene rendimientos asegurados.", style = MaterialTheme.typography.bodySmall)
        lessons.forEach { (t, b) ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text(t, style = MaterialTheme.typography.titleSmall); Text(b) } }
        }
        Text("¿Es una posible estafa?", style = MaterialTheme.typography.titleMedium)
        flags.forEachIndexed { i, f ->
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checks[i], { checks[i] = it }); Text(f)
            }
        }
        val n = checks.count { it }
        Text(
            if (n == 0) "Sin señales marcadas. Eso no demuestra que la oferta sea legítima: verifique siempre a la empresa y a la persona en fuentes oficiales."
            else "$n señal(es) de alerta. Sea muy cauteloso: no envíe dinero y verifique en fuentes oficiales.",
            color = if (n > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Text("Fuentes oficiales", style = MaterialTheme.typography.titleMedium)
        listOf(
            "Autorité des marchés financiers (Québec)" to "https://lautorite.qc.ca",
            "Agence de la consommation en matière financière du Canada" to "https://www.canada.ca/en/financial-consumer-agency.html",
            "Revenu Québec" to "https://www.revenuquebec.ca",
            "Agence du revenu du Canada" to "https://www.canada.ca/en/revenue-agency.html"
        ).forEach { (n2, u) -> OutlinedButton(onClick = { uri.openUri(u) }, modifier = Modifier.fillMaxWidth()) { Text(n2) } }
    }
}

// ---------------- Ajustes y seguridad ----------------
@Composable
fun SettingsScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val act = ctx as? FragmentActivity
    val csv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { u -> if (u != null) vm.exportCsv(u) }
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u -> if (u != null) vm.exportJson(u) }
    var pinDialog by remember { mutableStateOf(false) }
    var removePin by remember { mutableStateOf(false) }
    var wipe by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Apariencia", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Sistema", "Claro", "Oscuro").forEachIndexed { i, l ->
                FilterChip(AppState.themeMode == i, { Security.setTheme(ctx, i) }, { Text(l) })
            }
        }
        Text("Seguridad", style = MaterialTheme.typography.titleMedium)
        if (!AppState.pinSet) {
            Button(onClick = { pinDialog = true }) { Text("Activar bloqueo con PIN") }
        } else {
            Text("Bloqueo con PIN activo. La app se bloquea al salir de ella.")
            OutlinedButton(onClick = { pinDialog = true }) { Text("Cambiar PIN") }
            OutlinedButton(onClick = { removePin = true }) { Text("Quitar PIN") }
            if (act != null && biometricAvailable(act)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(AppState.biometric, { Security.setBiometric(ctx, it) })
                    Text("Desbloquear con huella o rostro")
                }
            } else Text("Huella/rostro no disponible en este dispositivo.", style = MaterialTheme.typography.bodySmall)
        }
        Text("Recordatorios", style = MaterialTheme.typography.titleMedium)
        val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) Reminders.enable(ctx) else vm.message = "Sin permiso de notificaciones no se pueden mostrar recordatorios."
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(AppState.reminders, { on ->
                if (!on) Reminders.disable(ctx)
                else if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                else Reminders.enable(ctx)
            })
            Text("Avisar de pagos previstos (hoy o mañana)")
        }
        Text("Se revisa aproximadamente una vez al día; Android puede retrasar la hora. El aviso no muestra nombres ni montos.", style = MaterialTheme.typography.bodySmall)
        Text("Privacidad", style = MaterialTheme.typography.titleMedium)
        Text("Sus datos se guardan solo en este teléfono. La app no tiene permiso de Internet, por lo que no envía información a ningún servidor.")
        Text("Exportar", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { csv.launch("movimientos.csv") }) { Text("Exportar movimientos (CSV)") }
        Button(onClick = { json.launch("copia_finanzas.json") }) { Text("Exportar copia completa (JSON)") }
        Text("Los archivos exportados no están cifrados: guárdelos en un lugar seguro. Por ahora no hay función para restaurar una copia.", style = MaterialTheme.typography.bodySmall)
        Text("Zona de riesgo", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { wipe = true }) { Text("Eliminar todos los datos financieros", color = MaterialTheme.colorScheme.error) }
    }

    if (pinDialog) PinDialog(onClose = { pinDialog = false })
    if (removePin) ConfirmDialog("¿Quitar el PIN?", "La app dejará de pedir PIN al abrirse.", { Security.clearPin(ctx); removePin = false }) { removePin = false }
    if (wipe) WipeDialog(onConfirm = { vm.deleteAllData(); wipe = false }) { wipe = false }
}

@Composable
private fun PinDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val kb = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    AlertDialog(
        onDismissRequest = onClose, title = { Text("PIN de 4 a 8 dígitos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(a, { a = it.filter(Char::isDigit).take(8) }, label = { Text("Nuevo PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                OutlinedTextField(b, { b = it.filter(Char::isDigit).take(8) }, label = { Text("Repita el PIN") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = kb)
                Text("Si olvida el PIN, solo podrá recuperar el acceso borrando los datos de la app.", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    a.length < 4 -> error = "El PIN debe tener al menos 4 dígitos."
                    a != b -> error = "Los PIN no coinciden."
                    else -> { Security.setPin(ctx, a); onClose() }
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}

@Composable
private fun WipeDialog(onConfirm: () -> Unit, onClose: () -> Unit) {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("Eliminar todos los datos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se borrarán cuentas, movimientos, presupuestos, metas, deudas y suscripciones. No se puede deshacer. Escriba BORRAR para confirmar.")
                OutlinedTextField(t, { t = it }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = t.trim() == "BORRAR") { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } }
    )
}
