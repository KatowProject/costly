package com.katowproject.costly

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class FinanceViewModel(application: Application, val store: FinanceStore) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, FinanceStore(application))
    var data by mutableStateOf(FinanceData()); private set
    var ready by mutableStateOf(false); private set
    var busy by mutableStateOf(false); private set
    var message by mutableStateOf<String?>(null)
    init {
        action(onSuccess = { ready = true }) {
            store.processRecurring()
            scheduleMaintenance(application, store.read().settings["backup_hours"]?.toLongOrNull() ?: 6)
        }
    }
    fun action(success: String? = null, onSuccess: () -> Unit = {}, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                data = withContext(Dispatchers.IO) { store.read() }
                if (success != null) message = success
                onSuccess()
            } catch (e: Exception) { message = e.message?.take(160) ?: "Proses gagal. Silakan coba lagi." }
            finally { busy = false }
        }
    }
    fun setting(key: String, value: String) = action { store.setting(key, value) }
    override fun onCleared() { store.close(); super.onCleared() }
}

object PinSecurity {
    fun hash(pin: String, salt: ByteArray): ByteArray = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(pin.toCharArray(), salt, 120_000, 256)).encoded
    fun set(store: FinanceStore, pin: String) {
        require(pin.matches(Regex("[0-9]{6}"))) { "Gunakan PIN 6 digit" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        store.setting("pin_salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        store.setting("pin_hash", Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
        store.setting("app_lock", "true")
    }
    fun verify(store: FinanceStore, pin: String): Boolean {
        val settings = store.read().settings
        val salt = Base64.decode(settings["pin_salt"] ?: return false, Base64.NO_WRAP)
        return MessageDigest.isEqual(hash(pin, salt), Base64.decode(settings["pin_hash"], Base64.NO_WRAP))
    }
}

object BackupService {
    private const val HEADER = "COSTLY01"
    private fun derive(password: String, salt: ByteArray): SecretKey = SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password.toCharArray(), salt, 120_000, 256)).encoded, "AES")
    fun encrypt(plain: ByteArray, password: String): ByteArray {
        require(password.length >= 8) { "Kata sandi backup minimal 8 karakter" }
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(password, salt))
        return HEADER.toByteArray() + salt + cipher.iv + cipher.doFinal(plain)
    }
    fun decrypt(bytes: ByteArray, password: String): ByteArray {
        require(bytes.size > 52 && bytes.copyOfRange(0, 8).contentEquals(HEADER.toByteArray())) { "Format backup tidak valid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(password, bytes.copyOfRange(8, 24)), GCMParameterSpec(128, bytes.copyOfRange(24, 36)))
        return try { cipher.doFinal(bytes.copyOfRange(36, bytes.size)) } catch (_: Exception) { throw IllegalArgumentException("Kata sandi salah atau backup rusak") }
    }
    fun create(store: FinanceStore, password: String): ByteArray {
        val root = store.exportJson()
        val files = linkedMapOf<String, java.io.File>()
        val entries = root.getJSONArray("transactions")
        for (i in 0 until entries.length()) {
            val entry = entries.getJSONObject(i)
            val path = entry.optString("attachment")
            if (path.isNotBlank()) {
                val file = java.io.File(path)
                if (file.isFile && file.canonicalFile.parentFile == store.attachmentsDirectory.canonicalFile) {
                    val name = "attachments/${file.name}"
                    files[name] = file
                    entry.put("attachment", name)
                } else entry.put("attachment", "")
            }
        }
        require(files.values.sumOf { it.length() } <= 100L * 1024 * 1024) { "Total lampiran backup melebihi 100 MB" }
        val stream = ByteArrayOutputStream()
        ZipOutputStream(stream).use { zip ->
            zip.putNextEntry(ZipEntry("costly.json"))
            zip.write(root.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            files.forEach { (name, file) ->
                zip.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
        }
        return encrypt(stream.toByteArray(), password)
    }
    fun restore(store: FinanceStore, bytes: ByteArray, password: String) {
        val plain = decrypt(bytes, password)
        val created = mutableListOf<java.io.File>()
        try {
            ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
                require(zip.nextEntry?.name == "costly.json") { "Isi backup tidak valid" }
                val root = JSONObject(zip.readBytesLimited(20 * 1024 * 1024).toString(Charsets.UTF_8))
                val files = mutableMapOf<String, String>()
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(entry.name.matches(Regex("attachments/[A-Za-z0-9._-]+")) && !entry.isDirectory) { "Lampiran backup tidak valid" }
                    require(!files.containsKey(entry.name)) { "Lampiran duplikat" }
                    val contents = zip.readBytesLimited(10 * 1024 * 1024)
                    total += contents.size
                    require(total <= 100L * 1024 * 1024) { "Total lampiran backup terlalu besar" }
                    store.attachmentsDirectory.mkdirs()
                    val ext = entry.name.substringAfterLast('.', "bin").take(10)
                    val target = java.io.File(store.attachmentsDirectory, "${java.util.UUID.randomUUID()}.$ext")
                    created.add(target); target.writeBytes(contents); files[entry.name] = target.absolutePath
                }
                val entries = root.getJSONArray("transactions")
                for (i in 0 until entries.length()) {
                    val row = entries.getJSONObject(i)
                    val path = row.optString("attachment")
                    row.put("attachment", if (path.isBlank()) "" else files[path] ?: error("Lampiran tidak ditemukan dalam backup"))
                }
                store.restoreJson(root)
            }
        } catch (e: Exception) {
            created.forEach { it.delete() }
            throw e
        }
    }
    private fun java.io.InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val size = read(buffer); if (size < 0) break; require(out.size() + size <= limit) { "Backup terlalu besar" }; out.write(buffer, 0, size) }
        return out.toByteArray()
    }
    fun readFile(context: Context, uri: Uri): ByteArray = context.contentResolver.openInputStream(uri)?.use { it.readBytesLimited(125 * 1024 * 1024) } ?: error("File tidak dapat dibaca")
    private fun deviceKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (keyStore.getKey("costly-backup-password", null) as? SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("costly-backup-password", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build()); generateKey()
        }
    }
    fun rememberPassword(context: Context, password: String) {
        require(password.length >= 8) { "Kata sandi minimal 8 karakter" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, deviceKey()) }
        val encoded = Base64.encodeToString(cipher.iv + cipher.doFinal(password.toByteArray()), Base64.NO_WRAP)
        context.getSharedPreferences("backup-secret", Context.MODE_PRIVATE).edit().putString("secret", encoded).apply()
    }
    fun password(context: Context): String? {
        val encoded = context.getSharedPreferences("backup-secret", Context.MODE_PRIVATE).getString("secret", null) ?: return null
        return runCatching {
            val bytes = Base64.decode(encoded, Base64.NO_WRAP)
            Cipher.getInstance("AES/GCM/NoPadding").run { init(Cipher.DECRYPT_MODE, deviceKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))); doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8) }
        }.getOrNull()
    }
    fun automatic(context: Context, store: FinanceStore) {
        val settings = store.read().settings
        if (settings["backup_enabled"] != "true" && settings["gdrive_backup_enabled"] != "true") return
        val password = password(context) ?: error("Atur kata sandi backup terlebih dahulu")
        val bytes = create(store, password)
        val retention = settings["backup_retention"]?.toIntOrNull()?.coerceIn(1, 30) ?: 7
        val timestamp = System.currentTimeMillis()
        val fileName = "Costly-$timestamp.costly"

        if (settings["backup_enabled"] == "true") {
            runCatching {
                val folder = settings["backup_folder"]?.let(Uri::parse) ?: error("Pilih folder backup terlebih dahulu")
                val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
                val uri = DocumentsContract.createDocument(context.contentResolver, parent, "application/octet-stream", fileName) ?: error("Folder backup tidak dapat ditulis")
                context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Backup tidak dapat ditulis")
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
                val files = mutableListOf<Pair<String, String>>()
                context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                    while (c.moveToNext()) { val name = c.getString(1); if (name.matches(Regex("Costly-[0-9]+\\.costly"))) files.add(c.getString(0) to name) }
                }
                files.sortedByDescending { it.second }.drop(retention).forEach { (id, _) -> DocumentsContract.deleteDocument(context.contentResolver, DocumentsContract.buildDocumentUriUsingTree(folder, id)) }
                store.setting("last_backup", java.time.LocalDateTime.now().toString())
                store.setting("backup_error", "")
            }.onFailure {
                store.setting("backup_error", it.message?.take(160) ?: "Backup lokal gagal")
            }
        }

        if (settings["gdrive_backup_enabled"] == "true" && GoogleDriveService.isConnected(context)) {
            runCatching {
                kotlinx.coroutines.runBlocking {
                    GoogleDriveService.uploadBackup(context, fileName, bytes)
                    GoogleDriveService.pruneOldBackups(context, retention)
                }
                store.setting("last_gdrive_backup", java.time.LocalDateTime.now().toString())
                store.setting("gdrive_backup_error", "")
            }.onFailure {
                store.setting("gdrive_backup_error", it.message?.take(160) ?: "Backup Google Drive gagal")
            }
        }
    }
    suspend fun backupToGoogleDrive(context: Context, store: FinanceStore): String {
        val password = password(context) ?: error("Atur kata sandi backup terlebih dahulu")
        val bytes = create(store, password)
        val retention = store.read().settings["backup_retention"]?.toIntOrNull()?.coerceIn(1, 30) ?: 7
        val fileName = "Costly-${System.currentTimeMillis()}.costly"
        GoogleDriveService.uploadBackup(context, fileName, bytes)
        GoogleDriveService.pruneOldBackups(context, retention)
        store.setting("last_gdrive_backup", java.time.LocalDateTime.now().toString())
        store.setting("gdrive_backup_error", "")
        return fileName
    }
    suspend fun restoreFromGoogleDrive(context: Context, store: FinanceStore, fileId: String, password: String) {
        val bytes = GoogleDriveService.downloadBackup(context, fileId)
        restore(store, bytes, password)
    }
}

class FinanceWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result = FinanceStore(applicationContext).use { store ->
        try { store.processRecurring(); BackupService.automatic(applicationContext, store); Result.success() }
        catch (e: Exception) { store.setting("backup_error", e.message?.take(160) ?: "Backup otomatis gagal"); Result.retry() }
    }
}
fun scheduleMaintenance(context: Context, hours: Long = 6) {
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("costly-maintenance", ExistingPeriodicWorkPolicy.UPDATE,
        PeriodicWorkRequestBuilder<FinanceWorker>(hours.coerceAtLeast(1), TimeUnit.HOURS).build())
}
