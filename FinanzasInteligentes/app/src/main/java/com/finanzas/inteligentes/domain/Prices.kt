package com.finanzas.inteligentes.domain

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

// ---------- Recibos ----------
data class ReceiptDraft(val store: String?, val day: LocalDate?, val totalCents: Long?)

/**
 * Extracción heurística: solo tienda (primera línea con letras), fecha en formato AAAA-MM-DD y total.
 * No separa productos ni impuestos. El resultado es un BORRADOR que el usuario debe revisar.
 * Limitación: totales con separador de miles ("1 234,56") pueden leerse mal.
 */
object ReceiptParser {
    private val amount = Regex("""(?<!\d)(\d{1,6})[.,](\d{2})(?!\d)""")
    private val iso = Regex("""(\d{4})-(\d{2})-(\d{2})""")
    private val excluded = listOf("sous", "sub", "tps", "tvq", "gst", "qst", "hst", "tax")

    fun parse(text: String): ReceiptDraft {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val store = lines.firstOrNull { l -> l.any { it.isLetter() } }?.take(40)
        val totalLines = lines.filter { l ->
            val low = l.lowercase()
            "total" in low && excluded.none { it in low }
        }
        var total: Long? = null
        for (l in totalLines.reversed()) {
            val m = amount.findAll(l).lastOrNull() ?: continue
            total = m.groupValues[1].toLong() * 100 + m.groupValues[2].toLong()
            break
        }
        val day = iso.find(text)?.let { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()
        }
        return ReceiptDraft(store, day, total)
    }
}

// ---------- Historial de precios ----------
data class Obs(
    val id: Long, val product: String, val store: String, val priceCents: Long,
    val qty: BigDecimal, val measure: Measure, val epochDay: Long, val source: String
) {
    val normalized: BigDecimal get() = UnitPrice.normalized(priceCents, qty, measure)
}

data class PriceSummary(val latest: Obs, val previous: Obs?, val minObs: Obs, val changePct: BigDecimal?, val comparable: Int)

object PriceHistory {
    /** Solo se comparan observaciones de la misma dimensión (masa, volumen o unidades) que la más reciente. */
    fun summary(all: List<Obs>): PriceSummary? {
        if (all.isEmpty()) return null
        val latest = all.maxWithOrNull(compareBy<Obs> { it.epochDay }.thenBy { it.id }) ?: return null
        val same = all.filter { it.measure.dim == latest.measure.dim }
            .sortedWith(compareByDescending<Obs> { it.epochDay }.thenByDescending { it.id })
        val previous = same.getOrNull(1)
        val minObs = same.minByOrNull { it.normalized } ?: latest
        val change = previous?.takeIf { it.normalized.signum() > 0 }?.let {
            latest.normalized.subtract(it.normalized).multiply(BigDecimal(100))
                .divide(it.normalized, 1, RoundingMode.HALF_UP)
        }
        return PriceSummary(latest, previous, minObs, change, same.size)
    }
}

/** Punto de extensión: cada proveedor de precios (API autorizada, catálogo público) implementa esta interfaz. */
interface PriceSource {
    val name: String
    /** true solo si la fuente es verificable y se usa respetando sus condiciones de uso. */
    val verifiable: Boolean
    suspend fun fetch(query: String): List<Obs>
}

/** Única fuente incluida: precios que escribe el usuario. No se conecta a ningún comercio. */
object ManualSource : PriceSource {
    override val name = "manual"
    override val verifiable = false
    override suspend fun fetch(query: String): List<Obs> = emptyList()
}
