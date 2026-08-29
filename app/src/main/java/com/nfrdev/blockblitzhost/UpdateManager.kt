package com.nfrdev.blockblitzhost

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URL
import java.security.MessageDigest

class UpdateManager(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private const val VERSION_JSON_URL =
            "https://raw.githubusercontent.com/nfrdev/BlockBlitzHost/feature/blockblitz/version.json"
        private const val TIMEOUT_MS = 5_000
    }

    /** Returns UpdateInfo if an update is available, null otherwise. */
    suspend fun checkUpdate(jsonUrl: String = VERSION_JSON_URL): UpdateInfo? =
        withContext(Dispatchers.IO) {
            repeat(3) { attempt ->
                try {
                    val connection = URL(jsonUrl).openConnection().apply {
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                    }
                    val content = connection.getInputStream().bufferedReader().readText()
                    val updateInfo = json.decodeFromString<UpdateInfo>(content)

                    val currentVersionCode = currentVersionCode()
                    if (updateInfo.versionCode > currentVersionCode) {
                        return@withContext updateInfo
                    } else {
                        return@withContext null
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if (attempt == 2) return@withContext null
                    kotlinx.coroutines.delay(1_000L * (attempt + 1))
                }
            }
            null
        }

    fun currentVersionCode(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }
    }

    /**
     * Downloads the APK. Calls [onProgress] with 0..100, [onSuccess] when done,
     * and [onError] on failure. Optionally verifies [expectedSha256] after download.
     */
    fun downloadAndInstall(
        apkUrl: String,
        versionName: String,
        expectedSha256: String? = null,
        onProgress: ((Int) -> Unit)? = null,
        onSuccess: (() -> Unit)? = null,
        onError: (() -> Unit)? = null
    ) {
        val fileName = "blockblitzhost-$versionName.apk"
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Updating BlockBlitzHost")
            .setDescription("Downloading version $versionName")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        // Poll progress on a background thread
        if (onProgress != null) {
            Thread {
                var downloading = true
                while (downloading) {
                    val q = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(q)
                    if (cursor != null && cursor.moveToFirst()) {
                        val bytesDownloaded = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        )
                        val bytesTotal = cursor.getLong(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        )
                        val status = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                        )
                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                            onProgress(progress)
                        }
                        if (status == DownloadManager.STATUS_SUCCESSFUL ||
                            status == DownloadManager.STATUS_FAILED
                        ) {
                            downloading = false
                        }
                    }
                    cursor?.close()
                    if (downloading) Thread.sleep(500)
                }
            }.start()
        }

        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (id == downloadId) {
                    ctx.unregisterReceiver(this)
                    val uri = downloadManager.getUriForDownloadedFile(downloadId)
                    if (uri == null) {
                        onError?.invoke()
                        return
                    }

                    // Optional SHA-256 verification
                    if (expectedSha256 != null) {
                        val filePath = Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_DOWNLOADS
                        ).absolutePath + "/$fileName"
                        val file = File(filePath)
                        if (file.exists()) {
                            val digest = MessageDigest.getInstance("SHA-256")
                            val hash = file.inputStream().use { stream ->
                                val buffer = ByteArray(8192)
                                var bytesRead: Int
                                while (stream.read(buffer).also { bytesRead = it } != -1) {
                                    digest.update(buffer, 0, bytesRead)
                                }
                                digest.digest().joinToString("") { "%02x".format(it) }
                            }
                            if (!hash.equals(expectedSha256, ignoreCase = true)) {
                                onError?.invoke()
                                return
                            }
                        }
                    }

                    onSuccess?.invoke()
                    val installIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    ctx.startActivity(installIntent)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                onComplete,
                IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                Context.RECEIVER_NOT_EXPORTED
            )
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }
    }
}
