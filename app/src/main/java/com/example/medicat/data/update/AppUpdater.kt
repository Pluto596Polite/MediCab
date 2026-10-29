package com.example.medicat.data.update

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.medicat.data.lookup.getJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** A published MediCab release with an APK attached. */
data class AppRelease(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

/** The outcome of a manual "Check for updates". */
sealed interface UpdateCheck {
    data class Available(val release: AppRelease) : UpdateCheck
    data class UpToDate(val version: String) : UpdateCheck
    data object Unreachable : UpdateCheck
    /** Debug builds have their own app ID, so a release APK wouldn't update them. */
    data object DebugBuild : UpdateCheck
}

/**
 * Keeps MediCab up to date from its GitHub releases. Only the request for the
 * latest release goes to GitHub; the APK is downloaded to the app's cache and
 * handed to Android's installer, which asks the user to confirm and refuses
 * any APK not signed with the same key as the installed app.
 */
@Singleton
class AppUpdater @Inject constructor(@ApplicationContext private val context: Context) {

    val installedVersion: String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()

    val isDebugBuild: Boolean = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private val _offer = MutableStateFlow<AppRelease?>(null)
    /** A newer release waiting for the user to accept or put off. */
    val offer: StateFlow<AppRelease?> = _offer.asStateFlow()

    suspend fun check(): UpdateCheck {
        if (isDebugBuild) return UpdateCheck.DebugBuild
        val latest = withContext(Dispatchers.IO) {
            updatesDir().deleteRecursively() // leftovers from an earlier update
            getJson(LATEST_RELEASE)
        }?.let { ReleaseParsers.latest(it, Build.SUPPORTED_ABIS.toList()) } ?: return UpdateCheck.Unreachable
        return if (ReleaseParsers.isNewer(latest.version, installedVersion)) {
            _offer.value = latest
            UpdateCheck.Available(latest)
        } else {
            UpdateCheck.UpToDate(installedVersion)
        }
    }

    fun dismissOffer() {
        _offer.value = null
    }

    /** Downloads the release's APK, reporting progress from 0 to 1. */
    suspend fun download(release: AppRelease, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(updatesDir().apply { mkdirs() }, "MediCab-${release.version}.apk")
        val connection = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", "MediCab/$installedVersion (Android)")
        }
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            target
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    /** Whether the user has let MediCab install apps (Settings › Install unknown apps). */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun allowInstallsIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))

    fun installIntent(apk: File): Intent =
        Intent(Intent.ACTION_VIEW)
            .setDataAndType(FileProvider.getUriForFile(context, "${context.packageName}.updates", apk), APK_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun updatesDir() = File(context.cacheDir, "updates")

    private companion object {
        const val LATEST_RELEASE = "https://api.github.com/repos/Pluto596Polite/MediCab/releases/latest"
        const val APK_TYPE = "application/vnd.android.package-archive"
    }
}

/** Release parsing, kept free of Android so it can be unit tested. */
object ReleaseParsers {

    /**
     * GitHub's "latest release" response; null for drafts, pre-releases or releases
     * without an APK. Of the APKs, the one built for the phone's preferred processor
     * type ([abis], best first) wins, then the universal one.
     */
    fun latest(json: JSONObject, abis: List<String>): AppRelease? {
        if (json.optBoolean("draft") || json.optBoolean("prerelease")) return null
        val tag = json.optString("tag_name").takeIf { it.isNotBlank() } ?: return null
        val assets = json.optJSONArray("assets") ?: return null
        val apks = (0 until assets.length()).mapNotNull(assets::optJSONObject)
            .filter { it.optString("name").endsWith(".apk", ignoreCase = true) }
        val apk = abis.firstNotNullOfOrNull { abi -> apks.firstOrNull { it.optString("name").contains("-$abi") } }
            ?: apks.firstOrNull { "universal" in it.optString("name") }
            ?: apks.firstOrNull()
            ?: return null
        val url = apk.optString("browser_download_url")
        // Only ever download from GitHub itself.
        if (!url.startsWith("https://github.com/")) return null
        return AppRelease(tag.removePrefix("v"), json.optString("body").trim(), url, apk.optLong("size"))
    }

    /** "1.10.0" is newer than "1.9.2"; suffixes such as "-beta" are ignored. */
    fun isNewer(candidate: String, installed: String): Boolean {
        fun parts(version: String) = version.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(installed)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
