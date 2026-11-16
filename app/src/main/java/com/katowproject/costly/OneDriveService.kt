package com.katowproject.costly

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

data class OneDriveBackup(
    val id: String,
    val name: String,
    val modifiedTime: String,
    val size: Long
)

object OneDriveService {
    const val CLIENT_ID = "b0cb868c-2334-440b-abf6-df3efee3f885"
    const val REDIRECT_URI = "costly://onedrive-callback"
    private const val SCOPE = "Files.ReadWrite.AppFolder offline_access User.Read"
    private const val AUTH_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    private const val TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    private const val GRAPH_API_BASE = "https://graph.microsoft.com/v1.0"

    private const val PREF_NAME = "onedrive_auth_pref"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_PKCE_VERIFIER = "pkce_verifier"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isConnected(context: Context): Boolean {
        val p = prefs(context)
        return !p.getString(KEY_REFRESH_TOKEN, null).isNullOrBlank()
    }

    fun getConnectedEmail(context: Context): String? =
        prefs(context).getString(KEY_USER_EMAIL, null)

    fun getConnectedName(context: Context): String? =
        prefs(context).getString(KEY_USER_NAME, null)

    fun signOut(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun createSignInIntent(context: Context): Intent {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(verifier.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

        prefs(context).edit().putString(KEY_PKCE_VERIFIER, verifier).apply()

        val authUri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPE)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .build()

        return Intent(Intent.ACTION_VIEW, authUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun handleAuthCallback(context: Context, code: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val verifier = prefs(context).getString(KEY_PKCE_VERIFIER, null)
                ?: error("Sesi verifikasi OAuth tidak ditemukan")

            val params = listOf(
                "client_id" to CLIENT_ID,
                "grant_type" to "authorization_code",
                "code" to code,
                "redirect_uri" to REDIRECT_URI,
                "code_verifier" to verifier
            ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

            val response = postUrlEncoded(TOKEN_URL, params)
            val json = JSONObject(response)
            val accessToken = json.getString("access_token")
            val refreshToken = json.optString("refresh_token")
            val expiresIn = json.optLong("expires_in", 3600L)
            val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)

            prefs(context).edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .remove(KEY_PKCE_VERIFIER)
                .apply()

            val profile = fetchProfile(accessToken)
            val email = profile.optString("mail").ifBlank { profile.optString("userPrincipalName") }
            val name = profile.optString("displayName")
            prefs(context).edit()
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_NAME, name)
                .apply()

            email.ifBlank { name }.ifBlank { "Microsoft User" }
        }
    }

    suspend fun getValidAccessToken(context: Context): String = withContext(Dispatchers.IO) {
        val p = prefs(context)
        val currentToken = p.getString(KEY_ACCESS_TOKEN, null)
        val expiresAt = p.getLong(KEY_EXPIRES_AT, 0L)
        val refreshToken = p.getString(KEY_REFRESH_TOKEN, null)
            ?: error("Akun Microsoft belum terhubung")

        if (!currentToken.isNullOrBlank() && System.currentTimeMillis() < (expiresAt - 300_000L)) {
            return@withContext currentToken
        }

        val params = listOf(
            "client_id" to CLIENT_ID,
            "grant_type" to "refresh_token",
            "refresh_token" to refreshToken,
            "redirect_uri" to REDIRECT_URI
        ).joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" }

        val response = postUrlEncoded(TOKEN_URL, params)
        val json = JSONObject(response)
        val newAccessToken = json.getString("access_token")
        val newRefreshToken = json.optString("refresh_token", refreshToken)
        val expiresIn = json.optLong("expires_in", 3600L)

        p.edit()
            .putString(KEY_ACCESS_TOKEN, newAccessToken)
            .putString(KEY_REFRESH_TOKEN, newRefreshToken)
            .putLong(KEY_EXPIRES_AT, System.currentTimeMillis() + (expiresIn * 1000L))
            .apply()

        newAccessToken
    }

    private fun postUrlEncoded(urlString: String, formBody: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(formBody.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { readAll(it) } ?: ""
        if (code !in 200..299) {
            val errDesc = runCatching { JSONObject(text).optString("error_description") }.getOrNull()
            error(errDesc?.ifBlank { null } ?: "Permintaan token gagal (HTTP $code)")
        }
        return text
    }

    private fun fetchProfile(accessToken: String): JSONObject {
        val url = URL("$GRAPH_API_BASE/me")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val code = conn.responseCode
        if (code in 200..299) {
            val text = conn.inputStream.use { readAll(it) }
            return JSONObject(text)
        }
        return JSONObject()
    }

    suspend fun uploadBackup(context: Context, fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context)
        val encodedName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20")
        val url = URL("$GRAPH_API_BASE/me/drive/special/approot:/$encodedName:/content")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "PUT"
            doOutput = true
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { readAll(it) } ?: ""
        if (code !in 200..299) {
            error("Gagal mengunggah ke OneDrive (HTTP $code: $text)")
        }
        val json = JSONObject(text)
        json.optString("id", fileName)
    }

    suspend fun listBackups(context: Context): List<OneDriveBackup> = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context)
        val url = URL("$GRAPH_API_BASE/me/drive/special/approot/children?\$select=id,name,lastModifiedDateTime,size")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.use { readAll(it) } ?: ""
        if (code !in 200..299) {
            error("Gagal mengambil daftar backup OneDrive (HTTP $code)")
        }
        val json = JSONObject(text)
        val items = json.optJSONArray("value") ?: return@withContext emptyList()
        val list = mutableListOf<OneDriveBackup>()
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val name = item.optString("name")
            if (name.matches(Regex("Costly-[0-9]+\\.costly"))) {
                list.add(
                    OneDriveBackup(
                        id = item.optString("id"),
                        name = name,
                        modifiedTime = item.optString("lastModifiedDateTime"),
                        size = item.optLong("size", 0L)
                    )
                )
            }
        }
        list.sortedByDescending { it.name }
    }

    suspend fun downloadBackup(context: Context, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        val token = getValidAccessToken(context)
        val url = URL("$GRAPH_API_BASE/me/drive/items/$fileId/content")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Authorization", "Bearer $token")
        }
        val code = conn.responseCode
        if (code !in 200..299) {
            error("Gagal mengunduh file dari OneDrive (HTTP $code)")
        }
        conn.inputStream.use { it.readBytes() }
    }

    suspend fun pruneOldBackups(context: Context, retention: Int) = withContext(Dispatchers.IO) {
        runCatching {
            val backups = listBackups(context)
            if (backups.size > retention) {
                val token = getValidAccessToken(context)
                backups.drop(retention).forEach { old ->
                    runCatching {
                        val delUrl = URL("$GRAPH_API_BASE/me/drive/items/${old.id}")
                        val conn = (delUrl.openConnection() as HttpURLConnection).apply {
                            requestMethod = "DELETE"
                            connectTimeout = 15_000
                            readTimeout = 15_000
                            setRequestProperty("Authorization", "Bearer $token")
                        }
                        conn.responseCode
                    }
                }
            }
        }
    }

    private fun readAll(stream: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
        return out.toString(Charsets.UTF_8.name())
    }
}
