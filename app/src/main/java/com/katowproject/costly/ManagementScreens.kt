package com.katowproject.costly

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katowproject.costly.ui.theme.*
import java.time.LocalDate

@Composable
fun ColumnScope.WalletsScreen(vm: FinanceViewModel, back: () -> Unit, add: () -> Unit) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    val data = vm.data
    var selected by remember { mutableStateOf<Wallet?>(null) }
    ScreenTitle(strings.walletsTitle, strings.walletsSubtitle(data.wallets.size, currency.format(data.balance(), strings.language)), back)
    data.wallets.forEach { wallet ->
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile("wallet", Purple); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(strings.walletName(wallet.name), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(strings.walletType(wallet.type), fontSize = 11.sp, color = Muted)
                }
                IconButton({ selected = wallet }) { Glyph("trash", Modifier.size(17.dp), Muted, strings.deleteWalletDesc) }
            }
            Spacer(Modifier.height(18.dp))
            Text(currency.format(data.balance(wallet.id), strings.language), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Purple)
            Spacer(Modifier.height(5.dp))
            Text(strings.openingBalance(currency.format(wallet.initial, strings.language)), fontSize = 11.sp, color = Muted)
        }
    }
    SoftButton(strings.addWallet, "wallet", add)
    if (selected != null) ConfirmDelete(strings.confirmDeleteWallet(strings.walletName(selected!!.name)), { selected = null }) {
        val id = selected!!.id; selected = null
        vm.action(strings.walletDeleted) {
            val current = vm.store.read()
            require(current.wallets.size > 1 && current.entries.none { it.walletId == id } && current.recurring.none { it.walletId == id }) { strings.walletInUseError }
            vm.store.delete("wallets", id)
        }
    }
}

@Composable
fun ColumnScope.CategoriesScreen(vm: FinanceViewModel, back: () -> Unit, add: () -> Unit) {
    val strings = LocalStrings.current
    var type by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Category?>(null) }
    ScreenTitle(strings.categoriesTitle, strings.categoriesSubtitle, back)
    Segments(listOf(strings.segmentExpense, strings.segmentIncome), type, { type = it })
    AppCard {
        vm.data.categories.filter { it.type == if (type == 0) "EXPENSE" else "INCOME" }.forEach { cat ->
            val catDisplayName = strings.categoryName(cat.name)
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(cat.icon, Color(cat.color), 38); Spacer(Modifier.width(12.dp))
                Text(catDisplayName, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                IconButton({ selected = cat }, Modifier.size(30.dp)) { Glyph("trash", Modifier.size(16.dp), Muted, strings.deleteCategoryDesc(catDisplayName)) }
            }
        }
    }
    SoftButton(strings.addCategory, "grid", add)
    if (selected != null) ConfirmDelete(strings.confirmDeleteCategory(strings.categoryName(selected!!.name)), { selected = null }) {
        val id = selected!!.id; selected = null
        vm.action(strings.categoryDeleted) {
            val current = vm.store.read()
            require(current.entries.none { it.categoryId == id } && current.budgets.none { it.categoryId == id } && current.recurring.none { it.categoryId == id }) { strings.categoryInUseError }
            vm.store.delete("categories", id)
        }
    }
}

@Composable
fun ColumnScope.RecurringScreen(vm: FinanceViewModel, back: () -> Unit, add: () -> Unit) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    var selected by remember { mutableStateOf<Recurring?>(null) }
    ScreenTitle(strings.recurringTitle, strings.recurringSubtitle, back)
    AppCard(color = Lavender) {
        Text(strings.recurringCardTitle, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Purple)
        Spacer(Modifier.height(8.dp))
        Text(strings.recurringCardDesc, fontSize = 12.sp, color = Muted)
    }
    if (vm.data.recurring.isEmpty()) AppCard { EmptyState(strings.noRecurring, strings.noRecurringSub, "repeat") }
    vm.data.recurring.forEach { rule ->
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile("repeat", Purple); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(rule.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("${strings.frequency(rule.frequency)} · ${strings.walletName(vm.data.wallet(rule.walletId)?.name.orEmpty())}", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton({ selected = rule }, Modifier.size(28.dp)) { Glyph("trash", Modifier.size(17.dp), Muted) }
            }
            Spacer(Modifier.height(16.dp))
            Text((if (rule.type == "INCOME") "+ " else "− ") + currency.format(rule.amount, strings.language), color = if (rule.type == "INCOME") Green else Red, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(strings.nextDate(localizedDateLabel(rule.nextDate, strings.lang)), fontSize = 11.sp, color = Muted)
        }
    }
    SoftButton(strings.addRecurring, "repeat", add)
    if (selected != null) ConfirmDelete(strings.confirmDeleteRecurring(selected!!.name), { selected = null }) {
        val id = selected!!.id; selected = null
        vm.action(strings.recurringStopped) { vm.store.delete("recurring_transactions", id) }
    }
}

@Composable
fun ColumnScope.DebtsScreen(vm: FinanceViewModel, back: () -> Unit, add: () -> Unit) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    var tab by remember { mutableIntStateOf(0) }
    var pay by remember { mutableStateOf<Debt?>(null) }
    var selected by remember { mutableStateOf<Debt?>(null) }
    ScreenTitle(strings.debtsTitle, strings.debtsSubtitle, back)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryTile(strings.myDebt, vm.data.debts.filter { it.type == "DEBT" }.sumOf { it.remaining }, "expense", Red, Modifier.weight(1f))
        SummaryTile(strings.myReceivable, vm.data.debts.filter { it.type == "RECEIVABLE" }.sumOf { it.remaining }, "trend", Green, Modifier.weight(1f))
    }
    Segments(listOf(strings.myDebt, strings.myReceivable), tab, { tab = it })
    val debts = vm.data.debts.filter { it.type == if (tab == 0) "DEBT" else "RECEIVABLE" }
    if (debts.isEmpty()) AppCard { EmptyState(strings.noDebts, strings.noDebtsSub, "people") }
    debts.forEach { debt ->
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile("people", if (debt.type == "DEBT") Red else Green); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(debt.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(strings.dueDate(localizedDateLabel(LocalDate.parse(debt.due), strings.lang)), fontSize = 10.sp, color = if (debt.remaining > 0 && LocalDate.parse(debt.due) < LocalDate.now()) Red else Muted, modifier = Modifier.padding(top = 4.dp))
                }
                IconButton({ selected = debt }, Modifier.size(28.dp)) { Glyph("trash", Modifier.size(17.dp), Muted) }
            }
            Spacer(Modifier.height(16.dp))
            Text(if (debt.remaining == 0L) strings.paidOff else currency.format(debt.remaining, strings.language), fontSize = 24.sp, color = if (debt.remaining == 0L) Green else Ink, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(strings.outOfTotal(currency.format(debt.amount, strings.language)), fontSize = 11.sp, color = Muted)
            Spacer(Modifier.height(12.dp))
            ProgressBar((debt.amount - debt.remaining).toFloat() / debt.amount, Green)
            if (debt.remaining > 0) {
                Spacer(Modifier.height(14.dp))
                SoftButton(strings.recordPayment, "check", { pay = debt })
            }
        }
    }
    SoftButton(strings.addDebtOrReceivable, "people", add)
    if (pay != null) {
        val debt = pay!!
        var amount by remember { mutableStateOf("") }
        FormDialog(strings.recordPayment, { pay = null }, amount.toLongOrNull()?.let { it in 1..debt.remaining } == true, vm.busy, {
            vm.action(strings.paymentRecorded, onSuccess = { pay = null }) { vm.store.save("debts", debt.id, "remaining" to (debt.remaining - amount.toLong())) }
        }) {
            Field(strings.amountPaidWithSymbol(currency.symbol), amount, { if (it.length <= 12) amount = it }, "0", numeric = true)
            Text(strings.debtPayNote(currency.format(debt.remaining, strings.language)), fontSize = 12.sp, color = Muted)
        }
    }
    if (selected != null) ConfirmDelete(strings.confirmDeleteDebt(selected!!.name), { selected = null }) {
        val id = selected!!.id; selected = null
        vm.action(strings.debtDeleted) { vm.store.delete("debts", id) }
    }
}
