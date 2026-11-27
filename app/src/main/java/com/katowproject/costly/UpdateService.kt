package com.katowproject.costly

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

data class AppReleaseInfo(
    val tagName: String,
    val cleanVersion: String,
    val title: String,
    val notes: String,
    val htmlUrl: String,
    val apkDownloadUrl: String?
)

sealed class UpdateCheckOutcome {
    data class UpdateAvailable(val release: AppReleaseInfo) : UpdateCheckOutcome()
    data object UpToDate : UpdateCheckOutcome()
    data object NoReleasesYet : UpdateCheckOutcome()
}

object UpdateService {
    private const val GITHUB_REPO = "KatowProject/costly"
    private const val GITHUB_LATEST_RELEASE_URL =
        "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    private const val GITHUB_WEB_LATEST_URL =
        "https://github.com/$GITHUB_REPO/releases/latest"
    private const val PREF_NAME = "costly_update_prefs"
    private const val KEY_LAST_CHECK_AT = "last_check_at"
    private const val KEY_REMIND_AFTER_AT = "remind_after_at"
    private const val KEY_SKIPPED_VERSION = "skipped_version"
    private const val KEY_CACHED_TAG = "cached_tag"
    private const val KEY_CACHED_VERSION = "cached_version"
    private const val KEY_CACHED_TITLE = "cached_title"
    private const val KEY_CACHED_NOTES = "cached_notes"
    private const val KEY_CACHED_HTML_URL = "cached_html_url"
    private const val KEY_CACHED_APK_URL = "cached_apk_url"
    private const val AUTO_CHECK_INTERVAL_MS = 12L * 60L * 60L * 1000L
    private const val DAY_MS = 24L * 60L * 60L * 1000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun currentVersionName(context: Context): String = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            ).versionName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }
    }.getOrNull()?.trim()?.ifBlank { null } ?: "1.0.0"

    fun parseVersionParts(version: String): List<Int> {
        val core = version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .trim()
        if (core.isEmpty()) return listOf(0)
        return core.split(".").map { it.toIntOrNull() ?: 0 }
    }

    fun isNewerVersion(remote: String, current: String): Boolean {
        val remoteParts = parseVersionParts(remote)
        val currentParts = parseVersionParts(current)
        val maxLen = maxOf(remoteParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val r = remoteParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun parseReleaseJson(jsonText: String): AppReleaseInfo {
        val json = JSONObject(jsonText)
        val tagName = json.getString("tag_name").trim()
        val cleanVersion = tagName.removePrefix("v").removePrefix("V").trim()
        val title = json.optString("name").trim().ifBlank { tagName }
        val notes = json.optString("body").trim()
        val htmlUrl = json.optString("html_url").trim()
        var apkUrl: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val asset = assets.optJSONObject(i) ?: continue
                val assetName = asset.optString("name")
                if (assetName.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    break
                }
            }
        }
        return AppReleaseInfo(
            tagName = tagName,
            cleanVersion = cleanVersion,
            title = title,
            notes = notes,
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl
        )
    }

    fun extractTagFromLocation(location: String): String? {
        val marker = "/releases/tag/"
        val idx = location.indexOf(marker)
        if (idx < 0) return null
        return location.substring(idx + marker.length)
            .substringBefore('?')
            .substringBefore('#')
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    fun extractApkUrlFromHtml(html: String): String? {
        val match = Regex("""href="(/[^"]+/releases/download/[^"]+\.apk)"""", RegexOption.IGNORE_CASE).find(html)
            ?: return null
        return "https://github.com${match.groupValues[1]}"
    }

    suspend fun checkLatestRelease(currentVersion: String): Result<UpdateCheckOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchViaApiOrFallback() ?: return@runCatching UpdateCheckOutcome.NoReleasesYet
            if (isNewerVersion(release.cleanVersion, currentVersion)) {
                UpdateCheckOutcome.UpdateAvailable(release)
            } else {
                UpdateCheckOutcome.UpToDate
            }
        }
    }

    private fun fetchViaApiOrFallback(): AppReleaseInfo? {
        val url = URL(GITHUB_LATEST_RELEASE_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Costly-Android")
        }
        try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code in 200..299) {
                val body = conn.inputStream.use { readAll(it) }
                return parseReleaseJson(body)
            }
            if (code == 403 || code == 429) {
                return fetchViaWebRedirect()
            }
            error("HTTP $code")
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchViaWebRedirect(): AppReleaseInfo? {
        val url = URL(GITHUB_WEB_LATEST_URL)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = false
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Costly-Android")
        }
        val location = try {
            val code = conn.responseCode
            if (code == 404) return null
            if (code in 300..399) {
                conn.getHeaderField("Location")
            } else {
                val finalUrl = conn.url.toString()
                if (finalUrl.contains("/releases/tag/")) finalUrl else null
            }
        } finally {
            conn.disconnect()
        } ?: return null

        val tagName = extractTagFromLocation(location) ?: return null
        val cleanVersion = tagName.removePrefix("v").removePrefix("V").trim()
        val htmlUrl = if (location.startsWith("http")) location else "https://github.com$location"
        val apkUrl = runCatching {
            val assetsUrl = URL("https://github.com/$GITHUB_REPO/releases/expanded_assets/$tagName")
            val assetConn = (assetsUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "Costly-Android")
            }
            try {
                if (assetConn.responseCode in 200..299) {
                    extractApkUrlFromHtml(assetConn.inputStream.use { readAll(it) })
                } else null
            } finally {
                assetConn.disconnect()
            }
        }.getOrNull()

        return AppReleaseInfo(
            tagName = tagName,
            cleanVersion = cleanVersion,
            title = tagName,
            notes = "",
            htmlUrl = htmlUrl,
            apkDownloadUrl = apkUrl
        )
    }

    fun shouldAutoCheck(context: Context, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val lastCheck = prefs(context).getLong(KEY_LAST_CHECK_AT, 0L)
        return nowMillis - lastCheck >= AUTO_CHECK_INTERVAL_MS
    }

    fun recordCheck(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(KEY_LAST_CHECK_AT, nowMillis).apply()
    }

    fun shouldShowAutoPrompt(context: Context, release: AppReleaseInfo, nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isVersionSkipped(context, release.cleanVersion)) return false
        val remindAfter = prefs(context).getLong(KEY_REMIND_AFTER_AT, 0L)
        return nowMillis >= remindAfter
    }

    fun snoozeForDays(context: Context, days: Int, nowMillis: Long = System.currentTimeMillis()) {
        prefs(context).edit()
            .putLong(KEY_REMIND_AFTER_AT, nowMillis + days.coerceAtLeast(1) * DAY_MS)
            .apply()
    }

    fun skipVersion(context: Context, cleanVersion: String) {
        prefs(context).edit().putString(KEY_SKIPPED_VERSION, cleanVersion).apply()
    }

    fun isVersionSkipped(context: Context, cleanVersion: String): Boolean {
        val skipped = prefs(context).getString(KEY_SKIPPED_VERSION, null) ?: return false
        return skipped.equals(cleanVersion, ignoreCase = true)
    }

    fun cacheAvailableUpdate(context: Context, info: AppReleaseInfo?) {
        val editor = prefs(context).edit()
        if (info == null) {
            editor.remove(KEY_CACHED_TAG)
                .remove(KEY_CACHED_VERSION)
                .remove(KEY_CACHED_TITLE)
                .remove(KEY_CACHED_NOTES)
                .remove(KEY_CACHED_HTML_URL)
                .remove(KEY_CACHED_APK_URL)
        } else {
            editor.putString(KEY_CACHED_TAG, info.tagName)
                .putString(KEY_CACHED_VERSION, info.cleanVersion)
                .putString(KEY_CACHED_TITLE, info.title)
                .putString(KEY_CACHED_NOTES, info.notes)
                .putString(KEY_CACHED_HTML_URL, info.htmlUrl)
                .putString(KEY_CACHED_APK_URL, info.apkDownloadUrl.orEmpty())
        }
        editor.apply()
    }

    fun getCachedUpdate(context: Context, currentVersion: String): AppReleaseInfo? {
        val p = prefs(context)
        val tag = p.getString(KEY_CACHED_TAG, null) ?: return null
        val version = p.getString(KEY_CACHED_VERSION, null) ?: return null
        if (!isNewerVersion(version, currentVersion)) {
            cacheAvailableUpdate(context, null)
            return null
        }
        return AppReleaseInfo(
            tagName = tag,
            cleanVersion = version,
            title = p.getString(KEY_CACHED_TITLE, tag) ?: tag,
            notes = p.getString(KEY_CACHED_NOTES, "") ?: "",
            htmlUrl = p.getString(KEY_CACHED_HTML_URL, "") ?: "",
            apkDownloadUrl = p.getString(KEY_CACHED_APK_URL, "")?.takeIf { it.isNotBlank() }
        )
    }

    fun openReleaseDownload(context: Context, info: AppReleaseInfo) {
        val targetUrl = info.apkDownloadUrl ?: info.htmlUrl
        if (targetUrl.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
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
