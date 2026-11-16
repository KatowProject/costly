package com.katowproject.costly

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class FinanceCalculationTest {
    private val month = YearMonth.of(2026, 11)
    private val data = FinanceData(
        wallets = listOf(Wallet(1, "Tunai", "CASH", 100_000), Wallet(2, "Bank", "BANK", 200_000)),
        entries = listOf(
            Entry(1, "INCOME", 1_000_000, 7, 2, LocalDate.of(2026, 10, 1), "Gaji lama"),
            Entry(2, "EXPENSE", 75_000, 1, 1, LocalDate.of(2026, 11, 14), "Makan"),
            Entry(3, "INCOME", 500_000, 7, 2, LocalDate.of(2026, 11, 14), "Bonus"),
            Entry(4, "EXPENSE", 25_000, 2, 2, LocalDate.of(2026, 11, 15), "Transportasi")))
    @Test fun balancesIncludeOpeningAndAllMonthsPerWallet() {
        assertEquals(1_700_000L, data.balance())
        assertEquals(25_000L, data.balance(1))
        assertEquals(1_675_000L, data.balance(2))
    }
    @Test fun reportAndBudgetOnlyCountSelectedMonthAndCategory() {
        assertEquals(500_000L, data.total("INCOME", month))
        assertEquals(100_000L, data.total("EXPENSE", month))
        assertEquals(75_000L, data.spent(1, month))
        assertEquals(0L, data.spent(7, month))
        assertEquals(0L, data.total("EXPENSE", month.plusMonths(1)))
    }
    @Test fun rupiahPreservesLargeWholeAmounts() {
        assertEquals("Rp 24.850.000", rupiah(24_850_000L))
        assertEquals("Rp 0", rupiah(0))
    }
}
