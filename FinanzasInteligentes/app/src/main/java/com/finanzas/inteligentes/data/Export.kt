package com.finanzas.inteligentes.data

import com.finanzas.inteligentes.domain.Money
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Exportación local. Los archivos NO van cifrados: guárdelos en un lugar seguro. */
object Export {
    private fun cell(s: String): String {
        var v = s
        if (v.isNotEmpty() && v[0] in "=+-@\t\r") v = "'" + v // evita fórmulas al abrir en hojas de cálculo
        return "\"" + v.replace("\"", "\"\"") + "\""
    }

    fun csv(txs: List<TxEntity>, accounts: List<AccountEntity>): String {
        val names = accounts.associate { it.id to it.name }
        val sb = StringBuilder("fecha,tipo,categoria,descripcion,importe_CAD,cuenta,cuenta_destino,notas\n")
        for (t in txs) {
            sb.append(
                listOf(
                    LocalDate.ofEpochDay(t.epochDay).toString(), t.type, t.category, t.description,
                    Money.toPlain(t.amountCents), names[t.accountId] ?: "", t.toAccountId?.let { names[it] } ?: "", t.notes
                ).joinToString(",") { cell(it) }
            ).append('\n')
        }
        return sb.toString()
    }

    fun json(
        accounts: List<AccountEntity>, txs: List<TxEntity>, budgets: List<BudgetEntity>, goals: List<GoalEntity>,
        debts: List<DebtEntity>, payments: List<DebtPaymentEntity>, subs: List<SubEntity>
    ): String {
        fun arr(items: List<JSONObject>) = JSONArray().also { a -> items.forEach { a.put(it) } }
        return JSONObject().apply {
            put("app", "Finanzas Inteligentes"); put("currency", Money.CURRENCY); put("version", 4)
            put("accounts", arr(accounts.map { JSONObject().put("id", it.id).put("name", it.name).put("openingCents", it.openingCents) }))
            put("transactions", arr(txs.map {
                JSONObject().put("id", it.id).put("type", it.type).put("amountCents", it.amountCents).put("category", it.category)
                    .put("date", LocalDate.ofEpochDay(it.epochDay).toString()).put("description", it.description)
                    .put("notes", it.notes).put("accountId", it.accountId).put("toAccountId", it.toAccountId ?: JSONObject.NULL)
            }))
            put("budgets", arr(budgets.map { JSONObject().put("category", it.category).put("limitCents", it.limitCents).put("weekly", it.weekly) }))
            put("goals", arr(goals.map {
                JSONObject().put("name", it.name).put("targetCents", it.targetCents).put("savedCents", it.savedCents)
                    .put("targetDate", it.targetEpochDay?.let { d -> LocalDate.ofEpochDay(d).toString() } ?: JSONObject.NULL)
            }))
            put("debts", arr(debts.map {
                JSONObject().put("id", it.id).put("name", it.name).put("balanceCents", it.balanceCents)
                    .put("rateBp", it.rateBp).put("minPaymentCents", it.minPaymentCents)
            }))
            put("debtPayments", arr(payments.map {
                JSONObject().put("debtId", it.debtId).put("amountCents", it.amountCents).put("date", LocalDate.ofEpochDay(it.epochDay).toString())
            }))
            put("subscriptions", arr(subs.map {
                JSONObject().put("name", it.name).put("amountCents", it.amountCents).put("frequency", it.frequency)
                    .put("nextDate", LocalDate.ofEpochDay(it.nextEpochDay).toString()).put("category", it.category).put("notes", it.notes)
            }))
        }.toString(2)
    }
}
