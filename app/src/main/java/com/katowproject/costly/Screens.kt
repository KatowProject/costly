package com.katowproject.costly

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katowproject.costly.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ColumnScope.TransactionsScreen(data: FinanceData, month: YearMonth, onMonth: (YearMonth) -> Unit, onEntry: (Entry) -> Unit) {
    val strings = LocalStrings.current
    var query by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableIntStateOf(0) }
    var wallet by rememberSaveable { mutableLongStateOf(0L) }
    var filterOpen by remember { mutableStateOf(false) }
    ScreenTitle(strings.transactionsTitle, strings.transactionsSubtitle) { IconTile("list", Purple, 42) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            query, { query = it }, Modifier.weight(1f),
            placeholder = { Text(strings.searchPlaceholder, fontSize = 13.sp) },
            leadingIcon = { Glyph("search", Modifier.size(19.dp), Muted) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, unfocusedContainerColor = CardBg)
        )
        Box {
            FilledTonalIconButton({ filterOpen = true }, Modifier.size(48.dp)) { Glyph("filter", tint = Purple, description = strings.filterWalletDesc) }
            DropdownMenu(filterOpen, { filterOpen = false }) {
                DropdownMenuItem(text = { Text(strings.allWallets) }, onClick = { wallet = 0; filterOpen = false })
                data.wallets.forEach { item ->
                    DropdownMenuItem(text = { Text(strings.walletName(item.name)) }, onClick = { wallet = item.id; filterOpen = false })
                }
            }
        }
    }
    Segments(listOf(strings.segmentAll, strings.segmentIncome, strings.segmentExpense), type, { type = it })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonthChip(month, onMonth)
        Spacer(Modifier.weight(1f))
        if (wallet != 0L) Text(strings.walletName(data.wallet(wallet)?.name.orEmpty()), fontSize = 11.sp, color = Purple)
    }
    val entries = data.monthly(month).filter {
        (type == 0 || it.type == if (type == 1) "INCOME" else "EXPENSE") &&
                (wallet == 0L || it.walletId == wallet) &&
                (query.isBlank() || it.note.contains(query, true) ||
                        data.category(it.categoryId)?.name?.contains(query, true) == true ||
                        strings.categoryName(data.category(it.categoryId)?.name.orEmpty()).contains(query, true) ||
                        it.amount.toString().contains(query))
    }
    if (entries.isEmpty()) AppCard { EmptyState(strings.noTransactions, strings.noTransactionsSub) }
    entries.groupBy { it.date }.forEach { (date, group) ->
        Column {
            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Text(localizedDayDateLabel(date, strings.lang), Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(strings.transactionCount(group.size), fontSize = 10.sp, color = Muted)
            }
            AppCard {
                group.forEachIndexed { i, entry ->
                    TransactionRow(entry, data, onEntry)
                    if (i != group.lastIndex) HorizontalDivider(color = Line)
                }
            }
        }
    }
}

@Composable
fun ColumnScope.ReportsScreen(data: FinanceData, month: YearMonth, onMonth: (YearMonth) -> Unit, onEntry: (Entry) -> Unit = {}) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("") }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { ReportExporter.csv(data, month, it, strings.language, currency, strings) } ?: error("File tidak dapat ditulis")
                }
                strings.csvExportSuccess
            }.getOrElse { strings.exportFailed(it.message.orEmpty()) }
        }
    }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { ReportExporter.pdf(data, month, it, strings.language, currency, strings) } ?: error("File tidak dapat ditulis")
                }
                strings.pdfExportSuccess
            }.getOrElse { strings.exportFailed(it.message.orEmpty()) }
        }
    }
    val excelLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")) { uri ->
        if (uri != null) scope.launch {
            status = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { ReportExporter.xlsx(data, month, it, strings.language, currency, strings) } ?: error("File tidak dapat ditulis")
                }
                strings.excelExportSuccess
            }.getOrElse { strings.exportFailed(it.message.orEmpty()) }
        }
    }
    ScreenTitle(strings.reportsTitle, strings.reportsSubtitle) { IconTile("chart", Purple, 42) }
    Segments(listOf(strings.segmentSummary, strings.segmentCategory, strings.segmentTrend), tab, { tab = it })
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(strings.reportPeriod, Modifier.weight(1f), fontSize = 12.sp, color = Muted)
        MonthChip(month, onMonth)
    }
    if (tab == 0) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SummaryTile(strings.income, data.total("INCOME", month), "trend", Green, Modifier.weight(1f))
            SummaryTile(strings.expense, data.total("EXPENSE", month), "expense", Red, Modifier.weight(1f))
        }
        AppCard(color = Lavender) {
            Text(strings.netDifference, fontSize = 12.sp, color = Purple)
            Spacer(Modifier.height(7.dp))
            val difference = data.total("INCOME", month) - data.total("EXPENSE", month)
            Text((if (difference >= 0) "+ " else "− ") + currency.format(kotlin.math.abs(difference), strings.language), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Purple)
        }
    }
    if (tab != 1) AppCard {
        Heading(strings.cashFlowTrend)
        Spacer(Modifier.height(18.dp))
        CashFlowChart(data, month)
    }
    if (tab != 2) AppCard {
        Heading(if (tab == 1) strings.expenseDistribution else strings.topExpenseCategories)
        Spacer(Modifier.height(18.dp))
        if (tab == 1) { CategoryDonut(data, month) { selectedCategory = it }; Spacer(Modifier.height(22.dp)) }
        val categories = categoryTotals(data, month)
        val total = data.total("EXPENSE", month)
        if (categories.isEmpty()) EmptyState(strings.noExpense, strings.noExpenseSub, "chart")
        else {
            (if (tab == 1) categories else categories.take(4)).forEach { (category, amount) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedCategory = category }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(category.icon, Color(category.color), 36); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(strings.categoryName(category.name), Modifier.weight(1f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Text(currency.format(amount, strings.language), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text("${amount * 100 / total}%", fontSize = 10.sp, color = Muted)
                            Spacer(Modifier.width(4.dp))
                            Glyph("chevron", Modifier.size(13.dp), tint = Muted)
                        }
                        ProgressBar(amount.toFloat() / total, Color(category.color))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(strings.tapCategoryForDetails, fontSize = 10.sp, color = Muted)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton({ pdfLauncher.launch("Costly-$month.pdf") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(8.dp)) {
            Glyph("file", Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(strings.exportPdf, fontSize = 12.sp)
        }
        OutlinedButton({ excelLauncher.launch("Costly-$month.xlsx") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(8.dp)) {
            Glyph("grid", Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(strings.exportExcel, fontSize = 12.sp)
        }
        OutlinedButton({ csvLauncher.launch("Costly-$month.csv") }, Modifier.weight(1f), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(8.dp)) {
            Glyph("download", Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text(strings.exportCsv, fontSize = 12.sp)
        }
    }
    if (status.isNotBlank()) Text(status, fontSize = 12.sp, color = Purple)
    selectedCategory?.let { category ->
        val catEntries = data.monthly(month).filter { it.type == "EXPENSE" && it.categoryId == category.id }
        val catTotal = catEntries.sumOf { it.amount }
        val monthTotal = data.total("EXPENSE", month)
        val percent = if (monthTotal > 0) (catTotal * 100 / monthTotal).toInt() else 0
        AlertDialog(
            onDismissRequest = { selectedCategory = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(category.icon, Color(category.color), 40)
                    Column(Modifier.weight(1f)) {
                        Text(strings.categoryName(category.name), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            localizedMonthLabel(month, strings.lang),
                            fontSize = 12.sp,
                            color = Muted
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(category.color).copy(alpha = 0.1f))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            strings.categoryTransactionsSummary(
                                catEntries.size,
                                currency.format(catTotal, strings.language),
                                percent
                            ),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink
                        )
                    }
                    if (catEntries.isEmpty()) {
                        Text(strings.noExpense, fontSize = 12.sp, color = Muted)
                    } else {
                        Column(Modifier.heightIn(max = 340.dp).verticalScroll(rememberScrollState())) {
                            catEntries.forEachIndexed { index, entry ->
                                if (index > 0) HorizontalDivider(color = Line)
                                TransactionRow(entry, data) {
                                    selectedCategory = null
                                    onEntry(entry)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedCategory = null }) {
                    Text(strings.close)
                }
            }
        )
    }
}

@Composable
fun ColumnScope.BudgetScreen(vm: FinanceViewModel, month: YearMonth, onMonth: (YearMonth) -> Unit, addBudget: () -> Unit, addGoal: () -> Unit, onSaving: (Goal) -> Unit) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var deleteBudget by remember { mutableStateOf<Budget?>(null) }
    var deleteGoal by remember { mutableStateOf<Goal?>(null) }
    val data = vm.data
    ScreenTitle(strings.budgetAndGoalsTitle, strings.budgetAndGoalsSubtitle) { IconTile("target", Purple, 42) }
    Segments(listOf(strings.segmentBudget, strings.segmentGoals), tab, { tab = it })
    if (tab == 0) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(strings.monthlyBudget, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
            MonthChip(month, onMonth)
        }
        val budgets = data.budgets.filter { it.month == month.toString() }
        if (budgets.isEmpty()) AppCard { EmptyState(strings.planExpenses, strings.planExpensesSub, "target") }
        else AppCard {
            budgets.forEach { budget ->
                BudgetRow(data, budget, month)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ deleteBudget = budget }, contentPadding = PaddingValues(0.dp)) { Text(strings.deleteBudget, fontSize = 10.sp, color = Muted) }
                }
            }
        }
        SoftButton(strings.addOrEditBudget, onClick = addBudget)
    }
    if (tab == 1 || data.goals.isNotEmpty()) {
        Heading(strings.savingsGoals, strings.add, addGoal)
        Text(strings.goalsSeparateNote, fontSize = 11.sp, color = Muted)
        if (data.goals.isEmpty()) AppCard { EmptyState(strings.realizePlans, strings.realizePlansSub, "target") }
        data.goals.forEach { goal ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile("target", Green); Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(goal.name, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(strings.targetDeadline(localizedDateLabel(LocalDate.parse(goal.deadline), strings.lang)), fontSize = 10.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
                    }
                    IconButton({ deleteGoal = goal }, Modifier.size(26.dp)) { Glyph("trash", Modifier.size(16.dp), tint = Muted) }
                }
                Spacer(Modifier.height(18.dp))
                Text("${currency.format(goal.saved, strings.language)} / ${currency.format(goal.target, strings.language)}", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(10.dp))
                ProgressBar(goal.saved.toFloat() / goal.target, Green)
                Spacer(Modifier.height(12.dp))
                SoftButton(if (goal.saved >= goal.target) strings.targetReachedAddProgress else strings.addSavings, onClick = { onSaving(goal) })
            }
        }
    }
    if (tab == 0 && data.goals.isEmpty()) SoftButton(strings.createSavingsGoal, "target", addGoal)
    if (tab == 1) SoftButton(strings.newSavingsGoal, onClick = addGoal)
    if (deleteBudget != null) ConfirmDelete(strings.confirmDeleteBudget, { deleteBudget = null }) {
        val id = deleteBudget!!.id
        deleteBudget = null
        vm.action(strings.budgetDeleted) { vm.store.delete("budgets", id) }
    }
    if (deleteGoal != null) ConfirmDelete(strings.confirmDeleteGoal(deleteGoal!!.name), { deleteGoal = null }) {
        val id = deleteGoal!!.id
        deleteGoal = null
        vm.action(strings.goalDeleted) { vm.store.delete("goals", id) }
    }
}

@Composable
fun ColumnScope.MoreScreen(
    data: FinanceData,
    navigate: (String) -> Unit,
    onProfile: () -> Unit,
    availableUpdate: AppReleaseInfo? = null,
    checkingUpdate: Boolean = false,
    onCheckUpdate: () -> Unit = {}
) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    val installedVersion = remember { UpdateService.currentVersionName(context) }
    ScreenTitle(strings.moreTitle, strings.moreSubtitle)
    AppCard(color = Lavender) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile("wallet", Purple, 54); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(data.settings["user_name"] ?: strings.defaultUserName, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(strings.personalFinanceSpace, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 5.dp))
            }
            IconButton(onProfile) { Glyph("edit", Modifier.size(18.dp), Purple) }
        }
    }
    AppCard {
        SettingsRow("target", strings.menuBudgetAndGoals, strings.menuBudgetAndGoalsSub, { navigate("budget") })
        HorizontalDivider(color = Line)
        SettingsRow("wallet", strings.menuWallets, strings.menuWalletsSub, { navigate("wallets") })
        HorizontalDivider(color = Line)
        SettingsRow("grid", strings.menuCategories, strings.menuCategoriesSub, { navigate("categories") })
        HorizontalDivider(color = Line)
        SettingsRow("repeat", strings.menuRecurring, strings.menuRecurringSub, { navigate("recurring") })
        HorizontalDivider(color = Line)
        SettingsRow("people", strings.menuDebts, strings.menuDebtsSub, { navigate("debts") })
    }
    AppCard {
        SettingsRow("settings", strings.menuSettings, strings.menuSettingsSub, { navigate("settings") })
        HorizontalDivider(color = Line)
        val updateDetail = when {
            checkingUpdate -> strings.checkingForUpdates
            availableUpdate != null -> strings.updateAvailableBadge(availableUpdate.tagName)
            else -> strings.currentVersionDetail(installedVersion)
        }
        SettingsRow(
            icon = "download",
            title = strings.checkForUpdateTitle,
            detail = updateDetail,
            onClick = { if (!checkingUpdate) onCheckUpdate() },
            trailing = {
                when {
                    checkingUpdate -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Purple)
                    availableUpdate != null -> Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Purple.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(strings.updateBadgeNew, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Purple)
                    }
                    else -> Glyph("chevron", Modifier.size(16.dp), tint = Muted)
                }
            }
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Glyph("shield", Modifier.size(14.dp), tint = Purple); Spacer(Modifier.width(7.dp))
        Text(strings.personalFinanceFooter, fontSize = 11.sp, color = Muted)
    }
}

@Composable
fun ConfirmDelete(text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(strings.confirmDeleteTitle) },
        text = { Text(text) },
        confirmButton = { TextButton(confirm) { Text(strings.delete, color = Red) } },
        dismissButton = { TextButton(dismiss) { Text(strings.cancel) } }
    )
}
