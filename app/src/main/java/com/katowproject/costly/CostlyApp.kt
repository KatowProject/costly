package com.katowproject.costly

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katowproject.costly.ui.theme.*
import java.time.YearMonth

@Composable
fun CostlyApp(vm: FinanceViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentStrings = remember(vm.data.settings["language"]) {
        LocalizationManager.getStrings(context, vm.data.settings["language"])
    }
    val currentCurrency = remember(vm.data.settings["currency"], currentStrings.language) {
        LocalizationManager.getCurrency(vm.data.settings["currency"], currentStrings.language.defaultCurrency)
    }
    CompositionLocalProvider(
        LocalStrings provides currentStrings,
        LocalCurrency provides currentCurrency
    ) {
        CostlyAppContent(vm)
    }
}

@Composable
private fun CostlyAppContent(vm: FinanceViewModel) {
    val strings = LocalStrings.current
    var screen by rememberSaveable { mutableStateOf("home") }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    var showEntry by rememberSaveable { mutableStateOf(false) }
    var editId by rememberSaveable { mutableLongStateOf(0L) }
    var dialog by remember { mutableStateOf("") }
    var goal by remember { mutableStateOf<Goal?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val data = vm.data
    val scrollState = rememberScrollState()
    LaunchedEffect(screen) { scrollState.scrollTo(0) }
    LaunchedEffect(vm.message) { vm.message?.let { snackbar.showSnackbar(it); vm.message = null } }
    BackHandler(enabled = showEntry || screen != "home") {
        if (showEntry) showEntry = false
        else screen = if (screen in listOf("settings", "categories", "wallets", "recurring", "debts")) "more" else "home"
    }
    val addEntry: () -> Unit = { editId = 0; showEntry = true }
    Scaffold(
        containerColor = Page,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (!showEntry) BottomBar(screen, { screen = it }, addEntry) }
    ) { padding ->
        if (!vm.ready) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Purple) }
        } else if (showEntry) {
            EntryEditor(vm, data.entries.firstOrNull { it.id == editId }, { showEntry = false }, Modifier.padding(padding))
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).widthIn(max = 680.dp).padding(horizontal = 22.dp)
                    .verticalScroll(scrollState).padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                when (screen) {
                    "home" -> Dashboard(data, month, { monthText = it.toString() }, addEntry, { screen = it }, { dialog = it }, { editId = it.id; showEntry = true })
                    "transactions" -> TransactionsScreen(data, month, { monthText = it.toString() }, { editId = it.id; showEntry = true })
                    "reports" -> ReportsScreen(data, month, { monthText = it.toString() })
                    "budget" -> BudgetScreen(vm, month, { monthText = it.toString() }, { dialog = "budget" }, { dialog = "goal" }, { goal = it; dialog = "saving" })
                    "more" -> MoreScreen(data, { screen = it }, { dialog = "profile" })
                    "settings" -> SettingsScreen(vm, { screen = "more" })
                    "wallets" -> WalletsScreen(vm, { screen = "more" }, { dialog = "wallet" })
                    "categories" -> CategoriesScreen(vm, { screen = "more" }, { dialog = "category" })
                    "recurring" -> RecurringScreen(vm, { screen = "more" }, { dialog = "recurring" })
                    "debts" -> DebtsScreen(vm, { screen = "more" }, { dialog = "debt" })
                }
                Text(strings.tagline, Modifier.fillMaxWidth().padding(top = 4.dp), fontSize = 9.sp, letterSpacing = 1.5.sp, color = Muted.copy(alpha = .65f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
    if (dialog.isNotEmpty()) EntityDialog(dialog, vm, month, goal, { dialog = "" })
}

@Composable
private fun BottomBar(screen: String, onScreen: (String) -> Unit, onAdd: () -> Unit) {
    val strings = LocalStrings.current
    Surface(color = CardBg, shadowElevation = 12.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
            listOf("home" to strings.navHome, "transactions" to strings.navTransactions, "add" to "", "reports" to strings.navReports, "more" to strings.navMore).forEach { (key, label) ->
                if (key == "add") Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val interactionSource = remember { MutableInteractionSource() }
                    val isPressed by interactionSource.collectIsPressedAsState()
                    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, label = "fab_scale")
                    Box(
                        Modifier
                            .scale(scale)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFA76AF7), Purple, Color(0xFF5B24BA))))
                            .clickable(interactionSource = interactionSource, indication = null, onClick = onAdd),
                        contentAlignment = Alignment.Center
                    ) {
                        Glyph("plus", Modifier.size(26.dp), tint = Color.White, description = strings.addTransactionDesc)
                    }
                } else {
                    val selected = screen == key || (key == "more" && screen in listOf("budget", "settings", "categories", "wallets", "recurring", "debts"))
                    val animColor by animateColorAsState(if (selected) Purple else Muted, animationSpec = tween(220), label = "tab_color")
                    val animBg by animateColorAsState(if (selected) Lavender else Color.Transparent, animationSpec = tween(220), label = "tab_bg")
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onScreen(key) }.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(animBg).padding(horizontal = 13.dp, vertical = 4.dp)) {
                            Glyph(when(key) { "home" -> "home"; "transactions" -> "list"; "reports" -> "chart"; else -> "menu" }, Modifier.size(21.dp), tint = animColor)
                        }
                        Text(label, fontSize = 10.sp, color = animColor, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
fun ColumnScope.Dashboard(data: FinanceData, month: YearMonth, onMonth: (YearMonth) -> Unit, onAdd: () -> Unit, navigate: (String) -> Unit, onDialog: (String) -> Unit, onEntry: (Entry) -> Unit) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    var hidden by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(strings.greeting(java.time.LocalTime.now().hour), fontSize = 13.sp, color = Muted)
            Text("${data.settings["user_name"] ?: strings.defaultUserName} 👋", fontSize = 25.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.8).sp)
        }
        Surface(onClick = { navigate("budget") }, color = CardBg, shape = CircleShape, border = BorderStroke(1.dp, Line)) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Glyph("target", Modifier.size(22.dp), tint = Purple, description = strings.budgetAndGoalsDesc)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MonthChip(month, onMonth)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(5.dp).background(Green, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(strings.savedOffline, color = Muted, fontSize = 10.sp)
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(23.dp)).background(Brush.linearGradient(listOf(Color(0xFF9A5AF0), Color(0xFF7436D8), Color(0xFF5522B0)))).padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).background(Color.White.copy(alpha = .15f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Glyph("wallet", Modifier.size(19.dp), Color.White) }
            Spacer(Modifier.width(10.dp))
            Text(strings.totalBalance, color = Color.White.copy(alpha = .85f), fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton({ hidden = !hidden }, Modifier.size(30.dp)) { Glyph("eye", Modifier.size(20.dp), Color.White, strings.toggleBalanceDesc) }
        }
        Text(if (hidden) "${currency.symbol} ••••••••" else currency.format(data.balance(), strings.language), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(strings.walletsConnected(data.wallets.size), color = Color.White.copy(alpha = .7f), fontSize = 11.sp, modifier = Modifier.weight(1f))
            Text("COSTLY", color = Color.White.copy(alpha = .65f), fontSize = 10.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SummaryTile(strings.income, data.total("INCOME", month), "trend", Green, Modifier.weight(1f), hidden)
        SummaryTile(strings.expense, data.total("EXPENSE", month), "expense", Red, Modifier.weight(1f), hidden)
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(Triple(strings.quickAdd, "plus", "add"), Triple(strings.quickBudget, "target", "budget"), Triple(strings.quickWallets, "wallet", "wallets"), Triple(strings.quickReports, "chart", "reports")).forEach { (label, icon, route) ->
            Column(Modifier.width(65.dp).clip(RoundedCornerShape(12.dp)).clickable { if (route == "add") onAdd() else navigate(route) }.padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTile(icon, Purple, 44)
                Text(label, fontSize = 11.sp, color = Ink, fontWeight = FontWeight.Medium)
            }
        }
    }
    AppCard {
        Heading(strings.thisMonthSummary, strings.details, { navigate("reports") })
        Spacer(Modifier.height(16.dp))
        CategoryDonut(data, month)
    }
    AppCard {
        Heading(strings.monthlyBudget, strings.manage, { navigate("budget") })
        val budgets = data.budgets.filter { it.month == month.toString() }
        if (budgets.isEmpty()) EmptyState(strings.setSpendingLimit, strings.setSpendingLimitSub, "target", strings.createBudget, { onDialog("budget") })
        else budgets.take(3).forEach { budget -> BudgetRow(data, budget, month) }
    }
    AppCard {
        Heading(strings.recentTransactions, strings.viewAll, { navigate("transactions") })
        val recent = data.monthly(month).take(4)
        if (recent.isEmpty()) EmptyState(strings.emptyTransactionsTitle, strings.emptyTransactionsSub, action = strings.addTransaction, onAction = onAdd)
        else recent.forEach { TransactionRow(it, data, onEntry) }
    }
}

@Composable
fun SummaryTile(title: String, amount: Long, icon: String, color: Color, modifier: Modifier = Modifier, hidden: Boolean = false) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = .055f)).border(1.dp, color.copy(alpha = .12f), RoundedCornerShape(16.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Glyph(icon, Modifier.size(17.dp), color); Spacer(Modifier.width(6.dp)); Text(title, fontSize = 11.sp, color = Muted) }
        Text(if (hidden) "${currency.symbol} ••••••" else currency.format(amount, strings.language), fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.5).sp, maxLines = 1)
    }
}

@Composable
fun TransactionRow(entry: Entry, data: FinanceData, onClick: (Entry) -> Unit) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    val cat = data.category(entry.categoryId)
    val color = if (entry.type == "INCOME") Green else cat?.let { Color(it.color) } ?: Red
    val catName = cat?.let { strings.categoryName(it.name) }
    val walletName = data.wallet(entry.walletId)?.let { strings.walletName(it.name) }.orEmpty()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onClick(entry) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(cat?.icon ?: "wallet", color, 42); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(entry.note.ifBlank { catName ?: strings.defaultTransaction }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text("${catName.orEmpty()} · $walletName", fontSize = 10.sp, color = Muted, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text((if (entry.type == "INCOME") "+ " else "− ") + currency.format(entry.amount, strings.language), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (entry.type == "INCOME") Green else Red)
            Text(localizedDateLabel(entry.date, strings.lang), fontSize = 10.sp, color = Muted)
        }
    }
}

@Composable
fun BudgetRow(data: FinanceData, budget: Budget, month: YearMonth) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    val cat = data.category(budget.categoryId)
    val spent = data.spent(budget.categoryId, month)
    val ratio = spent.toFloat() / budget.amount
    val catName = cat?.let { strings.categoryName(it.name) }.orEmpty()
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(cat?.icon ?: "grid", cat?.let { Color(it.color) } ?: Purple, 38); Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row {
                Text(catName, Modifier.weight(1f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("${(ratio * 100).toInt()}%", fontSize = 11.sp, color = if (ratio > 1) Red else Ink, fontWeight = FontWeight.SemiBold)
            }
            Text("${currency.format(spent, strings.language)} / ${currency.format(budget.amount, strings.language)}", fontSize = 10.sp, color = Muted)
            ProgressBar(ratio, if (ratio > 1) Red else if (ratio > .8f) Color(0xFFF0AD42) else Purple)
            if (ratio > 1) Text(strings.overBudgetBy(currency.format(spent - budget.amount, strings.language)), fontSize = 10.sp, color = Red)
        }
    }
}
