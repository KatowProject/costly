package com.katowproject.costly

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ColdStartTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun openingActualActivityLoadsWalletsAndCategoriesBeforeShowingDashboard() {
        val model = ViewModelProvider(compose.activity)[FinanceViewModel::class.java]
        compose.waitUntil(20_000) { model.ready && !model.busy }
        assertTrue(model.data.wallets.isNotEmpty())
        assertTrue(model.data.categories.isNotEmpty())
        compose.onNodeWithText("${model.data.wallets.size} dompet terhubung").assertIsDisplayed()
        compose.onNodeWithText("Ringkasan bulan ini").assertIsDisplayed()
    }
}
