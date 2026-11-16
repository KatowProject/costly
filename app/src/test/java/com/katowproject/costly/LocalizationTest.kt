package com.katowproject.costly

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth

class LocalizationTest {
    @Test
    fun testLanguageSelectionFromCode() {
        assertEquals(AppLanguage.ID, AppLanguage.fromCode("id"))
        assertEquals(AppLanguage.ID, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.ID, AppLanguage.fromCode(""))
        assertEquals(AppLanguage.ID, AppLanguage.fromCode("unknown"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.EN, AppLanguage.fromCode("EN"))
    }

    @Test
    fun testCategoryAndWalletNameLocalization() {
        assertEquals("Konsumsi", localizedCategoryName("Konsumsi", AppLanguage.ID))
        assertEquals("Food & Dining", localizedCategoryName("Konsumsi", AppLanguage.EN))
        assertEquals("Gaji", localizedCategoryName("Gaji", AppLanguage.ID))
        assertEquals("Salary", localizedCategoryName("Gaji", AppLanguage.EN))
        assertEquals("Shopping", localizedCategoryName("Belanja", AppLanguage.EN))
        assertEquals("Custom Category", localizedCategoryName("Custom Category", AppLanguage.EN))

        assertEquals("Dompet utama", localizedWalletName("Dompet utama", AppLanguage.ID))
        assertEquals("Main Wallet", localizedWalletName("Dompet utama", AppLanguage.EN))
        assertEquals("BCA", localizedWalletName("BCA", AppLanguage.EN))
    }

    @Test
    fun testFrequencyLocalizationAndCanonical() {
        assertEquals("Daily", localizedFrequency("Harian", AppLanguage.EN))
        assertEquals("Weekly", localizedFrequency("Mingguan", AppLanguage.EN))
        assertEquals("Monthly", localizedFrequency("Bulanan", AppLanguage.EN))
        assertEquals("Harian", localizedFrequency("Daily", AppLanguage.ID))

        assertEquals("Harian", canonicalFrequency("Daily"))
        assertEquals("Harian", canonicalFrequency("Harian"))
        assertEquals("Mingguan", canonicalFrequency("Weekly"))
        assertEquals("Bulanan", canonicalFrequency("Monthly"))
    }

    @Test
    fun testCurrencyAndDateFormatting() {
        assertEquals("Rp 150.000", formatRupiah(150_000L, AppLanguage.ID))
        assertEquals("Rp 150,000", formatRupiah(150_000L, AppLanguage.EN))

        val date = LocalDate.of(2026, 11, 14)
        val month = YearMonth.of(2026, 11)

        assertEquals("14 Nov 2026", localizedDateLabel(date, AppLanguage.ID))
        assertEquals("14 Nov 2026", localizedDateLabel(date, AppLanguage.EN))

        assertEquals("Nov 2026", localizedMonthLabel(month, AppLanguage.ID))
        assertEquals("Nov 2026", localizedMonthLabel(month, AppLanguage.EN))
    }

    @Test
    fun testCurrencyFormatting() {
        val enLang = LocalizationManager.getLanguage("en")
        val idLang = LocalizationManager.getLanguage("id")

        val usd = LocalizationManager.getCurrency("USD")
        val eur = LocalizationManager.getCurrency("EUR")
        val jpy = LocalizationManager.getCurrency("JPY")
        val idr = LocalizationManager.getCurrency("IDR")

        assertEquals("$ 1,500", usd.format(1500L, enLang))
        assertEquals("€ 1,500", eur.format(1500L, enLang))
        assertEquals("¥ 1,500", jpy.format(1500L, enLang))
        assertEquals("Rp 1.500", idr.format(1500L, idLang))
    }

    @Test
    fun testRegisterCustomCurrency() {
        val btc = AppCurrency(code = "BTC", symbol = "₿", name = "Bitcoin", symbolPrefix = true)
        LocalizationManager.registerCurrency(btc)

        val retrieved = LocalizationManager.getCurrency("BTC")
        assertEquals("BTC", retrieved.code)
        assertEquals("₿", retrieved.symbol)
        val enLang = LocalizationManager.getLanguage("en")
        assertEquals("₿ 2", retrieved.format(2L, enLang))
    }

    @Test
    fun testSupportedLanguages() {
        val supported = LocalizationManager.getSupportedLanguages()
        assertEquals(2, supported.size)
        assertEquals("id", supported[0].code)
        assertEquals("en", supported[1].code)
        assertEquals("Bahasa Indonesia", supported[0].displayName)
        assertEquals("English", supported[1].displayName)
    }

    @Test
    fun testExportWithCustomCurrency() {
        val month = YearMonth.of(2026, 11)
        val data = FinanceData(
            categories = listOf(Category(1, "Food", "EXPENSE", "food", 0)),
            wallets = listOf(Wallet(1, "Cash", "CASH", 0)),
            entries = listOf(Entry(1, "EXPENSE", 25, 1, 1, LocalDate.of(2026, 11, 14), "Coffee"))
        )

        val usd = LocalizationManager.getCurrency("USD")
        val enLang = LocalizationManager.getLanguage("en")

        val csvStream = ByteArrayOutputStream()
        ReportExporter.csv(data, month, csvStream, enLang, usd)
        val csv = csvStream.toByteArray().toString(Charsets.UTF_8)
        assertTrue(csv.contains("Date,Type,Note,Category,Wallet,Amount USD"))
        assertTrue(csv.contains("Coffee"))
        assertTrue(csv.contains("25"))
    }

    @Test
    fun testEnglishReportExports() {
        val month = YearMonth.of(2026, 11)
        val data = FinanceData(
            categories = listOf(Category(1, "Konsumsi", "EXPENSE", "food", 0)),
            wallets = listOf(Wallet(1, "Dompet utama", "CASH", 0)),
            entries = listOf(Entry(1, "EXPENSE", 50_000, 1, 1, LocalDate.of(2026, 11, 14), "Lunch"))
        )

        val csvStream = ByteArrayOutputStream()
        ReportExporter.csv(data, month, csvStream, AppLanguage.EN)
        val csv = csvStream.toByteArray().toString(Charsets.UTF_8)
        assertTrue(csv.contains("Date,Type,Note,Category,Wallet,Amount IDR"))
        assertTrue(csv.contains("Expense"))
        assertTrue(csv.contains("Food & Dining"))
        assertTrue(csv.contains("Main Wallet"))

        val xlsxStream = ByteArrayOutputStream()
        ReportExporter.xlsx(data, month, xlsxStream, AppLanguage.EN)
        val xlsxBytes = xlsxStream.toByteArray()
        assertTrue(xlsxBytes.isNotEmpty())
    }
}
