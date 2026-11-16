package com.katowproject.costly

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.YearMonth
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

class ExportTest {
    private val month = YearMonth.of(2026, 11)
    private val data = FinanceData(
        categories = listOf(Category(1, "Makan & minum", "EXPENSE", "food", 0)),
        wallets = listOf(Wallet(1, "Tunai", "CASH", 0)),
        entries = listOf(Entry(1, "EXPENSE", 75_000, 1, 1, LocalDate.of(2026, 11, 14), "=SUM(A1), \"kopi\" <enak>")))
    @Test fun csvQuotesAndNeutralizesFormulaCells() {
        val bytes = ByteArrayOutputStream().also { ReportExporter.csv(data, month, it) }.toByteArray()
        val result = bytes.toString(Charsets.UTF_8)
        assertTrue(result.contains("\"'=SUM(A1), \"\"kopi\"\" <enak>\""))
        assertTrue(result.contains("\"75000\""))
    }
    @Test fun xlsxIsWellFormedAndStoresAmountsAsNumbersAndNotesAsText() {
        val bytes = ByteArrayOutputStream().also { ReportExporter.xlsx(data, month, it) }.toByteArray()
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val entry = zip.nextEntry ?: break; entries[entry.name] = zip.readBytes() } }
        assertEquals(6, entries.size)
        entries.values.forEach { DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(it)) }
        val sheet = entries.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertTrue(sheet.contains("<v>75000</v>"))
        assertTrue(sheet.contains("Makan &amp; minum"))
        assertTrue(sheet.contains("&lt;enak&gt;"))
        assertFalse(sheet.contains("<f>"))
    }
}
