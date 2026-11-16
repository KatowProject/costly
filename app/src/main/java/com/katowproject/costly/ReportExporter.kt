package com.katowproject.costly

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.OutputStream
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object ReportExporter {
    fun xlsx(
        data: FinanceData,
        month: YearMonth,
        stream: OutputStream,
        language: Language = LocalizationManager.defaultLanguage,
        currency: AppCurrency = LocalizationManager.defaultCurrency,
        strings: AppStrings? = null
    ) {
        fun xml(s: String) = s.filter { it == '\n' || it == '\t' || it.code >= 32 }.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val isEn = language.code == "en"
        val sheetName = strings?.reportSheetName ?: (if (isEn) "Report" else "Laporan")
        val incomeLabel = strings?.income ?: (if (isEn) "Income" else "Pemasukan")
        val expenseLabel = strings?.expense ?: (if (isEn) "Expense" else "Pengeluaran")
        val diffLabel = strings?.netDifference ?: (if (isEn) "Net Difference" else "Selisih")
        val monthLabel = LocalizationManager.formatMonth(month, language)
        val tableHeaders = if (strings != null) {
            listOf(strings.tableDate, strings.tableType, strings.tableNote, strings.tableCategory, strings.tableWallet, "${strings.tableAmount} ${currency.code}")
        } else {
            if (isEn) listOf("Date", "Type", "Note", "Category", "Wallet", "Amount ${currency.code}")
            else listOf("Tanggal", "Jenis", "Catatan", "Kategori", "Dompet", "Jumlah ${currency.code}")
        }

        val rows = buildList<List<String>> {
            add(listOf("COSTLY - $sheetName $monthLabel"))
            add(listOf(incomeLabel, data.total("INCOME", month).toString()))
            add(listOf(expenseLabel, data.total("EXPENSE", month).toString()))
            add(listOf(diffLabel, (data.total("INCOME", month) - data.total("EXPENSE", month)).toString()))
            add(emptyList())
            add(tableHeaders)
            data.monthly(month).forEach { e ->
                val typeName = if (e.type == "INCOME") incomeLabel else expenseLabel
                val rawCat = data.category(e.categoryId)?.name.orEmpty()
                val rawWallet = data.wallet(e.walletId)?.name.orEmpty()
                val catName = strings?.categoryName(rawCat) ?: localizedCategoryName(rawCat, if (isEn) AppLanguage.EN else AppLanguage.ID)
                val walletName = strings?.walletName(rawWallet) ?: localizedWalletName(rawWallet, if (isEn) AppLanguage.EN else AppLanguage.ID)
                add(listOf(e.date.toString(), typeName, e.note, catName, walletName, e.amount.toString()))
            }
        }
        val sheet = buildString {
            append("""<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols><col min="1" max="2" width="18" customWidth="1"/><col min="3" max="3" width="40" customWidth="1"/><col min="4" max="6" width="22" customWidth="1"/></cols><sheetData>""")
            rows.forEachIndexed { r, row ->
                append("<row r=\"${r + 1}\">")
                row.forEachIndexed { c, value ->
                    val reference = "${('A'.code + c).toChar()}${r + 1}"
                    val numeric = (r in 1..3 && c == 1) || (r >= 6 && c == 5)
                    if (numeric) append("<c r=\"$reference\" s=\"2\"><v>$value</v></c>")
                    else append("<c r=\"$reference\" t=\"inlineStr\" s=\"${if (r == 0 || r == 5) 1 else 0}\"><is><t xml:space=\"preserve\">${xml(value)}</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        ZipOutputStream(stream).use { zip ->
            fun entry(name: String, value: String) { zip.putNextEntry(ZipEntry(name)); zip.write(value.toByteArray(Charsets.UTF_8)); zip.closeEntry() }
            entry("[Content_Types].xml", """<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""")
            entry("_rels/.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""")
            entry("xl/workbook.xml", """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="$sheetName" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            entry("xl/_rels/workbook.xml.rels", """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""")
            entry("xl/styles.xml", """<?xml version="1.0"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FF7437DB"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="3"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/><xf numFmtId="3" fontId="0" fillId="0" borderId="0" xfId="0" applyNumberFormat="1"/></cellXfs><cellStyles count="1"><cellStyle name="Normal" xfId="0" builtinId="0"/></cellStyles></styleSheet>""")
            entry("xl/worksheets/sheet1.xml", sheet)
        }
    }

    fun xlsx(data: FinanceData, month: YearMonth, stream: OutputStream, lang: AppLanguage, currency: AppCurrency = LocalizationManager.defaultCurrency) {
        xlsx(data, month, stream, LocalizationManager.getLanguage(lang.code), currency)
    }

    private fun csvCell(value: String): String {
        val safe = if (value.firstOrNull() in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }

    fun csv(
        data: FinanceData,
        month: YearMonth,
        stream: OutputStream,
        language: Language = LocalizationManager.defaultLanguage,
        currency: AppCurrency = LocalizationManager.defaultCurrency,
        strings: AppStrings? = null
    ) {
        val isEn = language.code == "en"
        val incomeLabel = strings?.income ?: (if (isEn) "Income" else "Pemasukan")
        val expenseLabel = strings?.expense ?: (if (isEn) "Expense" else "Pengeluaran")
        val tableHeaders = if (strings != null) {
            listOf(strings.tableDate, strings.tableType, strings.tableNote, strings.tableCategory, strings.tableWallet, "${strings.tableAmount} ${currency.code}")
        } else {
            if (isEn) listOf("Date", "Type", "Note", "Category", "Wallet", "Amount ${currency.code}")
            else listOf("Tanggal", "Jenis", "Catatan", "Kategori", "Dompet", "Jumlah ${currency.code}")
        }

        stream.bufferedWriter(Charsets.UTF_8).use { out ->
            out.write("\uFEFF")
            out.write("${tableHeaders.joinToString(",")}\r\n")
            data.monthly(month).forEach { e ->
                val typeName = if (e.type == "INCOME") incomeLabel else expenseLabel
                val rawCat = data.category(e.categoryId)?.name.orEmpty()
                val rawWallet = data.wallet(e.walletId)?.name.orEmpty()
                val catName = strings?.categoryName(rawCat) ?: localizedCategoryName(rawCat, if (isEn) AppLanguage.EN else AppLanguage.ID)
                val walletName = strings?.walletName(rawWallet) ?: localizedWalletName(rawWallet, if (isEn) AppLanguage.EN else AppLanguage.ID)
                out.write(listOf(e.date.toString(), typeName, e.note, catName, walletName, e.amount.toString()).joinToString(",") { csvCell(it) } + "\r\n")
            }
        }
    }

    fun csv(data: FinanceData, month: YearMonth, stream: OutputStream, lang: AppLanguage, currency: AppCurrency = LocalizationManager.defaultCurrency) {
        csv(data, month, stream, LocalizationManager.getLanguage(lang.code), currency)
    }

    fun pdf(
        data: FinanceData,
        month: YearMonth,
        stream: OutputStream,
        language: Language = LocalizationManager.defaultLanguage,
        currency: AppCurrency = LocalizationManager.defaultCurrency,
        strings: AppStrings? = null
    ) {
        val isEn = language.code == "en"
        val pdfTitle = strings?.reportPdfTitle ?: (if (isEn) "Financial Report" else "Laporan keuangan")
        val incomeLabel = strings?.income ?: (if (isEn) "Income" else "Pemasukan")
        val expenseLabel = strings?.expense ?: (if (isEn) "Expense" else "Pengeluaran")
        val diffLabel = strings?.netDifference ?: (if (isEn) "Net Difference" else "Selisih")
        val detailsLabel = strings?.reportTransactionDetails ?: (if (isEn) "Transaction details" else "Rincian transaksi")
        val noTxLabel = strings?.reportNoTransactions ?: (if (isEn) "No transactions in this period." else "Tidak ada transaksi pada periode ini.")

        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        fun line(text: String, size: Float = 11f, bold: Boolean = false) {
            paint.textSize = size; paint.isFakeBoldText = bold; paint.color = android.graphics.Color.rgb(35, 32, 53)
            var remaining = text
            do {
                if (page == null || y > 780) {
                    page?.let(document::finishPage)
                    page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, ++pageNumber).create())
                    y = 45f
                }
                val count = if (remaining.isEmpty()) 0 else paint.breakText(remaining, true, 515f, null).coerceAtLeast(1)
                page!!.canvas.drawText(remaining.take(count), 40f, y, paint)
                y += size + 11f
                remaining = remaining.drop(count).trimStart()
            } while (remaining.isNotEmpty())
        }
        try {
            line("COSTLY", 23f, true)
            line("$pdfTitle - ${LocalizationManager.formatMonth(month, language)}", 16f, true)
            line("$incomeLabel: ${currency.format(data.total("INCOME", month), language)}")
            line("$expenseLabel: ${currency.format(data.total("EXPENSE", month), language)}")
            val net = data.total("INCOME", month) - data.total("EXPENSE", month)
            val netSign = if (net >= 0) "+ " else "− "
            line("$diffLabel: $netSign${currency.format(kotlin.math.abs(net), language)}")
            line(detailsLabel, 14f, true)
            data.monthly(month).forEach { e ->
                val rawCat = data.category(e.categoryId)?.name.orEmpty()
                val rawWallet = data.wallet(e.walletId)?.name.orEmpty()
                val catName = strings?.categoryName(rawCat) ?: localizedCategoryName(rawCat, if (isEn) AppLanguage.EN else AppLanguage.ID)
                val walletName = strings?.walletName(rawWallet) ?: localizedWalletName(rawWallet, if (isEn) AppLanguage.EN else AppLanguage.ID)
                line("${LocalizationManager.formatDate(e.date, language)} | $catName | ${if (e.type == "INCOME") "+" else "-"} ${currency.format(e.amount, language)}", 10f, true)
                line("${e.note.take(65)} - $walletName", 10f)
            }
            if (data.monthly(month).isEmpty()) line(noTxLabel)
            page?.let(document::finishPage); document.writeTo(stream)
        } finally { document.close() }
    }

    fun pdf(data: FinanceData, month: YearMonth, stream: OutputStream, lang: AppLanguage, currency: AppCurrency = LocalizationManager.defaultCurrency) {
        pdf(data, month, stream, LocalizationManager.getLanguage(lang.code), currency)
    }
}
