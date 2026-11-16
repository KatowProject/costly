@file:Suppress("DEPRECATION")
package com.katowproject.costly

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.katowproject.costly.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ColumnScope.SettingsScreen(vm: FinanceViewModel, back: () -> Unit) {
    val strings = LocalStrings.current
    val currency = LocalCurrency.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = vm.data.settings
    var dialog by remember { mutableStateOf("") }
    var restoreUri by rememberSaveable { mutableStateOf("") }
    var gdriveBackups by remember { mutableStateOf<List<DriveBackup>>(emptyList()) }
    var selectedDriveBackup by remember { mutableStateOf<DriveBackup?>(null) }
    var fetchingGdrive by remember { mutableStateOf(false) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching {
            val account = task.getResult(ApiException::class.java)
            vm.setting("gdrive_backup_enabled", "true")
            vm.message = strings.connectedAs(account.email ?: "")
        }.onFailure {
            vm.message = "Google Sign-In: ${it.message}"
        }
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.action(strings.backupEncryptedSuccess) {
            val password = BackupService.password(context) ?: error(strings.setBackupPasswordFirst)
            val bytes = BackupService.create(vm.store, password)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("File tidak dapat ditulis")
            vm.store.setting("last_backup", java.time.LocalDateTime.now().toString())
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { restoreUri = uri.toString(); dialog = "restore" } }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.action(strings.backupFolderConnected) {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.store.setting("backup_folder", uri.toString())
        }
    }
    ScreenTitle(strings.settingsTitle, strings.settingsSubtitle, back)

    // Language Card
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("globe", Modifier.size(21.dp), Purple)
            Spacer(Modifier.width(10.dp))
            Text(strings.languageCardTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        SettingsRow(
            icon = "globe",
            title = strings.languageSettingTitle,
            detail = "${strings.language.displayName} (${strings.language.nativeName})",
            onClick = { dialog = "language" }
        )
    }

    // Currency Card
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("wallet", Modifier.size(21.dp), Purple)
            Spacer(Modifier.width(10.dp))
            Text(strings.currencyCardTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        SettingsRow(
            icon = "wallet",
            title = strings.currencySettingTitle,
            detail = "${currency.code} (${currency.symbol}) · ${currency.name}",
            onClick = { dialog = "currency" }
        )
    }

    // Backup & Restore Card
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Glyph("cloud", Modifier.size(21.dp), Purple); Spacer(Modifier.width(10.dp)); Text(strings.backupAndRestore, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(12.dp))
        SettingsRow("repeat", strings.automaticBackup, strings.automaticBackupSub, {
            if (settings["backup_enabled"] == "true") vm.setting("backup_enabled", "false")
            else if (settings["backup_folder"].isNullOrBlank()) vm.message = strings.selectBackupFolderFirst
            else if (BackupService.password(context) == null) dialog = "password"
            else vm.setting("backup_enabled", "true")
        }, trailing = {
            Switch(settings["backup_enabled"] == "true", onCheckedChange = { enabled ->
                if (!enabled) vm.setting("backup_enabled", "false")
                else if (settings["backup_folder"].isNullOrBlank()) vm.message = strings.selectBackupFolderFirst
                else if (BackupService.password(context) == null) dialog = "password"
                else vm.setting("backup_enabled", "true")
            }, modifier = Modifier.height(28.dp))
        })
        HorizontalDivider(color = Line)
        SettingsRow("file", strings.backupFolder, if (settings["backup_folder"].isNullOrBlank()) strings.folderNotSelected else strings.folderConnected, { pickFolder.launch(null) })
        Text(strings.backupFolderHelp, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(bottom = 12.dp))
        HorizontalDivider(color = Line)
        SettingsRow("lock", strings.backupPassword, strings.backupPasswordSub, { dialog = "password" })
        HorizontalDivider(color = Line)
        SettingsRow("calendar", strings.backupSchedule, strings.everyHours(settings["backup_hours"] ?: "6"), { dialog = "schedule" })
        HorizontalDivider(color = Line)
        SettingsRow("repeat", strings.backupRetention, strings.keepVersions(settings["backup_retention"] ?: "7"), { dialog = "retention" })
        HorizontalDivider(color = Line)
        SettingsRow("cloud", strings.backupNow, strings.backupNowSub, {
            if (BackupService.password(context) == null) dialog = "password" else createBackup.launch("Costly-${System.currentTimeMillis()}.costly")
        })
        HorizontalDivider(color = Line)
        SettingsRow("download", strings.restoreData, strings.restoreDataSub, { openBackup.launch(arrayOf("*/*")) })
    }
    val last = settings["last_backup"]?.let { runCatching { val date = java.time.LocalDateTime.parse(it); "${localizedDateLabel(date.toLocalDate(), strings.lang)}, ${date.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}" }.getOrDefault(it) } ?: strings.never
    Text(strings.lastBackupLabel(last), fontSize = 11.sp, color = Muted)
    settings["backup_error"]?.takeIf { it.isNotBlank() }?.let { Text(strings.autoBackupErrorLabel(it), fontSize = 11.sp, color = Red) }

    // Google Drive Cloud Backup Card
    val isDriveConnected = GoogleDriveService.isConnected(context)
    val driveAccount = GoogleDriveService.getSignedInAccount(context)
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Glyph("cloud", Modifier.size(21.dp), Purple)
            Spacer(Modifier.width(10.dp))
            Text(strings.googleDriveTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(8.dp))
        Text(strings.googleDriveDesc, fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(12.dp))

        if (!isDriveConnected) {
            OutlinedButton(
                onClick = { googleSignInLauncher.launch(GoogleDriveService.getSignInIntent(context)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Glyph("cloud", Modifier.size(16.dp), Purple)
                Spacer(Modifier.width(8.dp))
                Text(strings.connectGoogleDrive, color = Purple)
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(strings.connectedAs(driveAccount?.email.orEmpty()), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink)
                }
                TextButton(onClick = { dialog = "confirm_disconnect_gdrive" }) {
                    Text(strings.disconnectGoogleDrive, fontSize = 12.sp, color = Red)
                }
            }
            HorizontalDivider(color = Line)
            SettingsRow(
                "repeat",
                strings.gdriveSyncAuto,
                strings.gdriveSyncAutoSub,
                onClick = {
                    val current = settings["gdrive_backup_enabled"] == "true"
                    if (!current && BackupService.password(context) == null) dialog = "password"
                    else vm.setting("gdrive_backup_enabled", if (current) "false" else "true")
                },
                trailing = {
                    Switch(
                        checked = settings["gdrive_backup_enabled"] == "true",
                        onCheckedChange = { enabled ->
                            if (enabled && BackupService.password(context) == null) dialog = "password"
                            else vm.setting("gdrive_backup_enabled", if (enabled) "true" else "false")
                        },
                        modifier = Modifier.height(28.dp)
                    )
                }
            )
            HorizontalDivider(color = Line)
            SettingsRow(
                "cloud",
                strings.backupToDriveNow,
                strings.backupToDriveNowSub,
                onClick = {
                    if (BackupService.password(context) == null) dialog = "password"
                    else {
                        scope.launch {
                            vm.action(strings.driveBackupSuccess) {
                                BackupService.backupToGoogleDrive(context, vm.store)
                            }
                        }
                    }
                }
            )
            HorizontalDivider(color = Line)
            SettingsRow(
                "download",
                strings.restoreFromGdrive,
                strings.restoreFromGdriveSub,
                onClick = {
                    fetchingGdrive = true
                    dialog = "gdrive_restore"
                    scope.launch {
                        runCatching {
                            gdriveBackups = GoogleDriveService.listBackups(context)
                        }.onFailure {
                            vm.message = it.message ?: "Gagal membaca Google Drive"
                            dialog = ""
                        }
                        fetchingGdrive = false
                    }
                }
            )
        }
    }
    val lastDrive = settings["last_gdrive_backup"]?.let {
        runCatching {
            val date = java.time.LocalDateTime.parse(it)
            "${localizedDateLabel(date.toLocalDate(), strings.lang)}, ${date.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))}"
        }.getOrDefault(it)
    }
    if (lastDrive != null) {
        Text(strings.lastGdriveBackupLabel(lastDrive), fontSize = 11.sp, color = Muted)
    }
    settings["gdrive_backup_error"]?.takeIf { it.isNotBlank() }?.let {
        Text(strings.autoGdriveBackupErrorLabel(it), fontSize = 11.sp, color = Red)
    }

    // Security Card
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Glyph("shield", Modifier.size(21.dp), Purple); Spacer(Modifier.width(10.dp)); Text(strings.security, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        SettingsRow("lock", strings.appLock, strings.appLockSub, { dialog = if (settings["app_lock"] == "true") "disablePin" else "pin" }, trailing = { Switch(settings["app_lock"] == "true", { dialog = if (it) "pin" else "disablePin" }, modifier = Modifier.height(28.dp)) })
    }

    // Data Ownership Card
    AppCard(color = Lavender) {
        Text(strings.yourDataYourOwnership, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Purple)
        Spacer(Modifier.height(8.dp))
        Text(strings.dataOwnershipDesc, fontSize = 12.sp, color = Muted)
        Spacer(Modifier.height(8.dp))
        Text(strings.appVersionNote, fontSize = 10.sp, color = Purple)
    }

    if (dialog == "language") {
        val currentCode = strings.language.code
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(strings.selectLanguageTitle) },
            text = {
                Column {
                    LocalizationManager.getSupportedLanguages().forEach { lang ->
                        TextButton(
                            onClick = {
                                vm.setting("language", lang.code)
                                LocalizationManager.setSystemAppLanguage(context, lang.code)
                                dialog = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${lang.displayName} (${lang.nativeName})",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (lang.code == currentCode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (lang.code == currentCode) Purple else Ink
                                )
                                if (lang.code == currentCode) {
                                    Glyph("check", Modifier.size(18.dp), Purple)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ dialog = "" }) { Text(strings.close) } }
        )
    }

    if (dialog == "currency") {
        val currentCode = currency.code
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(strings.selectCurrencyTitle) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    LocalizationManager.getSupportedCurrencies().forEach { c ->
                        TextButton(
                            onClick = {
                                vm.setting("currency", c.code)
                                dialog = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${c.code} (${c.symbol}) - ${c.name}",
                                    modifier = Modifier.weight(1f),
                                    fontWeight = if (c.code == currentCode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (c.code == currentCode) Purple else Ink
                                )
                                if (c.code == currentCode) {
                                    Glyph("check", Modifier.size(18.dp), Purple)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ dialog = "" }) { Text(strings.close) } }
        )
    }

    if (dialog in listOf("password", "pin", "disablePin", "restore")) {
        var password by remember(dialog) { mutableStateOf("") }
        var confirmation by remember(dialog) { mutableStateOf("") }
        var error by remember(dialog) { mutableStateOf("") }
        val pin = dialog in listOf("pin", "disablePin")
        val confirmRequired = dialog in listOf("password", "pin")
        FormDialog(
            when(dialog) { "password" -> strings.backupPassword; "pin" -> strings.enableAppLock; "disablePin" -> strings.disableAppLock; else -> strings.restoreBackup },
            { dialog = "" },
            (if (pin) password.matches(Regex("[0-9]{6}")) else password.length >= 8) && (!confirmRequired || password == confirmation),
            vm.busy,
            {
                when (dialog) {
                    "password" -> vm.action(strings.backupPasswordSaved, onSuccess = { dialog = "" }) { BackupService.rememberPassword(context, password) }
                    "pin" -> vm.action(strings.appLockEnabled, onSuccess = { dialog = "" }) { PinSecurity.set(vm.store, password) }
                    "disablePin" -> vm.action(strings.appLockDisabled, onSuccess = { dialog = "" }) { require(PinSecurity.verify(vm.store, password)) { strings.pinIncorrect }; vm.store.setting("app_lock", "false") }
                    "restore" -> vm.action(strings.restoreSuccess, onSuccess = { dialog = "" }) { BackupService.restore(vm.store, BackupService.readFile(context, Uri.parse(restoreUri)), password) }
                }
            }
        ) {
            if (dialog == "restore") Text(strings.restoreWarning, fontSize = 12.sp, color = Red)
            Field(if (pin) strings.pin6Digits else strings.backupPassword, password, { if (!pin || it.length <= 6) password = it }, numeric = pin, password = true)
            if (confirmRequired) Field(if (pin) strings.repeatPin else strings.repeatPassword, confirmation, { if (!pin || it.length <= 6) confirmation = it }, numeric = pin, password = true)
            if (dialog == "password") Text(strings.newPasswordNote, fontSize = 11.sp, color = Muted)
            if (error.isNotEmpty()) Text(error, color = Red, fontSize = 12.sp)
        }
    }

    if (dialog == "schedule" || dialog == "retention") {
        val schedule = dialog == "schedule"
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(if (schedule) strings.backupSchedule else strings.backupRetention) },
            text = {
                Column {
                    (if (schedule) listOf(1, 6, 12, 24) else listOf(3, 7, 14, 30)).forEach { value ->
                        TextButton({
                            vm.action(strings.settingsSaved, onSuccess = { dialog = "" }) {
                                vm.store.setting(if (schedule) "backup_hours" else "backup_retention", value.toString())
                                if (schedule) scheduleMaintenance(context, value.toLong())
                            }
                        }, Modifier.fillMaxWidth()) {
                            Text(if (schedule) strings.everyHours(value.toString()) else strings.lastVersionsText(value))
                        }
                    }
                }
            },
            confirmButton = { TextButton({ dialog = "" }) { Text(strings.close) } }
        )
    }

    if (dialog == "gdrive_restore") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(strings.selectDriveBackupTitle) },
            text = {
                if (fetchingGdrive) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Purple)
                        Spacer(Modifier.width(12.dp))
                        Text(strings.fetchingDriveBackups)
                    }
                } else if (gdriveBackups.isEmpty()) {
                    Text(strings.noDriveBackups, color = Muted)
                } else {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        gdriveBackups.forEach { backup ->
                            TextButton(
                                onClick = {
                                    selectedDriveBackup = backup
                                    dialog = "restore_gdrive_password"
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                                    Text(backup.name, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 13.sp)
                                    val sizeText = if (backup.size > 0) "${backup.size / 1024} KB" else ""
                                    Text("${backup.modifiedTime.take(16).replace('T', ' ')}  $sizeText", fontSize = 11.sp, color = Muted)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton({ dialog = "" }) { Text(strings.cancel) } }
        )
    }

    if (dialog == "restore_gdrive_password") {
        var password by remember { mutableStateOf("") }
        FormDialog(
            strings.restoreBackup,
            { dialog = "" },
            password.length >= 8,
            vm.busy,
            {
                val backup = selectedDriveBackup ?: return@FormDialog
                scope.launch {
                    vm.action(strings.restoreSuccess, onSuccess = { dialog = "" }) {
                        BackupService.restoreFromGoogleDrive(context, vm.store, backup.id, password)
                    }
                }
            }
        ) {
            Text(strings.restoreWarning, fontSize = 12.sp, color = Red)
            Spacer(Modifier.height(8.dp))
            Text(selectedDriveBackup?.name.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Field(strings.backupPassword, password, { password = it }, password = true)
        }
    }

    if (dialog == "confirm_disconnect_gdrive") {
        AlertDialog(
            onDismissRequest = { dialog = "" },
            title = { Text(strings.disconnectGoogleDrive) },
            text = { Text(strings.confirmDisconnectGdrive) },
            confirmButton = {
                TextButton({
                    (context as? Activity)?.let { act ->
                        scope.launch {
                            GoogleDriveService.signOut(act)
                            vm.setting("gdrive_backup_enabled", "false")
                            dialog = ""
                        }
                    }
                }) { Text(strings.delete, color = Red) }
            },
            dismissButton = { TextButton({ dialog = "" }) { Text(strings.cancel) } }
        )
    }
}

@Composable
fun LockScreen(vm: FinanceViewModel, onUnlock: () -> Unit, onBiometric: () -> Unit) {
    val strings = LocalStrings.current
    var pin by rememberSaveable { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lock-attempts", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    Surface(color = Page, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            IconTile("wallet", Purple, 78)
            Spacer(Modifier.height(22.dp)); Text(strings.welcomeBack, fontSize = 24.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text(strings.unlockFinanceSpace, fontSize = 13.sp, color = Muted)
            Spacer(Modifier.height(32.dp)); Field(strings.enterAppPin, pin, { if (it.length <= 6) pin = it }, strings.sixDigitsPlaceholder, numeric = true, password = true)
            if (error.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(error, color = Red, fontSize = 12.sp) }
            Spacer(Modifier.height(20.dp)); PrimaryButton(if (checking) strings.checking else strings.unlockCostly, "lock", pin.length == 6 && !checking) {
                val blockedUntil = prefs.getLong("until", 0)
                if (System.currentTimeMillis() < blockedUntil) error = strings.tryAgainIn((blockedUntil - System.currentTimeMillis()) / 1000 + 1)
                else {
                    checking = true
                    scope.launch {
                        val success = withContext(Dispatchers.IO) { PinSecurity.verify(vm.store, pin) }
                        checking = false
                        if (success) { prefs.edit().clear().apply(); onUnlock() }
                        else {
                            val count = prefs.getInt("failures", 0) + 1
                            prefs.edit().putInt("failures", if (count >= 5) 0 else count).putLong("until", if (count >= 5) System.currentTimeMillis() + 30_000 else 0L).apply()
                            error = if (count >= 5) strings.tooManyAttempts else strings.pinWrongTryAgain
                            pin = ""
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp)); TextButton(onBiometric) { Glyph("shield", Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(strings.useBiometric) }
        }
    }
}
