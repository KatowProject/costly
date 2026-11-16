package com.katowproject.costly

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katowproject.costly.ui.theme.*
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Composable
fun EntryEditor(vm: FinanceViewModel, entry: Entry?, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    val context = LocalContext.current
    val data = vm.data
    var type by rememberSaveable(entry?.id) { mutableStateOf(entry?.type ?: "EXPENSE") }
    var amount by rememberSaveable(entry?.id) { mutableStateOf(entry?.amount?.toString().orEmpty()) }
    var categoryId by rememberSaveable(entry?.id) { mutableLongStateOf(entry?.categoryId ?: 0L) }
    var walletId by rememberSaveable(entry?.id) { mutableLongStateOf(entry?.walletId ?: data.wallets.firstOrNull()?.id ?: 0L) }
    var dateText by rememberSaveable(entry?.id) { mutableStateOf((entry?.date ?: LocalDate.now()).toString()) }
    var note by rememberSaveable(entry?.id) { mutableStateOf(entry?.note.orEmpty()) }
    var attachment by rememberSaveable(entry?.id) { mutableStateOf(entry?.attachment.orEmpty()) }
    var cameraUri by rememberSaveable { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) attachment = uri.toString() }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) attachment = cameraUri }
    val category = data.categories.firstOrNull { it.id == categoryId && it.type == type }
    val wallet = data.wallets.firstOrNull { it.id == walletId }
    val valid = amount.toLongOrNull()?.let { it in 1..999_999_999_999L } == true && category != null && wallet != null
    Column(modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp), verticalArrangement = Arrangement.spacedBy(21.dp)) {
            ScreenTitle(if (entry == null) strings.addTransactionTitle else strings.transactionDetailTitle, back = onClose) {
                if (entry != null) IconButton({ confirmDelete = true }) { Glyph("trash", Modifier.size(20.dp), Red, strings.deleteTransactionDesc) }
            }
            Segments(listOf(strings.segmentExpense, strings.segmentIncome), if (type == "EXPENSE") 0 else 1, { type = if (it == 0) "EXPENSE" else "INCOME"; categoryId = 0 })
            Column(Modifier.fillMaxWidth().background(if (type == "EXPENSE") Red.copy(alpha = .045f) else Green.copy(alpha = .045f), RoundedCornerShape(18.dp)).padding(18.dp)) {
                Text(strings.transactionAmount, fontSize = 12.sp, color = Muted)
                OutlinedTextField(
                    amount, { if (it.all(Char::isDigit) && it.length <= 12) amount = it }, Modifier.fillMaxWidth().padding(top = 10.dp),
                    prefix = { Text(currency.symbol, fontSize = 24.sp, color = Purple) }, placeholder = { Text("0", fontSize = 28.sp) },
                    textStyle = LocalTextStyle.current.copy(fontSize = 28.sp, fontWeight = FontWeight.Bold), singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, focusedBorderColor = Purple, unfocusedContainerColor = CardBg)
                )
                if (amount.isNotEmpty()) Text(currency.format(amount.toLongOrNull() ?: 0, strings.language), fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 8.dp))
            }
            SelectField(strings.category, data.categories.filter { it.type == type }, category, { strings.categoryName(it.name) }, { categoryId = it.id }, "grid")
            SelectField(strings.walletOrAccount, data.wallets, wallet, { strings.walletName(it.name) }, { walletId = it.id }, "wallet")
            DateField(strings.date, LocalDate.parse(dateText), { dateText = it.toString() })
            Field(strings.noteOptional, note, { if (it.length <= 500) note = it }, strings.notePlaceholder, lines = 2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(strings.attachmentOptional, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(strings.camera to "camera", strings.gallery to "image", strings.file to "file").forEach { (label, icon) ->
                        OutlinedButton({
                            if (label == strings.camera) {
                                val file = File(context.cacheDir, "receipt-${UUID.randomUUID()}.jpg")
                                cameraUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file).toString()
                                runCatching { photo.launch(Uri.parse(cameraUri)) }.onFailure { vm.message = strings.cameraUnavailable }
                            } else files.launch(if (label == strings.gallery) arrayOf("image/*") else arrayOf("*/*"))
                        }, Modifier.weight(1f), shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 4.dp)) { Glyph(icon, Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(label, fontSize = 11.sp) }
                    }
                }
                if (attachment.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Glyph("file", Modifier.size(16.dp), Purple); Spacer(Modifier.width(6.dp)); Text(strings.selectedAttachment, Modifier.weight(1f), fontSize = 12.sp, color = Purple)
                    if (attachment.startsWith("/")) TextButton({
                        runCatching {
                            val file = File(attachment)
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "application/octet-stream"
                            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                        }.onFailure { vm.message = strings.noAppToOpenAttachment }
                    }) { Text(strings.open, fontSize = 11.sp) }
                    IconButton({ attachment = "" }, Modifier.size(30.dp)) { Glyph("close", Modifier.size(16.dp), Muted, strings.removeAttachmentDesc) }
                }
                Text(strings.attachmentLimitHelp, fontSize = 10.sp, color = Muted)
            }
        }
        Surface(color = Page) {
            Box(Modifier.padding(horizontal = 22.dp, vertical = 14.dp).imePadding()) {
                PrimaryButton(if (vm.busy) strings.savingProgress else strings.saveTransaction, "check", valid && !vm.busy) {
                    vm.action(strings.transactionSavedSuccess, onSuccess = onClose) {
                        var path = attachment
                        var newFile: File? = null
                        if (path.isNotBlank() && !path.startsWith("/")) {
                            val uri = Uri.parse(path)
                            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(context.contentResolver.getType(uri)) ?: "bin"
                            val dir = File(context.filesDir, "attachments").apply { mkdirs() }
                            newFile = File(dir, "${UUID.randomUUID()}.$ext")
                            try {
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    newFile.outputStream().use { output ->
                                        val buffer = ByteArray(8192)
                                        var total = 0
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            total += count
                                            require(total <= 10 * 1024 * 1024) { strings.attachmentTooLarge }
                                            output.write(buffer, 0, count)
                                        }
                                    }
                                } ?: error(strings.attachmentUnreadable)
                                path = newFile.absolutePath
                            } catch (e: Exception) { newFile.delete(); throw e }
                        }
                        try { vm.store.saveEntry(Entry(entry?.id ?: 0, type, amount.toLong(), categoryId, walletId, LocalDate.parse(dateText), note.trim(), path, entry?.recurringKey.orEmpty())) }
                        catch (e: Exception) { newFile?.delete(); throw e }
                    }
                }
            }
        }
    }
    if (confirmDelete && entry != null) ConfirmDelete(strings.confirmDeleteTransaction(currency.format(entry.amount, strings.language)), { confirmDelete = false }) {
        confirmDelete = false; vm.action(strings.transactionDeletedSuccess, onSuccess = onClose) { vm.store.delete("transactions", entry.id) }
    }
}

@Composable
fun EntityDialog(kind: String, vm: FinanceViewModel, month: YearMonth, goal: Goal?, dismiss: () -> Unit) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(if (kind == "wallet") "CASH" else if (kind == "debt") "DEBT" else "EXPENSE") }
    var categoryId by remember { mutableLongStateOf(0L) }
    var walletId by remember { mutableLongStateOf(vm.data.wallets.firstOrNull()?.id ?: 0L) }
    var date by remember { mutableStateOf(LocalDate.now().plusMonths(1)) }
    var frequency by remember { mutableStateOf("Bulanan") }
    var icon by remember { mutableStateOf("grid") }
    val categories = vm.data.categories.filter { it.type == type }
    val category = categories.firstOrNull { it.id == categoryId }
    val number = amount.toLongOrNull()
    val needsName = kind in listOf("goal", "wallet", "category", "debt", "recurring", "profile")
    val needsAmount = kind !in listOf("category", "profile")
    val needsCategory = kind in listOf("budget", "recurring")
    val valid = (!needsName || name.isNotBlank()) && (!needsAmount || number?.let { it in (if (kind == "wallet") 0L else 1L)..999_999_999_999L } == true) && (!needsCategory || category != null)
    val title = when (kind) {
        "budget" -> strings.dialogTitleBudget
        "goal" -> strings.dialogTitleGoal
        "saving" -> strings.dialogTitleSaving
        "wallet" -> strings.dialogTitleWallet
        "category" -> strings.dialogTitleCategory
        "debt" -> strings.dialogTitleDebt
        "recurring" -> strings.dialogTitleRecurring
        else -> strings.dialogTitleProfile
    }
    FormDialog(title, dismiss, valid, vm.busy, {
        vm.action(strings.savedSuccess, onSuccess = dismiss) {
            when (kind) {
                "profile" -> vm.store.setting("user_name", name.trim())
                "budget" -> vm.store.upsertBudget(categoryId, number!!, month)
                "goal" -> vm.store.save("goals", 0, "name" to name.trim(), "target" to number!!, "saved" to 0L, "deadline" to date.toString())
                "saving" -> if (goal != null) vm.store.save("goals", goal.id, "saved" to (goal.saved + number!!))
                "wallet" -> vm.store.save("wallets", 0, "name" to name.trim(), "type" to type, "initial_balance" to number!!)
                "category" -> vm.store.save("categories", 0, "name" to name.trim(), "type" to type, "icon" to icon, "color" to if (type == "INCOME") 0xFF18A96BL else 0xFF9D6AEBL)
                "debt" -> vm.store.save("debts", 0, "name" to name.trim(), "type" to type, "amount" to number!!, "remaining" to number, "due" to date.toString())
                "recurring" -> {
                    vm.store.save("recurring_transactions", 0, "name" to name.trim(), "type" to type, "amount" to number!!, "category_id" to categoryId, "wallet_id" to walletId, "frequency" to canonicalFrequency(frequency), "next_date" to date.toString())
                    vm.store.processRecurring()
                }
            }
        }
    }) {
        if (kind in listOf("category", "recurring")) Segments(listOf(strings.segmentExpense, strings.segmentIncome), if (type == "EXPENSE") 0 else 1, { type = if (it == 0) "EXPENSE" else "INCOME"; categoryId = 0 })
        if (kind == "debt") Segments(listOf(strings.myDebt, strings.myReceivable), if (type == "DEBT") 0 else 1, { type = if (it == 0) "DEBT" else "RECEIVABLE" })
        if (needsName) Field(if (kind == "debt") strings.fieldPersonOrPurpose else strings.fieldName, name, { if (it.length <= 60) name = it }, if (kind == "goal") strings.placeholderGoal else strings.placeholderName)
        if (kind == "wallet") SelectField(strings.walletType, listOf("CASH", "BANK", "EWALLET"), type, { strings.walletType(it) }, { type = it }, "wallet")
        if (needsCategory) SelectField(strings.category, categories, category, { strings.categoryName(it.name) }, { categoryId = it.id })
        if (needsAmount) Field(if (kind == "wallet") strings.initialBalanceWithSymbol(currency.symbol) else if (kind == "goal") strings.targetAmountWithSymbol(currency.symbol) else strings.amountWithSymbol(currency.symbol), amount, { if (it.length <= 12) amount = it }, "0", numeric = true)
        if (kind == "budget") Text(strings.budgetAppliesTo(localizedMonthLabel(month, strings.lang)), fontSize = 11.sp, color = Muted)
        if (kind in listOf("goal", "debt", "recurring")) DateField(if (kind == "recurring") strings.startDate else strings.targetOrDueDate, date, { date = it })
        if (kind == "saving") Text(strings.goalProgressNote(goal?.name.orEmpty()), fontSize = 12.sp, color = Muted)
        if (kind == "recurring") {
            SelectField(strings.wallet, vm.data.wallets, vm.data.wallet(walletId), { strings.walletName(it.name) }, { walletId = it.id }, "wallet")
            SelectField(strings.frequency, listOf("Harian", "Mingguan", "Bulanan"), frequency, { strings.frequency(it) }, { frequency = it }, "repeat")
            Text(strings.recurringDueHelp, fontSize = 11.sp, color = Muted)
        }
        if (kind == "category") SelectField(strings.icon, listOf("grid", "food", "car", "bag", "shopping", "bolt", "wallet", "target"), icon, { strings.iconName(it) }, { icon = it })
        if (kind == "debt") Text(strings.debtCashFlowNote, fontSize = 11.sp, color = Muted)
    }
}
