package com.katowproject.costly

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FinanceStoreTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "costly-test-${UUID.randomUUID()}.db"
    private lateinit var store: FinanceStore
    private val files = mutableListOf<File>()
    @Before fun setup() { store = FinanceStore(context, name); store.read() }
    @After fun cleanup() { store.close(); context.deleteDatabase(name); files.forEach { it.delete() } }
    private fun expense(amount: Long = 75_000) = Entry(type = "EXPENSE", amount = amount, categoryId = 1, walletId = 1, date = LocalDate.of(2026, 11, 14), note = "Makan siang")
    @Test fun insertEditDeletePersistsAndRecalculates() {
        store.saveEntry(expense())
        val saved = store.read().entries.single()
        assertEquals(-75_000L, store.read().balance())
        store.saveEntry(saved.copy(amount = 50_000))
        FinanceStore(context, name).use { reopened -> assertEquals(-50_000L, reopened.read().balance()) }
        store.delete("transactions", saved.id)
        assertEquals(0L, store.read().balance())
    }
    @Test fun recurringCatchupIsIdempotent() {
        store.save("recurring_transactions", 0, "name" to "Tagihan", "type" to "EXPENSE", "amount" to 20_000L, "category_id" to 1L, "wallet_id" to 1L, "frequency" to "Bulanan", "next_date" to "2026-09-16")
        store.processRecurring(LocalDate.of(2026, 11, 16)); store.processRecurring(LocalDate.of(2026, 11, 16))
        assertEquals(3, store.read().entries.size)
        assertEquals(LocalDate.of(2026, 12, 16), store.read().recurring.single().nextDate)
        assertEquals(-60_000L, store.read().balance())
    }
    @Test fun budgetUpsertDoesNotDuplicateCategoryMonth() {
        val month = YearMonth.of(2026, 11)
        store.upsertBudget(1, 100_000, month); store.upsertBudget(1, 200_000, month)
        assertEquals(200_000L, store.read().budgets.single().amount)
    }
    @Test fun encryptedBackupRestoresDataAndAttachmentAndPreservesPin() {
        store.attachmentsDirectory.mkdirs()
        val receipt = File(store.attachmentsDirectory, "test-${UUID.randomUUID()}.txt").also { files.add(it); it.writeText("Receipt content") }
        store.saveEntry(expense().copy(attachment = receipt.absolutePath))
        PinSecurity.set(store, "123456")
        val backup = BackupService.create(store, "password-test")
        assertFalse(backup.toString(Charsets.ISO_8859_1).contains("Makan siang"))
        store.delete("transactions", store.read().entries.single().id)
        BackupService.restore(store, backup, "password-test")
        val restored = store.read().entries.single()
        val restoredReceipt = File(restored.attachment).also { files.add(it) }
        assertEquals("Receipt content", restoredReceipt.readText())
        assertEquals(75_000L, restored.amount)
        assertTrue(PinSecurity.verify(store, "123456"))
    }
    @Test fun wrongPasswordAndCorruptedBackupPreserveExistingData() {
        store.saveEntry(expense())
        val backup = BackupService.create(store, "password-test")
        try { BackupService.restore(store, backup, "wrong-password"); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        backup[backup.lastIndex] = (backup.last().toInt() xor 1).toByte()
        try { BackupService.restore(store, backup, "password-test"); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        assertEquals(75_000L, store.read().entries.single().amount)
    }
    @Test fun invalidRestoreRollsBackAndForeignKeysBlockDeletion() {
        store.saveEntry(expense())
        val root = store.exportJson()
        root.getJSONArray("transactions").getJSONObject(0).put("category_id", 999)
        try { store.restoreJson(root); fail("Expected rejection") } catch (_: IllegalArgumentException) { }
        assertEquals(75_000L, store.read().entries.single().amount)
        root.getJSONArray("transactions").getJSONObject(0).put("category_id", 1)
        root.getJSONArray("transactions").put(org.json.JSONObject(root.getJSONArray("transactions").getJSONObject(0).toString()))
        try { store.restoreJson(root); fail("Expected duplicate key rejection") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(75_000L, store.read().entries.single().amount)
        try { store.delete("categories", 1); fail("Expected foreign key rejection") } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(8, store.read().categories.size)
    }
    @Test fun pdfReportCanBeRenderedAcrossMultiplePages() {
        val data = store.read().copy(entries = (1..80).map { expense().copy(id = it.toLong(), note = "Catatan panjang untuk memastikan laporan dapat dibuka dan dirender dengan benar") })
        val file = File(context.cacheDir, "report-${UUID.randomUUID()}.pdf").also { files.add(it) }
        file.outputStream().use { ReportExporter.pdf(data, YearMonth.of(2026, 11), it) }
        android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            android.graphics.pdf.PdfRenderer(descriptor).use { renderer ->
                assertTrue(renderer.pageCount > 1)
                renderer.openPage(0).use { page -> assertEquals(595, page.width); assertEquals(842, page.height) }
            }
        }
    }
}
