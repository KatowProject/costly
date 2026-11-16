package com.katowproject.costly

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Language(
    val code: String,
    val displayName: String,
    val nativeName: String,
    val locale: Locale,
    val defaultCurrency: String = "IDR"
)

data class AppCurrency(
    val code: String,
    val symbol: String,
    val name: String,
    val symbolPrefix: Boolean = true
) {
    fun format(amount: Long, locale: Locale): String =
        LocalizationManager.formatMoney(amount, this, locale)

    fun format(amount: Long, language: Language): String =
        format(amount, language.locale)
}

enum class AppLanguage(val code: String, val displayName: String, val locale: Locale) {
    ID("id", "Bahasa Indonesia", Locale.forLanguageTag("id-ID")),
    EN("en", "English", Locale.ENGLISH);

    companion object {
        fun fromCode(code: String?): AppLanguage = if (code?.lowercase() == "en") EN else ID
    }
}

class AppStrings(private val context: Context) {
    private fun s(@StringRes id: Int): String = context.getString(id)
    private fun s(@StringRes id: Int, vararg args: Any): String = context.getString(id, *args)

    val language: Language
        get() = LocalizationManager.getLanguage(context.resources.configuration.locales[0].language)

    val lang: AppLanguage
        get() = AppLanguage.fromCode(language.code)

    // Dynamic translation helpers for domain models
    fun categoryName(categoryName: String): String = when (categoryName.trim()) {
        "Konsumsi" -> s(R.string.cat_konsumsi)
        "Transportasi" -> s(R.string.cat_transportasi)
        "Operasional" -> s(R.string.cat_operasional)
        "Utilitas" -> s(R.string.cat_utilitas)
        "Belanja" -> s(R.string.cat_belanja)
        "Lainnya" -> s(R.string.cat_lainnya)
        "Gaji" -> s(R.string.cat_gaji)
        "Pendapatan lain" -> s(R.string.cat_pendapatan_lain)
        else -> categoryName
    }

    fun walletName(walletName: String): String = when (walletName.trim()) {
        "Dompet utama" -> s(R.string.wallet_dompet_utama)
        else -> walletName
    }

    fun walletType(type: String): String = when (type) {
        "CASH" -> s(R.string.wallet_type_cash)
        "BANK" -> s(R.string.wallet_type_bank)
        else -> s(R.string.wallet_type_ewallet)
    }

    fun frequency(freq: String): String = when (freq) {
        "Harian", "Daily" -> s(R.string.freq_daily)
        "Mingguan", "Weekly" -> s(R.string.freq_weekly)
        "Bulanan", "Monthly" -> s(R.string.freq_monthly)
        else -> freq
    }

    fun iconName(icon: String): String = when (icon) {
        "food" -> s(R.string.icon_food)
        "car" -> s(R.string.icon_car)
        "bag" -> s(R.string.icon_bag)
        "shopping" -> s(R.string.icon_shopping)
        "bolt" -> s(R.string.icon_bolt)
        "wallet" -> s(R.string.icon_wallet)
        "target" -> s(R.string.icon_target)
        else -> s(R.string.icon_general)
    }

    fun amountWithSymbol(symbol: String) = s(R.string.amount_with_symbol, symbol)
    fun initialBalanceWithSymbol(symbol: String) = s(R.string.initial_balance_with_symbol, symbol)
    fun targetAmountWithSymbol(symbol: String) = s(R.string.target_amount_with_symbol, symbol)
    fun amountPaidWithSymbol(symbol: String) = s(R.string.amount_paid_with_symbol, symbol)

    fun greeting(hour: Int): String {
        val id = when (hour) {
            in 4..10 -> R.string.greeting_morning
            in 11..14 -> R.string.greeting_afternoon
            in 15..17 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }
        return s(id)
    }

    fun walletsConnected(count: Int) = s(R.string.wallets_connected, count)
    fun overBudgetBy(amount: String) = s(R.string.over_budget_by, amount)
    fun transactionCount(count: Int) = s(R.string.transaction_count, count)
    fun exportFailed(err: String) = s(R.string.export_failed, err)
    fun targetDeadline(deadline: String) = s(R.string.target_deadline, deadline)
    fun confirmDeleteGoal(name: String) = s(R.string.confirm_delete_goal, name)
    fun walletsSubtitle(count: Int, balance: String) = s(R.string.wallets_subtitle, count, balance)
    fun openingBalance(amt: String) = s(R.string.opening_balance, amt)
    fun confirmDeleteWallet(name: String) = s(R.string.confirm_delete_wallet, name)
    fun deleteCategoryDesc(name: String) = s(R.string.delete_category_desc, name)
    fun confirmDeleteCategory(name: String) = s(R.string.confirm_delete_category, name)
    fun nextDate(date: String) = s(R.string.next_date, date)
    fun confirmDeleteRecurring(name: String) = s(R.string.confirm_delete_recurring, name)
    fun dueDate(d: String) = s(R.string.due_date, d)
    fun outOfTotal(tot: String) = s(R.string.out_of_total, tot)
    fun debtPayNote(remaining: String) = s(R.string.debt_pay_note, remaining)
    fun confirmDeleteDebt(name: String) = s(R.string.confirm_delete_debt, name)
    fun everyHours(h: String) = s(R.string.every_hours, h)
    fun keepVersions(v: String) = s(R.string.keep_versions, v)
    fun lastBackupLabel(last: String) = s(R.string.last_backup_label, last)
    fun autoBackupErrorLabel(err: String) = s(R.string.auto_backup_error_label, err)
    fun lastVersionsText(v: Int) = s(R.string.last_versions_text, v)
    fun tryAgainIn(sec: Long) = s(R.string.try_again_in, sec)
    fun confirmDeleteTransaction(amount: String) = s(R.string.confirm_delete_transaction, amount)
    fun budgetAppliesTo(month: String) = s(R.string.budget_applies_to, month)
    fun goalProgressNote(name: String) = s(R.string.goal_progress_note, name)
    fun selectFieldPlaceholder(label: String) = s(R.string.select_field_placeholder, label)
    val navHome: String get() = s(R.string.nav_home)
    val navTransactions: String get() = s(R.string.nav_transactions)
    val navReports: String get() = s(R.string.nav_reports)
    val navMore: String get() = s(R.string.nav_more)
    val addTransactionDesc: String get() = s(R.string.add_transaction_desc)
    val defaultUserName: String get() = s(R.string.default_user_name)
    val budgetAndGoalsDesc: String get() = s(R.string.budget_and_goals_desc)
    val savedOffline: String get() = s(R.string.saved_offline)
    val totalBalance: String get() = s(R.string.total_balance)
    val toggleBalanceDesc: String get() = s(R.string.toggle_balance_desc)
    val income: String get() = s(R.string.income)
    val expense: String get() = s(R.string.expense)
    val quickAdd: String get() = s(R.string.quick_add)
    val quickBudget: String get() = s(R.string.quick_budget)
    val quickWallets: String get() = s(R.string.quick_wallets)
    val quickReports: String get() = s(R.string.quick_reports)
    val thisMonthSummary: String get() = s(R.string.this_month_summary)
    val details: String get() = s(R.string.details)
    val monthlyBudget: String get() = s(R.string.monthly_budget)
    val manage: String get() = s(R.string.manage)
    val setSpendingLimit: String get() = s(R.string.set_spending_limit)
    val setSpendingLimitSub: String get() = s(R.string.set_spending_limit_sub)
    val createBudget: String get() = s(R.string.create_budget)
    val recentTransactions: String get() = s(R.string.recent_transactions)
    val viewAll: String get() = s(R.string.view_all)
    val emptyTransactionsTitle: String get() = s(R.string.empty_transactions_title)
    val emptyTransactionsSub: String get() = s(R.string.empty_transactions_sub)
    val addTransaction: String get() = s(R.string.add_transaction)
    val tagline: String get() = s(R.string.tagline)
    val defaultTransaction: String get() = s(R.string.default_transaction)
    val transactionsTitle: String get() = s(R.string.transactions_title)
    val transactionsSubtitle: String get() = s(R.string.transactions_subtitle)
    val searchPlaceholder: String get() = s(R.string.search_placeholder)
    val allWallets: String get() = s(R.string.all_wallets)
    val filterWalletDesc: String get() = s(R.string.filter_wallet_desc)
    val segmentAll: String get() = s(R.string.segment_all)
    val segmentIncome: String get() = s(R.string.segment_income)
    val segmentExpense: String get() = s(R.string.segment_expense)
    val noTransactions: String get() = s(R.string.no_transactions)
    val noTransactionsSub: String get() = s(R.string.no_transactions_sub)
    val today: String get() = s(R.string.today)
    val reportsTitle: String get() = s(R.string.reports_title)
    val reportsSubtitle: String get() = s(R.string.reports_subtitle)
    val segmentSummary: String get() = s(R.string.segment_summary)
    val segmentCategory: String get() = s(R.string.segment_category)
    val segmentTrend: String get() = s(R.string.segment_trend)
    val reportPeriod: String get() = s(R.string.report_period)
    val netDifference: String get() = s(R.string.net_difference)
    val cashFlowTrend: String get() = s(R.string.cash_flow_trend)
    val expenseDistribution: String get() = s(R.string.expense_distribution)
    val topExpenseCategories: String get() = s(R.string.top_expense_categories)
    val noExpense: String get() = s(R.string.no_expense)
    val noExpenseSub: String get() = s(R.string.no_expense_sub)
    val cashFlowAccumulation: String get() = s(R.string.cash_flow_accumulation)
    val exportPdf: String get() = s(R.string.export_pdf)
    val exportExcel: String get() = s(R.string.export_excel)
    val exportCsv: String get() = s(R.string.export_csv)
    val csvExportSuccess: String get() = s(R.string.csv_export_success)
    val pdfExportSuccess: String get() = s(R.string.pdf_export_success)
    val excelExportSuccess: String get() = s(R.string.excel_export_success)
    val reportSheetName: String get() = s(R.string.report_sheet_name)
    val reportPdfTitle: String get() = s(R.string.report_pdf_title)
    val reportNoTransactions: String get() = s(R.string.report_no_transactions)
    val reportTransactionDetails: String get() = s(R.string.report_transaction_details)
    val tableDate: String get() = s(R.string.table_date)
    val tableType: String get() = s(R.string.table_type)
    val tableNote: String get() = s(R.string.table_note)
    val tableCategory: String get() = s(R.string.table_category)
    val tableWallet: String get() = s(R.string.table_wallet)
    val tableAmount: String get() = s(R.string.table_amount)
    val budgetAndGoalsTitle: String get() = s(R.string.budget_and_goals_title)
    val budgetAndGoalsSubtitle: String get() = s(R.string.budget_and_goals_subtitle)
    val segmentBudget: String get() = s(R.string.segment_budget)
    val segmentGoals: String get() = s(R.string.segment_goals)
    val planExpenses: String get() = s(R.string.plan_expenses)
    val planExpensesSub: String get() = s(R.string.plan_expenses_sub)
    val deleteBudget: String get() = s(R.string.delete_budget)
    val addOrEditBudget: String get() = s(R.string.add_or_edit_budget)
    val savingsGoals: String get() = s(R.string.savings_goals)
    val add: String get() = s(R.string.add)
    val goalsSeparateNote: String get() = s(R.string.goals_separate_note)
    val realizePlans: String get() = s(R.string.realize_plans)
    val realizePlansSub: String get() = s(R.string.realize_plans_sub)
    val targetReachedAddProgress: String get() = s(R.string.target_reached_add_progress)
    val addSavings: String get() = s(R.string.add_savings)
    val createSavingsGoal: String get() = s(R.string.create_savings_goal)
    val newSavingsGoal: String get() = s(R.string.new_savings_goal)
    val confirmDeleteBudget: String get() = s(R.string.confirm_delete_budget)
    val budgetDeleted: String get() = s(R.string.budget_deleted)
    val goalDeleted: String get() = s(R.string.goal_deleted)
    val moreTitle: String get() = s(R.string.more_title)
    val moreSubtitle: String get() = s(R.string.more_subtitle)
    val personalFinanceSpace: String get() = s(R.string.personal_finance_space)
    val menuBudgetAndGoals: String get() = s(R.string.menu_budget_and_goals)
    val menuBudgetAndGoalsSub: String get() = s(R.string.menu_budget_and_goals_sub)
    val menuWallets: String get() = s(R.string.menu_wallets)
    val menuWalletsSub: String get() = s(R.string.menu_wallets_sub)
    val menuCategories: String get() = s(R.string.menu_categories)
    val menuCategoriesSub: String get() = s(R.string.menu_categories_sub)
    val menuRecurring: String get() = s(R.string.menu_recurring)
    val menuRecurringSub: String get() = s(R.string.menu_recurring_sub)
    val menuDebts: String get() = s(R.string.menu_debts)
    val menuDebtsSub: String get() = s(R.string.menu_debts_sub)
    val menuSettings: String get() = s(R.string.menu_settings)
    val menuSettingsSub: String get() = s(R.string.menu_settings_sub)
    val personalFinanceFooter: String get() = s(R.string.personal_finance_footer)
    val walletsTitle: String get() = s(R.string.wallets_title)
    val addWallet: String get() = s(R.string.add_wallet)
    val deleteWalletDesc: String get() = s(R.string.delete_wallet_desc)
    val walletDeleted: String get() = s(R.string.wallet_deleted)
    val walletInUseError: String get() = s(R.string.wallet_in_use_error)
    val categoriesTitle: String get() = s(R.string.categories_title)
    val categoriesSubtitle: String get() = s(R.string.categories_subtitle)
    val addCategory: String get() = s(R.string.add_category)
    val categoryDeleted: String get() = s(R.string.category_deleted)
    val categoryInUseError: String get() = s(R.string.category_in_use_error)
    val recurringTitle: String get() = s(R.string.recurring_title)
    val recurringSubtitle: String get() = s(R.string.recurring_subtitle)
    val recurringCardTitle: String get() = s(R.string.recurring_card_title)
    val recurringCardDesc: String get() = s(R.string.recurring_card_desc)
    val noRecurring: String get() = s(R.string.no_recurring)
    val noRecurringSub: String get() = s(R.string.no_recurring_sub)
    val addRecurring: String get() = s(R.string.add_recurring)
    val recurringStopped: String get() = s(R.string.recurring_stopped)
    val frequencyDaily: String get() = s(R.string.frequency_daily)
    val frequencyWeekly: String get() = s(R.string.frequency_weekly)
    val frequencyMonthly: String get() = s(R.string.frequency_monthly)
    val debtsTitle: String get() = s(R.string.debts_title)
    val debtsSubtitle: String get() = s(R.string.debts_subtitle)
    val myDebt: String get() = s(R.string.my_debt)
    val myReceivable: String get() = s(R.string.my_receivable)
    val noDebts: String get() = s(R.string.no_debts)
    val noDebtsSub: String get() = s(R.string.no_debts_sub)
    val paidOff: String get() = s(R.string.paid_off)
    val recordPayment: String get() = s(R.string.record_payment)
    val addDebtOrReceivable: String get() = s(R.string.add_debt_or_receivable)
    val amountPaid: String get() = s(R.string.amount_paid)
    val paymentRecorded: String get() = s(R.string.payment_recorded)
    val debtDeleted: String get() = s(R.string.debt_deleted)
    val settingsTitle: String get() = s(R.string.settings_title)
    val settingsSubtitle: String get() = s(R.string.settings_subtitle)
    val languageCardTitle: String get() = s(R.string.language_card_title)
    val languageSettingTitle: String get() = s(R.string.language_setting_title)
    val languageSettingDesc: String get() = s(R.string.language_setting_desc)
    val selectLanguageTitle: String get() = s(R.string.select_language_title)
    val currencyCardTitle: String get() = s(R.string.currency_card_title)
    val currencySettingTitle: String get() = s(R.string.currency_setting_title)
    val selectCurrencyTitle: String get() = s(R.string.select_currency_title)
    val backupAndRestore: String get() = s(R.string.backup_and_restore)
    val googleDriveTitle: String get() = s(R.string.google_drive_title)
    val googleDriveDesc: String get() = s(R.string.google_drive_desc)
    val connectGoogleDrive: String get() = s(R.string.connect_google_drive)
    val disconnectGoogleDrive: String get() = s(R.string.disconnect_google_drive)
    fun connectedAs(email: String): String = s(R.string.connected_as, email)
    val gdriveSyncAuto: String get() = s(R.string.gdrive_sync_auto)
    val gdriveSyncAutoSub: String get() = s(R.string.gdrive_sync_auto_sub)
    val backupToDriveNow: String get() = s(R.string.backup_to_drive_now)
    val backupToDriveNowSub: String get() = s(R.string.backup_to_drive_now_sub)
    val restoreFromGdrive: String get() = s(R.string.restore_from_gdrive)
    val restoreFromGdriveSub: String get() = s(R.string.restore_from_gdrive_sub)
    val selectDriveBackupTitle: String get() = s(R.string.select_drive_backup_title)
    val noDriveBackups: String get() = s(R.string.no_drive_backups)
    val fetchingDriveBackups: String get() = s(R.string.fetching_drive_backups)
    val driveBackupSuccess: String get() = s(R.string.drive_backup_success)
    fun lastGdriveBackupLabel(last: String): String = s(R.string.last_gdrive_backup_label, last)
    fun autoGdriveBackupErrorLabel(err: String): String = s(R.string.auto_gdrive_backup_error_label, err)
    val confirmDisconnectGdrive: String get() = s(R.string.confirm_disconnect_gdrive)
    val themeModeTitle: String get() = s(R.string.theme_mode_title)
    val themeModeSub: String get() = s(R.string.theme_mode_sub)
    val themeSystem: String get() = s(R.string.theme_system)
    val themeLight: String get() = s(R.string.theme_light)
    val themeDark: String get() = s(R.string.theme_dark)
    val lockTimeoutTitle: String get() = s(R.string.lock_timeout_title)
    val lockTimeoutSub: String get() = s(R.string.lock_timeout_sub)
    val lockImmediately: String get() = s(R.string.lock_immediately)
    val lockAfter30s: String get() = s(R.string.lock_after_30s)
    val lockAfter1m: String get() = s(R.string.lock_after_1m)
    val lockAfter5m: String get() = s(R.string.lock_after_5m)
    fun lastBackupSuccess(last: String): String = s(R.string.last_backup_success, last)
    val neverBackedUp: String get() = s(R.string.never_backed_up)
    fun lastCloudSyncSuccess(last: String): String = s(R.string.last_cloud_sync_success, last)
    val neverCloudSynced: String get() = s(R.string.never_cloud_synced)
    fun appVersionLabel(version: String): String = s(R.string.app_version_label, version)
    val automaticBackup: String get() = s(R.string.automatic_backup)
    val automaticBackupSub: String get() = s(R.string.automatic_backup_sub)
    val backupFolder: String get() = s(R.string.backup_folder)
    val folderNotSelected: String get() = s(R.string.folder_not_selected)
    val folderConnected: String get() = s(R.string.folder_connected)
    val backupFolderHelp: String get() = s(R.string.backup_folder_help)
    val backupPassword: String get() = s(R.string.backup_password)
    val backupPasswordSub: String get() = s(R.string.backup_password_sub)
    val backupSchedule: String get() = s(R.string.backup_schedule)
    val backupRetention: String get() = s(R.string.backup_retention)
    val backupNow: String get() = s(R.string.backup_now)
    val backupNowSub: String get() = s(R.string.backup_now_sub)
    val restoreData: String get() = s(R.string.restore_data)
    val restoreDataSub: String get() = s(R.string.restore_data_sub)
    val never: String get() = s(R.string.never)
    val security: String get() = s(R.string.security)
    val appLock: String get() = s(R.string.app_lock)
    val appLockSub: String get() = s(R.string.app_lock_sub)
    val yourDataYourOwnership: String get() = s(R.string.your_data_your_ownership)
    val dataOwnershipDesc: String get() = s(R.string.data_ownership_desc)
    val appVersionNote: String get() = s(R.string.app_version_note)
    val enableAppLock: String get() = s(R.string.enable_app_lock)
    val disableAppLock: String get() = s(R.string.disable_app_lock)
    val restoreBackup: String get() = s(R.string.restore_backup)
    val restoreWarning: String get() = s(R.string.restore_warning)
    val pin6Digits: String get() = s(R.string.pin6_digits)
    val repeatPin: String get() = s(R.string.repeat_pin)
    val repeatPassword: String get() = s(R.string.repeat_password)
    val newPasswordNote: String get() = s(R.string.new_password_note)
    val settingsSaved: String get() = s(R.string.settings_saved)
    val backupEncryptedSuccess: String get() = s(R.string.backup_encrypted_success)
    val setBackupPasswordFirst: String get() = s(R.string.set_backup_password_first)
    val selectBackupFolderFirst: String get() = s(R.string.select_backup_folder_first)
    val backupFolderConnected: String get() = s(R.string.backup_folder_connected)
    val backupPasswordSaved: String get() = s(R.string.backup_password_saved)
    val appLockEnabled: String get() = s(R.string.app_lock_enabled)
    val appLockDisabled: String get() = s(R.string.app_lock_disabled)
    val pinIncorrect: String get() = s(R.string.pin_incorrect)
    val restoreSuccess: String get() = s(R.string.restore_success)
    val close: String get() = s(R.string.close)
    val welcomeBack: String get() = s(R.string.welcome_back)
    val unlockFinanceSpace: String get() = s(R.string.unlock_finance_space)
    val enterAppPin: String get() = s(R.string.enter_app_pin)
    val sixDigitsPlaceholder: String get() = s(R.string.six_digits_placeholder)
    val checking: String get() = s(R.string.checking)
    val unlockCostly: String get() = s(R.string.unlock_costly)
    val useBiometric: String get() = s(R.string.use_biometric)
    val tooManyAttempts: String get() = s(R.string.too_many_attempts)
    val pinWrongTryAgain: String get() = s(R.string.pin_wrong_try_again)
    val biometricTitle: String get() = s(R.string.biometric_title)
    val biometricSubtitle: String get() = s(R.string.biometric_subtitle)
    val usePinButton: String get() = s(R.string.use_pin_button)
    val biometricUnavailable: String get() = s(R.string.biometric_unavailable)
    val addTransactionTitle: String get() = s(R.string.add_transaction_title)
    val transactionDetailTitle: String get() = s(R.string.transaction_detail_title)
    val deleteTransactionDesc: String get() = s(R.string.delete_transaction_desc)
    val transactionAmount: String get() = s(R.string.transaction_amount)
    val category: String get() = s(R.string.category)
    val walletOrAccount: String get() = s(R.string.wallet_or_account)
    val date: String get() = s(R.string.date)
    val noteOptional: String get() = s(R.string.note_optional)
    val notePlaceholder: String get() = s(R.string.note_placeholder)
    val attachmentOptional: String get() = s(R.string.attachment_optional)
    val camera: String get() = s(R.string.camera)
    val gallery: String get() = s(R.string.gallery)
    val file: String get() = s(R.string.file)
    val selectedAttachment: String get() = s(R.string.selected_attachment)
    val open: String get() = s(R.string.open)
    val removeAttachmentDesc: String get() = s(R.string.remove_attachment_desc)
    val attachmentLimitHelp: String get() = s(R.string.attachment_limit_help)
    val savingProgress: String get() = s(R.string.saving_progress)
    val saveTransaction: String get() = s(R.string.save_transaction)
    val transactionSavedSuccess: String get() = s(R.string.transaction_saved_success)
    val transactionDeletedSuccess: String get() = s(R.string.transaction_deleted_success)
    val cameraUnavailable: String get() = s(R.string.camera_unavailable)
    val noAppToOpenAttachment: String get() = s(R.string.no_app_to_open_attachment)
    val attachmentTooLarge: String get() = s(R.string.attachment_too_large)
    val attachmentUnreadable: String get() = s(R.string.attachment_unreadable)
    val dialogTitleBudget: String get() = s(R.string.dialog_title_budget)
    val dialogTitleGoal: String get() = s(R.string.dialog_title_goal)
    val dialogTitleSaving: String get() = s(R.string.dialog_title_saving)
    val dialogTitleWallet: String get() = s(R.string.dialog_title_wallet)
    val dialogTitleCategory: String get() = s(R.string.dialog_title_category)
    val dialogTitleDebt: String get() = s(R.string.dialog_title_debt)
    val dialogTitleRecurring: String get() = s(R.string.dialog_title_recurring)
    val dialogTitleProfile: String get() = s(R.string.dialog_title_profile)
    val savedSuccess: String get() = s(R.string.saved_success)
    val fieldPersonOrPurpose: String get() = s(R.string.field_person_or_purpose)
    val fieldName: String get() = s(R.string.field_name)
    val placeholderGoal: String get() = s(R.string.placeholder_goal)
    val placeholderName: String get() = s(R.string.placeholder_name)
    val walletType: String get() = s(R.string.wallet_type)
    val initialBalanceRp: String get() = s(R.string.initial_balance_rp)
    val targetAmountRp: String get() = s(R.string.target_amount_rp)
    val amountRp: String get() = s(R.string.amount_rp)
    val startDate: String get() = s(R.string.start_date)
    val targetOrDueDate: String get() = s(R.string.target_or_due_date)
    val wallet: String get() = s(R.string.wallet)
    val frequency: String get() = s(R.string.frequency)
    val recurringDueHelp: String get() = s(R.string.recurring_due_help)
    val icon: String get() = s(R.string.icon)
    val debtCashFlowNote: String get() = s(R.string.debt_cash_flow_note)
    val save: String get() = s(R.string.save)
    val cancel: String get() = s(R.string.cancel)
    val delete: String get() = s(R.string.delete)
    val confirmDeleteTitle: String get() = s(R.string.confirm_delete_title)
    val selectPeriod: String get() = s(R.string.select_period)
    val thisMonth: String get() = s(R.string.this_month)

}

object LocalizationManager {
    private val languages = listOf(
        Language("id", "Bahasa Indonesia", "Bahasa Indonesia", Locale.forLanguageTag("id-ID"), "IDR"),
        Language("en", "English", "English", Locale.ENGLISH, "USD")
    )
    private val currencies = linkedMapOf<String, AppCurrency>()

    val defaultLanguage: Language get() = languages[0]
    val defaultCurrency: AppCurrency get() = currencies["IDR"] ?: AppCurrency("IDR", "Rp", "Indonesian Rupiah")

    init {
        registerCurrency(AppCurrency("IDR", "Rp", "Indonesian Rupiah"))
        registerCurrency(AppCurrency("USD", "$", "US Dollar"))
        registerCurrency(AppCurrency("EUR", "€", "Euro"))
        registerCurrency(AppCurrency("GBP", "£", "British Pound"))
        registerCurrency(AppCurrency("SGD", "S$", "Singapore Dollar"))
        registerCurrency(AppCurrency("MYR", "RM", "Malaysian Ringgit"))
        registerCurrency(AppCurrency("JPY", "¥", "Japanese Yen"))
        registerCurrency(AppCurrency("AUD", "A$", "Australian Dollar"))
    }

    fun getSupportedLanguages(): List<Language> = languages

    fun getLanguage(code: String?): Language =
        languages.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: languages[0]

    fun getContextForLanguage(context: Context, languageCode: String?): Context {
        if (languageCode.isNullOrBlank()) return context
        val lang = getLanguage(languageCode)
        val config = Configuration(context.resources.configuration)
        config.setLocale(lang.locale)
        return context.createConfigurationContext(config)
    }

    fun getStrings(context: Context, languageCode: String? = null): AppStrings {
        val targetContext = getContextForLanguage(context, languageCode)
        return AppStrings(targetContext)
    }

    fun setSystemAppLanguage(context: Context, code: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales =
                LocaleList.forLanguageTags(code)
        }
    }

    fun applyAppLanguage(context: Context, code: String?) {
        if (!code.isNullOrBlank()) {
            setSystemAppLanguage(context, code)
        }
    }

    @Synchronized
    fun registerCurrency(currency: AppCurrency) {
        currencies[currency.code.uppercase()] = currency
    }

    @Synchronized
    fun getSupportedCurrencies(): List<AppCurrency> = currencies.values.toList()

    @Synchronized
    fun getCurrency(code: String?, fallbackCurrency: String = "IDR"): AppCurrency {
        val key = code?.uppercase()
        return currencies[key] ?: currencies[fallbackCurrency.uppercase()] ?: defaultCurrency
    }

    fun formatMoney(amount: Long, currency: AppCurrency, locale: Locale): String {
        val formatted = NumberFormat.getIntegerInstance(locale).format(amount)
        return if (currency.symbolPrefix) "${currency.symbol} $formatted" else "$formatted ${currency.symbol}"
    }

    fun formatMoney(amount: Long, currency: AppCurrency = defaultCurrency, language: Language = defaultLanguage): String =
        formatMoney(amount, currency, language.locale)

    fun formatMonth(month: YearMonth, locale: Locale = Locale.forLanguageTag("id-ID")): String =
        month.format(DateTimeFormatter.ofPattern("MMM yyyy", locale))

    fun formatMonth(month: YearMonth, language: Language): String =
        formatMonth(month, language.locale)

    fun formatDate(date: LocalDate, locale: Locale = Locale.forLanguageTag("id-ID")): String =
        date.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))

    fun formatDate(date: LocalDate, language: Language): String =
        formatDate(date, language.locale)

    fun formatDayDate(date: LocalDate, locale: Locale = Locale.forLanguageTag("id-ID")): String {
        return if (date == LocalDate.now()) {
            if (locale.language == "en") "Today" else "Hari ini"
        } else {
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMM", locale))
        }
    }
}

val LocalStrings = staticCompositionLocalOf<AppStrings> {
    error("LocalStrings not provided")
}
val LocalCurrency = staticCompositionLocalOf { LocalizationManager.defaultCurrency }

// Backward compatibility helpers
fun formatRupiah(amount: Long, lang: AppLanguage = AppLanguage.ID): String =
    LocalizationManager.formatMoney(amount, LocalizationManager.getCurrency("IDR"), lang.locale)

fun formatMoney(amount: Long, currency: AppCurrency = LocalizationManager.defaultCurrency, lang: AppLanguage = AppLanguage.ID): String =
    LocalizationManager.formatMoney(amount, currency, lang.locale)

fun localizedMonthLabel(month: YearMonth, lang: AppLanguage = AppLanguage.ID): String =
    LocalizationManager.formatMonth(month, lang.locale)

fun localizedDateLabel(date: LocalDate, lang: AppLanguage = AppLanguage.ID): String =
    LocalizationManager.formatDate(date, lang.locale)

fun localizedDayDateLabel(date: LocalDate, lang: AppLanguage = AppLanguage.ID): String =
    LocalizationManager.formatDayDate(date, lang.locale)

fun localizedCategoryName(name: String, lang: AppLanguage): String = when (lang) {
    AppLanguage.EN -> when (name.trim()) {
        "Konsumsi" -> "Food & Dining"
        "Transportasi" -> "Transportation"
        "Operasional" -> "Operations"
        "Utilitas" -> "Utilities"
        "Belanja" -> "Shopping"
        "Lainnya" -> "Others"
        "Gaji" -> "Salary"
        "Pendapatan lain" -> "Other Income"
        else -> name
    }
    AppLanguage.ID -> name
}

fun localizedWalletName(name: String, lang: AppLanguage): String = when (lang) {
    AppLanguage.EN -> when (name.trim()) {
        "Dompet utama" -> "Main Wallet"
        else -> name
    }
    AppLanguage.ID -> name
}

fun localizedWalletType(type: String, lang: AppLanguage): String = when (lang) {
    AppLanguage.EN -> when (type) {
        "CASH" -> "Cash"
        "BANK" -> "Bank account"
        else -> "E-wallet"
    }
    AppLanguage.ID -> when (type) {
        "CASH" -> "Tunai"
        "BANK" -> "Rekening bank"
        else -> "E-wallet"
    }
}

fun localizedFrequency(freq: String, lang: AppLanguage): String = when (lang) {
    AppLanguage.EN -> when (freq) {
        "Harian", "Daily" -> "Daily"
        "Mingguan", "Weekly" -> "Weekly"
        "Bulanan", "Monthly" -> "Monthly"
        else -> freq
    }
    AppLanguage.ID -> when (freq) {
        "Harian", "Daily" -> "Harian"
        "Mingguan", "Weekly" -> "Mingguan"
        "Bulanan", "Monthly" -> "Bulanan"
        else -> freq
    }
}

fun canonicalFrequency(freq: String): String = when (freq) {
    "Daily", "Harian" -> "Harian"
    "Weekly", "Mingguan" -> "Mingguan"
    "Monthly", "Bulanan" -> "Bulanan"
    else -> freq
}

fun localizedIconName(icon: String, lang: AppLanguage): String = when (lang) {
    AppLanguage.EN -> when (icon) {
        "food" -> "Food"
        "car" -> "Transportation"
        "bag" -> "Work"
        "shopping" -> "Shopping"
        "bolt" -> "Utilities"
        "wallet" -> "Wallet"
        "target" -> "Goal"
        else -> "General"
    }
    AppLanguage.ID -> when (icon) {
        "food" -> "Makanan"
        "car" -> "Transportasi"
        "bag" -> "Pekerjaan"
        "shopping" -> "Belanja"
        "bolt" -> "Utilitas"
        "wallet" -> "Dompet"
        "target" -> "Target"
        else -> "Umum"
    }
}