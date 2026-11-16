package com.katowproject.costly

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.File

val Indonesian: Locale = Locale.forLanguageTag("id-ID")
fun rupiah(amount: Long): String = "Rp " + NumberFormat.getIntegerInstance(Indonesian).format(amount)
fun monthLabel(month: YearMonth): String = month.format(DateTimeFormatter.ofPattern("MMM yyyy", Indonesian))
fun dateLabel(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d MMM yyyy", Indonesian))

data class Category(val id: Long, val name: String, val type: String, val icon: String, val color: Long)
data class Wallet(val id: Long, val name: String, val type: String, val initial: Long)
data class Entry(val id: Long = 0, val type: String, val amount: Long, val categoryId: Long, val walletId: Long, val date: LocalDate, val note: String, val attachment: String = "", val recurringKey: String = "")
data class Budget(val id: Long, val categoryId: Long, val amount: Long, val month: String)
data class Goal(val id: Long, val name: String, val target: Long, val saved: Long, val deadline: String)
data class Debt(val id: Long, val name: String, val type: String, val amount: Long, val remaining: Long, val due: String)
data class Recurring(val id: Long, val name: String, val type: String, val amount: Long, val categoryId: Long, val walletId: Long, val frequency: String, val nextDate: LocalDate)
data class FinanceData(
    val entries: List<Entry> = emptyList(), val categories: List<Category> = emptyList(),
    val wallets: List<Wallet> = emptyList(), val budgets: List<Budget> = emptyList(),
    val goals: List<Goal> = emptyList(), val debts: List<Debt> = emptyList(),
    val recurring: List<Recurring> = emptyList(), val settings: Map<String, String> = emptyMap()
) {
    fun category(id: Long) = categories.firstOrNull { it.id == id }
    fun wallet(id: Long) = wallets.firstOrNull { it.id == id }
    fun monthly(month: YearMonth) = entries.filter { YearMonth.from(it.date) == month }
    fun total(type: String, month: YearMonth) = monthly(month).filter { it.type == type }.sumOf { it.amount }
    fun spent(categoryId: Long, month: YearMonth) = monthly(month).filter { it.type == "EXPENSE" && it.categoryId == categoryId }.sumOf { it.amount }
    fun balance(walletId: Long? = null): Long = wallets.filter { walletId == null || it.id == walletId }.sumOf { it.initial } + entries.filter { walletId == null || it.walletId == walletId }.sumOf { if (it.type == "INCOME") it.amount else -it.amount }
}

class FinanceStore(context: Context, databaseName: String = "costly.db") : SQLiteOpenHelper(context, databaseName, null, 1) {
    val attachmentsDirectory = File(context.filesDir, "attachments")
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE categories(id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, icon TEXT NOT NULL, color INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE wallets(id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, initial_balance INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE transactions(id INTEGER PRIMARY KEY, type TEXT NOT NULL CHECK(type IN ('INCOME','EXPENSE')), amount INTEGER NOT NULL CHECK(amount > 0), category_id INTEGER NOT NULL REFERENCES categories(id), wallet_id INTEGER NOT NULL REFERENCES wallets(id), date TEXT NOT NULL, note TEXT NOT NULL, attachment TEXT NOT NULL DEFAULT '', recurring_key TEXT UNIQUE)")
        db.execSQL("CREATE TABLE budgets(id INTEGER PRIMARY KEY, category_id INTEGER NOT NULL REFERENCES categories(id), amount INTEGER NOT NULL CHECK(amount > 0), month TEXT NOT NULL, UNIQUE(category_id, month))")
        db.execSQL("CREATE TABLE goals(id INTEGER PRIMARY KEY, name TEXT NOT NULL, target INTEGER NOT NULL CHECK(target > 0), saved INTEGER NOT NULL DEFAULT 0, deadline TEXT NOT NULL)")
        db.execSQL("CREATE TABLE debts(id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, amount INTEGER NOT NULL, remaining INTEGER NOT NULL, due TEXT NOT NULL)")
        db.execSQL("CREATE TABLE recurring_transactions(id INTEGER PRIMARY KEY, name TEXT NOT NULL, type TEXT NOT NULL, amount INTEGER NOT NULL, category_id INTEGER NOT NULL REFERENCES categories(id), wallet_id INTEGER NOT NULL REFERENCES wallets(id), frequency TEXT NOT NULL, next_date TEXT NOT NULL)")
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        val defaults = listOf(
            Triple("Konsumsi", "food", 0xFFF15470L), Triple("Transportasi", "car", 0xFFF4A52CL),
            Triple("Operasional", "bag", 0xFF7482F8L), Triple("Utilitas", "bolt", 0xFF29AECCL),
            Triple("Belanja", "shopping", 0xFFC46FF0L), Triple("Lainnya", "grid", 0xFFAA8CDEL)
        )
        defaults.forEach { (name, icon, color) -> db.insertOrThrow("categories", null, values("name" to name, "type" to "EXPENSE", "icon" to icon, "color" to color)) }
        db.insertOrThrow("categories", null, values("name" to "Gaji", "type" to "INCOME", "icon" to "trend", "color" to 0xFF18A96BL))
        db.insertOrThrow("categories", null, values("name" to "Pendapatan lain", "type" to "INCOME", "icon" to "wallet", "color" to 0xFF31B8A8L))
        db.insertOrThrow("wallets", null, values("name" to "Dompet utama", "type" to "CASH", "initial_balance" to 0L))
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    private fun rows(table: String): List<JSONObject> = readableDatabase.rawQuery("SELECT * FROM $table", null).use { cursor ->
        buildList { while (cursor.moveToNext()) add(JSONObject().apply { cursor.columnNames.forEachIndexed { i, name -> if (!cursor.isNull(i)) put(name, cursor.getString(i)) } }) }
    }
    @Synchronized fun read(): FinanceData = FinanceData(
        entries = rows("transactions").map { Entry(it.getLong("id"), it.getString("type"), it.getLong("amount"), it.getLong("category_id"), it.getLong("wallet_id"), LocalDate.parse(it.getString("date")), it.getString("note"), it.optString("attachment"), it.optString("recurring_key")) }.sortedWith(compareByDescending<Entry> { it.date }.thenByDescending { it.id }),
        categories = rows("categories").map { Category(it.getLong("id"), it.getString("name"), it.getString("type"), it.getString("icon"), it.getLong("color")) },
        wallets = rows("wallets").map { Wallet(it.getLong("id"), it.getString("name"), it.getString("type"), it.getLong("initial_balance")) },
        budgets = rows("budgets").map { Budget(it.getLong("id"), it.getLong("category_id"), it.getLong("amount"), it.getString("month")) },
        goals = rows("goals").map { Goal(it.getLong("id"), it.getString("name"), it.getLong("target"), it.getLong("saved"), it.getString("deadline")) },
        debts = rows("debts").map { Debt(it.getLong("id"), it.getString("name"), it.getString("type"), it.getLong("amount"), it.getLong("remaining"), it.getString("due")) },
        recurring = rows("recurring_transactions").map { Recurring(it.getLong("id"), it.getString("name"), it.getString("type"), it.getLong("amount"), it.getLong("category_id"), it.getLong("wallet_id"), it.getString("frequency"), LocalDate.parse(it.getString("next_date"))) },
        settings = rows("settings").associate { it.getString("key") to it.getString("value") }
    )
    @Synchronized fun saveEntry(entry: Entry) {
        require(entry.amount in 1..999_999_999_999L) { "Jumlah tidak valid" }
        val categoryType = readableDatabase.rawQuery("SELECT type FROM categories WHERE id=?", arrayOf(entry.categoryId.toString())).use { if (it.moveToFirst()) it.getString(0) else "" }
        require(categoryType == entry.type) { "Kategori tidak sesuai jenis transaksi" }
        val v = values("type" to entry.type, "amount" to entry.amount, "category_id" to entry.categoryId, "wallet_id" to entry.walletId, "date" to entry.date.toString(), "note" to entry.note, "attachment" to entry.attachment)
        if (entry.recurringKey.isNotBlank()) v.put("recurring_key", entry.recurringKey)
        if (entry.id == 0L) writableDatabase.insertOrThrow("transactions", null, v) else writableDatabase.update("transactions", v, "id=?", arrayOf(entry.id.toString()))
    }
    @Synchronized fun save(table: String, id: Long = 0L, vararg fields: Pair<String, Any>) {
        require(table in tables)
        val v = values(*fields)
        if (id == 0L) writableDatabase.insertOrThrow(table, null, v) else writableDatabase.update(table, v, "id=?", arrayOf(id.toString()))
    }
    @Synchronized fun delete(table: String, id: Long) {
        require(table in tables && table != "settings")
        writableDatabase.delete(table, "id=?", arrayOf(id.toString()))
    }
    @Synchronized fun setting(key: String, value: String) {
        writableDatabase.insertWithOnConflict("settings", null, values("key" to key, "value" to value), SQLiteDatabase.CONFLICT_REPLACE)
    }
    @Synchronized fun upsertBudget(categoryId: Long, amount: Long, month: YearMonth) {
        val existing = read().budgets.firstOrNull { it.categoryId == categoryId && it.month == month.toString() }
        save("budgets", existing?.id ?: 0L, "category_id" to categoryId, "amount" to amount, "month" to month.toString())
    }
    @Synchronized fun processRecurring(today: LocalDate = LocalDate.now()) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            read().recurring.forEach { rule ->
                var next = rule.nextDate
                var count = 0
                while (next <= today && count++ < 366) {
                    val key = "${rule.id}:$next"
                    val exists = db.rawQuery("SELECT id FROM transactions WHERE recurring_key=?", arrayOf(key)).use { it.moveToFirst() }
                    if (!exists) saveEntry(Entry(type = rule.type, amount = rule.amount, categoryId = rule.categoryId, walletId = rule.walletId, date = next, note = rule.name, recurringKey = key))
                    next = when (rule.frequency) { "Harian" -> next.plusDays(1); "Mingguan" -> next.plusWeeks(1); else -> next.plusMonths(1) }
                }
                db.update("recurring_transactions", values("next_date" to next.toString()), "id=?", arrayOf(rule.id.toString()))
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    @Synchronized fun exportJson(): JSONObject {
        val db = writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val root = JSONObject().apply {
                put("version", 1)
                tables.forEach { table -> put(table, JSONArray(rows(table).filter { table != "settings" || it.getString("key") !in privateSettings })) }
            }
            db.setTransactionSuccessful()
            return root
        } finally { db.endTransaction() }
    }
    @Synchronized fun restoreJson(root: JSONObject) {
        require(root.getInt("version") == 1) { "Versi backup tidak didukung" }
        tables.forEach { require(root.has(it)) { "Backup tidak lengkap" } }
        validateBackup(root)
        val db = writableDatabase
        db.beginTransaction()
        try {
            tables.reversed().filter { it != "settings" }.forEach { db.delete(it, null, null) }
            db.delete("settings", "key NOT IN (${privateSettings.joinToString { "?" }})", privateSettings.toTypedArray())
            tables.forEach { table ->
                val arr = root.getJSONArray(table)
                for (i in 0 until arr.length()) {
                    val row = arr.getJSONObject(i)
                    if (table == "settings" && row.getString("key") in privateSettings) continue
                    val cv = ContentValues()
                    row.keys().forEach { key -> if (!row.isNull(key)) cv.put(key, row.getString(key)) }
                    db.insertOrThrow(table, null, cv)
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    private fun validateBackup(root: JSONObject) {
        fun objects(table: String) = root.getJSONArray(table).let { array -> (0 until array.length()).map { array.getJSONObject(it) } }
        val categories = objects("categories").associate { it.getLong("id") to it.getString("type") }
        require(categories.values.all { it in listOf("INCOME", "EXPENSE") })
        val wallets = objects("wallets").map { it.getLong("id") }.toSet()
        require(wallets.isNotEmpty() && categories.isNotEmpty()) { "Dompet atau kategori tidak ditemukan" }
        objects("transactions").forEach {
            require(it.getLong("amount") in 1..999_999_999_999L)
            require(it.getString("type") in listOf("INCOME", "EXPENSE") && categories[it.getLong("category_id")] == it.getString("type"))
            require(it.getLong("wallet_id") in wallets)
            LocalDate.parse(it.getString("date"))
        }
        objects("budgets").forEach { require(it.getLong("amount") > 0 && categories[it.getLong("category_id")] == "EXPENSE"); YearMonth.parse(it.getString("month")) }
        objects("goals").forEach { require(it.getLong("target") > 0 && it.getLong("saved") >= 0); LocalDate.parse(it.getString("deadline")) }
        objects("debts").forEach { require(it.getString("type") in listOf("DEBT", "RECEIVABLE") && it.getLong("amount") > 0 && it.getLong("remaining") in 0..it.getLong("amount")); LocalDate.parse(it.getString("due")) }
        objects("recurring_transactions").forEach {
            require(it.getLong("amount") > 0 && it.getLong("wallet_id") in wallets && categories[it.getLong("category_id")] == it.getString("type"))
            require(it.getString("frequency") in listOf("Harian", "Mingguan", "Bulanan")); LocalDate.parse(it.getString("next_date"))
        }
    }
    companion object {
        val tables = listOf("categories", "wallets", "transactions", "budgets", "goals", "debts", "recurring_transactions", "settings")
        val privateSettings = setOf("pin_hash", "pin_salt", "app_lock", "backup_folder", "backup_enabled", "last_backup")
        fun values(vararg fields: Pair<String, Any>): ContentValues = ContentValues().apply {
            fields.forEach { (key, value) -> when (value) { is Long -> put(key, value); is Int -> put(key, value); else -> put(key, value.toString()) } }
        }
    }
}
