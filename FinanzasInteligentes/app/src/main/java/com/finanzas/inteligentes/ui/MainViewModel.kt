package com.finanzas.inteligentes.ui

import android.app.Application
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
    private val dao = AppDb.get(app).dao()

    private fun <T> Flow<T>.state(init: T) = stateIn(viewModelScope, SharingStarted.Eagerly, init)

    val accounts = dao.accounts().state(emptyList())
    val txs = dao.txs().state(emptyList())
    val budgets = dao.budgets().state(emptyList())
    val goals = dao.goals().state(emptyList())
    val debts = dao.debts().state(emptyList())
    val subs = dao.subs().state(emptyList())

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

    fun addBudget(category: String, limitCents: Long) {
        if (budgets.value.any { it.category.equals(category, ignoreCase = true) }) {
            message = "Ya existe un presupuesto para esa categoría."
            return
        }
        launchSafe { dao.insertBudget(BudgetEntity(category = category, limitCents = limitCents)) }
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
}
