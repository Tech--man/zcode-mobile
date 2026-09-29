package dev.xray.zcode.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dev.xray.zcode.web.WebLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * APK 下载与安装。不用 DownloadManager：私有仓库走令牌下载时 GitHub 会 302 到 S3 签名 URL，
 * 系统下载器会把 Authorization 头透传给 S3 而被拒。手写重定向循环，按域名决定是否带令牌。
 */
object UpdateManager {

    private const val MAX_REDIRECTS = 5

    fun apkFile(context: Context, version: String): File =
        File(File(context.filesDir, "update"), "ZCode-$version.apk")

    /**
     * 下载到应用私有目录（filesDir/update），完成时返回文件。
     * onProgress 在 IO 线程回调（0..1），直接写 Compose 状态是安全的。
     */
    suspend fun download(
        context: Context,
        info: UpdateInfo,
        token: String?,
        onProgress: (Float) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dest = apkFile(context, info.version)
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, dest.name + ".part")
        part.delete()

        var url = URL(info.apkUrl)
        var redirects = 0
        try {
            part.outputStream().use { sink ->
                while (true) {
                    coroutineContext.ensureActive()
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 30_000
                        instanceFollowRedirects = false
                        setRequestProperty("User-Agent", "ZCode-android")
                        // 仅对 GitHub 域带令牌；S3 签名 URL 必须匿名
                        if (!token.isNullOrBlank() &&
                            (url.host == "github.com" || url.host == "api.github.com" || url.host.endsWith(".github.com"))
                        ) {
                            setRequestProperty("Authorization", "Bearer $token")
                        }
                    }
                    val code = conn.responseCode
                    if (code in 300..399) {
                        val loc = conn.getHeaderField("Location") ?: throw IOException("重定向缺少 Location")
                        if (++redirects > MAX_REDIRECTS) throw IOException("重定向过多")
                        url = URL(url, loc)
                        conn.disconnect()
                        continue
                    }
                    if (code != 200) throw IOException("下载返回 $code")

                    val total = conn.contentLengthLong
                    conn.inputStream.use { input ->
                        val buf = ByteArray(64 * 1024)
                        var got = 0L
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            sink.write(buf, 0, n)
                            got += n
                            if (total > 0) onProgress((got.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                    conn.disconnect()
                    break
                }
            }
            if (!part.renameTo(dest)) throw IOException("落盘失败")
            dest
        } catch (e: Exception) {
            part.delete()
            throw e
        }
    }

    /** 本包是否已被用户允许安装未知来源应用（minSdk 26 起必有此检查）。 */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** 「允许安装未知应用」的系统设置页，只针对本包。 */
    fun unknownSourceSettings(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    /** 拉起系统安装器（FileProvider 授权只读）。返回 false 表示启动失败（如被厂商层拦截）。 */
    fun install(context: Context, file: File): Boolean {
        val uri: Uri = try {
            FileProvider.getUriForFile(context, "${context.packageName}.update", file)
        } catch (e: Exception) {
            WebLog.log("update", "fileprovider failed: ${e.message}")
            return false
        }
        return try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        } catch (e: Exception) {
            WebLog.log("update", "install failed: ${e.message}")
            false
        }
    }
}
