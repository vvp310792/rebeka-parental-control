package com.example.rebeka.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.rebeka.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Приложение ставится не из Google Play, поэтому магазин само не обновляет —
 * проверку и установку делаем сами (тот же принцип, что у F-Droid/Obtainium).
 *
 * CI (.github/workflows/build.yml) на каждый push в main публикует debug-APK
 * под фиксированным тегом релиза "latest-debug" с target_commitish = SHA
 * коммита. Здесь сравниваем этот SHA с BuildConfig.GIT_SHA (подставляется в
 * app/build.gradle.kts из `git rev-parse HEAD` на момент сборки) — если они
 * разные, значит вышла новая сборка.
 *
 * Скачать APK можно молча (обычный HTTP GET), а вот поставить — нет: без прав
 * Device Owner Android не даёт стороннему приложению установить APK без
 * подтверждения пользователем в системном диалоге. Это ограничение платформы,
 * не этого кода (см. README, тот же принцип, что и с остальными правами
 * в этом проекте — задержка/явное действие вместо тихого обхода системы).
 */
object UpdateManager {

    private const val REPO = "vvp310792/rebeka-parental-control"
    private const val RELEASE_TAG = "latest-debug"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/tags/$RELEASE_TAG"

    data class UpdateInfo(
        val sha: String,
        val releaseName: String,
        val downloadUrl: String
    )

    sealed class CheckResult {
        data class Available(val info: UpdateInfo) : CheckResult()
        data object UpToDate : CheckResult()
        data class Error(val message: String) : CheckResult()
    }

    /** Коммит, из которого собран текущий установленный APK. */
    fun currentSha(): String = BuildConfig.GIT_SHA

    suspend fun check(): CheckResult = withContext(Dispatchers.IO) {
        try {
            val current = currentSha()
            if (current == "unknown") {
                return@withContext CheckResult.Error("В этой сборке не зашит commit SHA")
            }

            val json = httpGetJson(API_URL)
            val sha = json.optString("target_commitish").takeIf { it.isNotBlank() }
                ?: return@withContext CheckResult.Error("Ответ GitHub без commit SHA")

            if (sha.equals(current, ignoreCase = true)) {
                return@withContext CheckResult.UpToDate
            }

            val assets = json.optJSONArray("assets")
            val apkUrl = (0 until (assets?.length() ?: 0))
                .asSequence()
                .map { assets!!.getJSONObject(it) }
                .firstOrNull { it.optString("name").endsWith(".apk") }
                ?.optString("browser_download_url")
                ?: return@withContext CheckResult.Error("В релизе нет APK-файла")

            CheckResult.Available(
                UpdateInfo(
                    sha = sha,
                    releaseName = json.optString("name").ifBlank { RELEASE_TAG },
                    downloadUrl = apkUrl
                )
            )
        } catch (e: Exception) {
            CheckResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun download(context: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        // Отдельный файл на каждый SHA — старые недокачанные/чужие версии не подсовываются.
        val file = File(dir, "update-${info.sha.take(8)}.apk")

        val connection = openConnection(info.downloadUrl)
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} при скачивании APK")
            }
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        file
    }

    /** Можно ли поставить APK от этого приложения без похода в настройки. */
    fun canRequestInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Экран «Разрешить установку из этого источника» — диалог тут есть, это ок. */
    fun requestInstallPermissionIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        )

    /** Запускает системный установщик пакетов на скачанном APK. */
    fun installIntent(context: Context, apkFile: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun httpGetJson(url: String): JSONObject {
        val connection = openConnection(url)
        // Заголовок обязательно до connect() — после него setRequestProperty падает.
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IOException("HTTP ${connection.responseCode} от GitHub API")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        return connection
    }
}
