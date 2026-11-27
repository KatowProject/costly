package com.katowproject.costly

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class UpdateServiceTest {
    @Test
    fun versionComparisonDetectsNewerMajorMinorPatch() {
        assertTrue(UpdateService.isNewerVersion("v1.2.0", "1.1.0"))
        assertTrue(UpdateService.isNewerVersion("1.10.0", "1.2.0"))
        assertTrue(UpdateService.isNewerVersion("v2.0.0", "1.9.9"))
        assertTrue(UpdateService.isNewerVersion("1.1.1", "1.1.0"))
        assertTrue(UpdateService.isNewerVersion("V1.2.0-rc1", "1.1.0"))

        assertFalse(UpdateService.isNewerVersion("v1.1.0", "1.1.0"))
        assertFalse(UpdateService.isNewerVersion("1.0.9", "1.1.0"))
        assertFalse(UpdateService.isNewerVersion("1.1", "1.1.0"))
        assertFalse(UpdateService.isNewerVersion("1.1.0-beta", "1.1.0"))
    }

    @Test
    fun parseVersionPartsHandlesPrefixesAndSuffixes() {
        assertEquals(listOf(1, 2, 3), UpdateService.parseVersionParts("v1.2.3"))
        assertEquals(listOf(2, 0, 1), UpdateService.parseVersionParts("V2.0.1-alpha+12"))
        assertEquals(listOf(1, 0), UpdateService.parseVersionParts("1.0"))
    }

    @Test
    fun categoryDrilldownFiltersOnlySelectedCategoryAndMonthExpenses() {
        val month = YearMonth.of(2026, 11)
        val data = FinanceData(
            categories = listOf(
                Category(1, "Konsumsi", "EXPENSE", "food", 0),
                Category(2, "Transportasi", "EXPENSE", "car", 0),
                Category(7, "Gaji", "INCOME", "trend", 0)
            ),
            entries = listOf(
                Entry(1, "EXPENSE", 45_000, 1, 1, LocalDate.of(2026, 11, 2), "Sarapan"),
                Entry(2, "EXPENSE", 60_000, 1, 1, LocalDate.of(2026, 11, 5), "Makan malam"),
                Entry(3, "EXPENSE", 25_000, 2, 1, LocalDate.of(2026, 11, 6), "Ojek"),
                Entry(4, "EXPENSE", 90_000, 1, 1, LocalDate.of(2026, 10, 20), "Bulan lalu"),
                Entry(5, "INCOME", 500_000, 7, 1, LocalDate.of(2026, 11, 1), "Bonus")
            )
        )

        val konsumsiEntries = data.monthly(month).filter { it.type == "EXPENSE" && it.categoryId == 1L }
        assertEquals(2, konsumsiEntries.size)
        assertEquals(105_000L, konsumsiEntries.sumOf { it.amount })
        assertTrue(konsumsiEntries.all { it.categoryId == 1L && YearMonth.from(it.date) == month })
    }

    @Test
    fun webFallbackExtractsTagAndApkAssetUrl() {
        assertEquals("v1.1.0", UpdateService.extractTagFromLocation("https://github.com/KatowProject/costly/releases/tag/v1.1.0"))
        assertEquals("v1.2.3", UpdateService.extractTagFromLocation("/KatowProject/costly/releases/tag/v1.2.3?foo=1"))
        assertNull(UpdateService.extractTagFromLocation("https://github.com/KatowProject/costly/releases"))

        val sampleHtml = """
            <div>
              <a href="/KatowProject/costly/releases/download/v1.1.0/Costly-v1.1.0.apk" rel="nofollow">Costly-v1.1.0.apk</a>
            </div>
        """.trimIndent()
        assertEquals(
            "https://github.com/KatowProject/costly/releases/download/v1.1.0/Costly-v1.1.0.apk",
            UpdateService.extractApkUrlFromHtml(sampleHtml)
        )
    }
}
