package com.finanzas.inteligentes.ui

import android.app.Application
import android.net.Uri
import androidx.room.withTransaction
import com.finanzas.inteligentes.data.Export
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.finanzas.inteligentes.data.*
import com.finanzas.inteligentes.domain.Finance
import com.finanzas.inteligentes.domain.Money
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)
    private val dao = db.dao()

    private fun <T> Flow<T>.state(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val accounts = dao.accounts().state(emptyList())
    val txs = dao.txs().state(emptyList())
    val budgets = dao.budgets().state(emptyList())
    val goals = dao.goals().state(emptyList())
    val debts = dao.debts().state(emptyList())
    val subs = dao.subs().state(emptyList())
    val debtPayments = dao.debtPayments().state(emptyList())
    val shopLists = dao.shopLists().state(emptyList())
    val shopItems = dao.shopItems().state(emptyList())
    val priceObs = dao.priceObs().state(emptyList())

    var month by mutableStateOf(YearMonth.now())
    var message by mutableStateOf<String?>(null)

    fun available(): Long =
        Finance.totalBalance(accounts.value, txs.value) - goals.value.sumOf { it.savedCents }

    private fun launchSafe(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() } catch (e: Exception) { message = "No se pudo guardar. Inténtelo de nuevo." }
        }
    }

    fun addAccount(name: String, openingCents: Long) = launchSafe {
        dao.insertAccount(AccountEntity(name = name, openingCents = openingCents))
    }

    fun saveTx(t: TxEntity) = launchSafe {
        if (t.id == 0L) dao.insertTx(t) else dao.updateTx(t)
    }

    fun deleteTx(id: Long) = launchSafe { dao.deleteTx(id) }

    fun addBudget(category: String, limitCents: Long, weekly: Int = 0) {
        if (budgets.value.any { it.category.equals(category, ignoreCase = true) && it.weekly == weekly }) {
            message = "Ya existe un presupuesto para esa categoría."
            return
        }
        launchSafe { dao.insertBudget(BudgetEntity(category = category, limitCents = limitCents, weekly = weekly)) }
    }

    fun deleteBudget(id: Long) = launchSafe { dao.deleteBudget(id) }

    fun addGoal(name: String, targetCents: Long, targetEpochDay: Long?) = launchSafe {
        dao.insertGoal(GoalEntity(name = name, targetCents = targetCents, savedCents = 0, targetEpochDay = targetEpochDay))
    }

    /** Asigna dinero ya existente en las cuentas a una meta (no crea ingreso ni gasto). */
    fun contribute(goal: GoalEntity, cents: Long) {
        if (cents > available()) {
            message = "No hay suficiente dinero disponible (${Money.format(available())})."
            return
        }
        if (goal.savedCents + cents > goal.targetCents) {
            message = "La aportación supera la meta."
            return
        }
        launchSafe { dao.addToGoal(goal.id, cents) }
    }

    fun deleteGoal(id: Long) = launchSafe { dao.deleteGoal(id) }

    fun addDebt(d: DebtEntity) = launchSafe { dao.insertDebt(d) }
    fun deleteDebt(id: Long) = launchSafe { dao.deleteDebt(id) }
    fun addSub(s: SubEntity) = launchSafe { dao.insertSub(s) }
    fun deleteSub(id: Long) = launchSafe { dao.deleteSub(id) }

    fun payDebt(d: DebtEntity, cents: Long) {
        if (cents > d.balanceCents) { message = "El pago supera el saldo de la deuda."; return }
        launchSafe {
            db.withTransaction {
                dao.reduceDebt(d.id, cents)
                dao.insertPayment(DebtPaymentEntity(debtId = d.id, amountCents = cents, epochDay = LocalDate.now().toEpochDay()))
            }
        }
    }

    private fun export(uri: Uri, content: () -> String) = launchSafe {
        withContext(Dispatchers.IO) {
            val out = getApplication<Application>().contentResolver.openOutputStream(uri) ?: throw IllegalStateException()
            out.use { it.write(content().toByteArray(Charsets.UTF_8)) }
        }
        message = "Archivo guardado."
    }

    fun exportCsv(uri: Uri) = export(uri) { Export.csv(txs.value, accounts.value) }
    fun exportJson(uri: Uri) = export(uri) {
        Export.json(accounts.value, txs.value, budgets.value, goals.value, debts.value, debtPayments.value, subs.value)
    }

    fun deleteAllData() = launchSafe {
        withContext(Dispatchers.IO) { db.clearAllTables() }
        message = "Datos financieros eliminados."
    }

    fun addShopList(name: String, budgetCents: Long) = launchSafe { dao.insertShopList(ShopListEntity(name = name, budgetCents = budgetCents)) }
    fun deleteShopList(id: Long) = launchSafe { db.withTransaction { dao.deleteItemsOfList(id); dao.deleteShopList(id) } }
    fun addShopItem(i: ShopItemEntity) = launchSafe { dao.insertShopItem(i) }
    fun toggleItem(i: ShopItemEntity) = launchSafe { dao.setChecked(i.id, !i.checked) }
    fun deleteShopItem(id: Long) = launchSafe { dao.deleteShopItem(id) }
    fun addPriceObs(o: PriceObsEntity) = launchSafe { dao.insertPriceObs(o) }
    fun deletePriceObs(id: Long) = launchSafe { dao.deletePriceObs(id) }
}
