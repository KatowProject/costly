package com.katowproject.costly

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.katowproject.costly.ui.theme.CostlyTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class TransactionFlowTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val databaseName = "ui-test-${UUID.randomUUID()}.db"
    private val model = FinanceViewModel(context.applicationContext as Application, FinanceStore(context, databaseName))
    @After fun cleanup() { model.store.close(); context.deleteDatabase(databaseName) }
    @Test fun createEditDeleteTransactionUpdatesVisibleScreensAndBalance() {
        compose.setContent { CostlyTheme { CostlyApp(model) } }
        compose.waitUntil(15_000) { model.ready && !model.busy }
        compose.onNodeWithContentDescription("Tambah transaksi").performClick()
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("75000")
        compose.onNodeWithText("Pilih kategori").performScrollTo().performClick()
        compose.onNodeWithText("Konsumsi").performClick()
        compose.onAllNodes(hasSetTextAction())[1].performScrollTo().performTextInput("Makan uji")
        compose.onNodeWithText("Simpan transaksi").performClick()
        compose.waitUntil(15_000) { model.data.entries.size == 1 && !model.busy }
        assertEquals(-75_000L, model.data.balance())
        compose.onNodeWithText("Transaksi", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Makan uji").performClick()
        compose.onNodeWithText("Detail transaksi").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("50000")
        compose.onAllNodes(hasSetTextAction())[0].assertTextContains("50000")
        compose.onNodeWithText("Simpan transaksi").assertIsEnabled().performSemanticsAction(SemanticsActions.OnClick) { it() }
        try { compose.waitUntil(15_000) { model.data.balance() == -50_000L && !model.busy } }
        catch (e: Exception) { throw AssertionError("balance=${model.data.balance()}, entries=${model.data.entries}, busy=${model.busy}, message=${model.message}", e) }
        compose.onNodeWithText("Makan uji").performClick()
        compose.onNodeWithContentDescription("Hapus transaksi").performClick()
        compose.onNodeWithText("Hapus", useUnmergedTree = true).performClick()
        compose.waitUntil(15_000) { model.data.entries.isEmpty() && !model.busy }
        assertEquals(0L, model.data.balance())
        compose.onNodeWithText("Belum ada transaksi").assertIsDisplayed()
        compose.onNodeWithText("Laporan", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Kenali pola. Rencanakan lebih baik.").assertIsDisplayed()
    }
}
