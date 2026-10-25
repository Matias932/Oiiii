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
    val limitCents: Long,
    @ColumnInfo(defaultValue = "0") val weekly: Int = 0
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

@Entity(tableName = "debt_payments")
data class DebtPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val debtId: Long,
    val amountCents: Long,
    val epochDay: Long
)

@Entity(tableName = "shop_lists")
data class ShopListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val budgetCents: Long
)

@Entity(tableName = "shop_items")
data class ShopItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val name: String,
    val quantity: Int,
    val priceCents: Long,
    val checked: Boolean
)

@Entity(tableName = "price_obs")
data class PriceObsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val product: String,
    val store: String,
    val priceCents: Long,
    val qty: String,
    val measure: String,
    val epochDay: Long,
    val source: String
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
    @Query("SELECT * FROM debt_payments ORDER BY epochDay DESC, id DESC") fun debtPayments(): Flow<List<DebtPaymentEntity>>
    @Insert suspend fun insertPayment(p: DebtPaymentEntity): Long
    @Query("UPDATE debts SET balanceCents = balanceCents - :cents WHERE id = :id")
    suspend fun reduceDebt(id: Long, cents: Long)

    @Query("SELECT * FROM shop_lists ORDER BY id") fun shopLists(): Flow<List<ShopListEntity>>
    @Query("SELECT * FROM shop_items ORDER BY id") fun shopItems(): Flow<List<ShopItemEntity>>
    @Query("SELECT * FROM price_obs ORDER BY epochDay DESC, id DESC") fun priceObs(): Flow<List<PriceObsEntity>>
    @Insert suspend fun insertShopList(l: ShopListEntity): Long
    @Insert suspend fun insertShopItem(i: ShopItemEntity): Long
    @Insert suspend fun insertPriceObs(o: PriceObsEntity): Long
    @Query("UPDATE shop_items SET checked = :checked WHERE id = :id") suspend fun setChecked(id: Long, checked: Boolean)
    @Query("DELETE FROM shop_items WHERE id = :id") suspend fun deleteShopItem(id: Long)
    @Query("DELETE FROM shop_items WHERE listId = :listId") suspend fun deleteItemsOfList(listId: Long)
    @Query("DELETE FROM shop_lists WHERE id = :id") suspend fun deleteShopList(id: Long)
    @Query("DELETE FROM price_obs WHERE id = :id") suspend fun deletePriceObs(id: Long)

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
    entities = [AccountEntity::class, TxEntity::class, BudgetEntity::class, GoalEntity::class, DebtEntity::class, SubEntity::class, DebtPaymentEntity::class, ShopListEntity::class, ShopItemEntity::class, PriceObsEntity::class],
    version = 4,
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

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `debt_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `debtId` INTEGER NOT NULL, `amountCents` INTEGER NOT NULL, `epochDay` INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `budgets` ADD COLUMN `weekly` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE TABLE IF NOT EXISTS `shop_lists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `budgetCents` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `shop_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `listId` INTEGER NOT NULL, `name` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `priceCents` INTEGER NOT NULL, `checked` INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `price_obs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `product` TEXT NOT NULL, `store` TEXT NOT NULL, `priceCents` INTEGER NOT NULL, `qty` TEXT NOT NULL, `measure` TEXT NOT NULL, `epochDay` INTEGER NOT NULL, `source` TEXT NOT NULL)")
            }
        }

        @Volatile private var instance: AppDb? = null
        fun get(c: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "finanzas.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
