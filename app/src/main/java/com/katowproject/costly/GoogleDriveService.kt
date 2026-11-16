@file:Suppress("DEPRECATION")
package com.katowproject.costly

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class DriveBackup(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val size: Long
)

object GoogleDriveService {
    private const val SCOPE_FILE = "https://www.googleapis.com/auth/drive.file"
    private const val SCOPE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    private const val OAUTH_SCOPE_STRING = "oauth2:$SCOPE_FILE $SCOPE_APPDATA"

    fun getSignInClient(context: Context) = GoogleSignIn.getClient(
        context,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(SCOPE_FILE), Scope(SCOPE_APPDATA))
            .build()
    )

    fun getSignInIntent(context: Context): Intent =
        getSignInClient(context).signInIntent

    fun getSignedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)

    fun isConnected(context: Context): Boolean =
        getSignedInAccount(context) != null

    fun getConnectedEmail(context: Context): String? =
        getSignedInAccount(context)?.email

    suspend fun signOut(activity: Activity): Boolean = withContext(Dispatchers.IO) {
        try {
            getSignInClient(activity).signOut()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun getAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        val account = getSignedInAccount(context)?.account
            ?: error("Akun Google belum terhubung")
        GoogleAuthUtil.getToken(context, account, OAUTH_SCOPE_STRING)
    }

    suspend fun uploadBackup(context: Context, fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val token = getAccessToken(context)
        val boundary = "costly_boundary_${System.currentTimeMillis()}"
        val url = URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
        }

        val metadata = JSONObject().apply {
            put("name", fileName)
            put("description", "Costly encrypted backup archive")
            put("mimeType", "application/octet-stream")
        }.toString()

        val prefix = buildString {
            append("--$boundary\r\n")
            append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata)
            append("\r\n--$boundary\r\n")
            append("Content-Type: application/octet-stream\r\n\r\n")
        }.toByteArray(Charsets.UTF_8)

        val suffix = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

        conn.outputStream.use { out ->
            out.write(prefix)
            out.write(bytes)
            out.write(suffix)
            out.flush()
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            JSONObject(responseText).optString("id")
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            error("Gagal mengunggah ke Google Drive: $errText")
        }
    }

    suspend fun listBackups(context: Context): List<DriveBackup> = withContext(Dispatchers.IO) {
        val token = getAccessToken(context)
        val query = URLEncoder.encode("name contains 'Costly-' and trashed = false", "UTF-8")
        val fields = URLEncoder.encode("files(id,name,modifiedTime,size)", "UTF-8")
        val url = URL("https://www.googleapis.com/drive/v3/files?q=$query&fields=$fields&orderBy=createdTime+desc")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
        }

        val code = conn.responseCode
        if (code in 200..299) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val filesArray = json.optJSONArray("files") ?: return@withContext emptyList()
            val list = mutableListOf<DriveBackup>()
            for (i in 0 until filesArray.length()) {
                val item = filesArray.getJSONObject(i)
                val name = item.optString("name")
                if (name.startsWith("Costly-") && name.endsWith(".costly")) {
                    list.add(
                        DriveBackup(
                            id = item.optString("id"),
                            name = name,
                            modifiedTime = item.optString("modifiedTime"),
                            size = item.optLong("size", 0L)
                        )
                    )
                }
            }
            list
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            error("Gagal membaca Google Drive: $errText")
        }
    }

    suspend fun downloadBackup(context: Context, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val token = getAccessToken(context)
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId?alt=media")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }

        val code = conn.responseCode
        if (code in 200..299) {
            conn.inputStream.use { it.readBytesLimited(125 * 1024 * 1024) }
        } else {
            val errText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            error("Gagal mengunduh dari Google Drive: $errText")
        }
    }

    suspend fun deleteBackup(context: Context, fileId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getAccessToken(context)
        val url = URL("https://www.googleapis.com/drive/v3/files/$fileId")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "DELETE"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        conn.responseCode in 200..299
    }

    suspend fun pruneOldBackups(context: Context, retention: Int) {
        runCatching {
            val backups = listBackups(context)
            if (backups.size > retention) {
                val toDelete = backups.drop(retention)
                toDelete.forEach { deleteBackup(context, it.id) }
            }
        }
    }

    private fun InputStream.readBytesLimited(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = read(buffer)
            if (size < 0) break
            require(out.size() + size <= limit) { "Backup terlalu besar" }
            out.write(buffer, 0, size)
        }
        return out.toByteArray()
    }
}
