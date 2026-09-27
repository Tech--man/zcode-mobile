package dev.xray.zcode.web

import android.content.Context
import android.util.Log
import android.webkit.WebView
import dev.xray.zcode.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 本次运行的构建标识：所有关键日志行都带它，避免"日志与安装包对不上"的版本漂移。 */
fun buildVersion(): String = "${BuildConfig.VERSION_NAME}(${BuildConfig.VERSION_CODE})"

fun webViewVersion(): String = try {
    WebView.getCurrentWebViewPackage()?.versionName ?: "未知"
} catch (_: Exception) {
    "未知"
}

/** 会话诊断日志：内存环形缓冲 + 应用私有文件（filesDir/zcode_web.log）双写，logcat tag=ZCodeWeb。 */
object WebLog {
    private const val MAX = 2000
    private val buf = ArrayDeque<String>()
    private var file: File? = null
    private val ts = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    fun init(context: Context) {
        if (file == null) file = File(context.filesDir, "zcode_web.log")
    }

    @Synchronized
    fun log(tag: String, msg: String) {
        val line = "${ts.format(Date())} [$tag] $msg"
        buf.addLast(line)
        while (buf.size > MAX) buf.removeFirst()
        Log.d("ZCodeWeb", line)
        try {
            file?.appendText("$line\n")
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun snapshot(): List<String> {
        // 进程重启后内存为空：从文件回灌，保证日志跨进程可查
        if (buf.isEmpty()) {
            try {
                file?.takeIf { it.exists() }?.readLines()?.takeLast(MAX)?.forEach { buf.addLast(it) }
            } catch (_: Exception) {
            }
        }
        return buf.toList()
    }

    @Synchronized
    fun clear() {
        buf.clear()
        try {
            file?.delete()
        } catch (_: Exception) {
        }
    }
}
