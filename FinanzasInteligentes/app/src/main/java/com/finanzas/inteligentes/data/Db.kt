package com.finanzas.inteligentes.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

enum class TxType { INCOME, EXPENSE, TRANSFER }

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val openingCents: Long
)

@Entity(tableName = "transactions")
data class TxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amountCents: Long,
    val category: String,
    val epochDay: Long,
    val description: String,
    val notes: String,
    val accountId: Long,
    val toAccountId: Long?
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val limitCents: Long
)

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetCents: Long,
    val savedCents: Long,
    val targetEpochDay: Long?
)

@Entity(tableName = "debts")
data class DebtEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val balanceCents: Long,
    val rateBp: Int,
    val minPaymentCents: Long
)

@Entity(tableName = "subscriptions")
data class SubEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amountCents: Long,
    val frequency: String,
    val nextEpochDay: Long,
    val category: String,
    val notes: String
)

@Dao
interface FinDao {
    @Query("SELECT * FROM accounts ORDER BY id") fun accounts(): Flow<List<AccountEntity>>
    @Query("SELECT * FROM transactions ORDER BY epochDay DESC, id DESC") fun txs(): Flow<List<TxEntity>>
    @Query("SELECT * FROM budgets ORDER BY category") fun budgets(): Flow<List<BudgetEntity>>
    @Query("SELECT * FROM goals ORDER BY id") fun goals(): Flow<List<GoalEntity>>

    @Insert suspend fun insertAccount(a: AccountEntity): Long
    @Insert suspend fun insertTx(t: TxEntity): Long
    @Update suspend fun updateTx(t: TxEntity)
    @Query("DELETE FROM transactions WHERE id = :id") suspend fun deleteTx(id: Long)

    @Insert suspend fun insertBudget(b: BudgetEntity): Long
    @Query("DELETE FROM budgets WHERE id = :id") suspend fun deleteBudget(id: Long)

    @Query("SELECT * FROM debts ORDER BY id") fun debts(): Flow<List<DebtEntity>>
    @Insert suspend fun insertDebt(d: DebtEntity): Long
    @Query("DELETE FROM debts WHERE id = :id") suspend fun deleteDebt(id: Long)

    @Query("SELECT * FROM subscriptions ORDER BY name") fun subs(): Flow<List<SubEntity>>
    @Insert suspend fun insertSub(s: SubEntity): Long
    @Query("DELETE FROM subscriptions WHERE id = :id") suspend fun deleteSub(id: Long)

    @Insert suspend fun insertGoal(g: GoalEntity): Long
    @Query("UPDATE goals SET savedCents = savedCents + :cents WHERE id = :id")
    suspend fun addToGoal(id: Long, cents: Long)
    @Query("DELETE FROM goals WHERE id = :id") suspend fun deleteGoal(id: Long)
}

@Database(
    entities = [AccountEntity::class, TxEntity::class, BudgetEntity::class, GoalEntity::class, DebtEntity::class, SubEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): FinDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `debts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `balanceCents` INTEGER NOT NULL, `rateBp` INTEGER NOT NULL, `minPaymentCents` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `subscriptions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amountCents` INTEGER NOT NULL, `frequency` TEXT NOT NULL, `nextEpochDay` INTEGER NOT NULL, `category` TEXT NOT NULL, `notes` TEXT NOT NULL)")
            }
        }

        @Volatile private var instance: AppDb? = null
        fun get(c: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "finanzas.db")
                .addMigrations(MIGRATION_1_2)
                .build().also { instance = it }
        }
    }
}

object Categories {
    val expense = listOf(
        "Vivienda", "Alimentación", "Transporte", "Servicios", "Teléfono e internet",
        "Salud", "Ropa", "Entretenimiento", "Compras", "Otros"
    )
    val income = listOf("Sueldo", "Ingreso adicional", "Trabajo independiente", "Otros ingresos")
    const val TRANSFER = "Transferencia"
}
