package dev.xray.zcode.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** 远端最新版本信息（GitHub Release 的最小字段集）。 */
data class UpdateInfo(
    val tag: String,     // 如 v0.0.3
    val version: String, // 如 0.0.3
    val notes: String,   // 更新说明（已做轻量 Markdown 清理）
    val apkUrl: String,
    val apkSize: Long,   // 字节；未知为 -1
)

sealed class UpdateResult {
    data class NewVersion(val info: UpdateInfo) : UpdateResult()
    data object UpToDate : UpdateResult()
    data class Failure(val message: String) : UpdateResult()
}

/**
 * GitHub Releases 版本检测：latest = 最新一个非 draft、非 prerelease 的 Release。
 * 与发布管线对齐——release.yml 按 `v*` tag 构建并把 `ZCode-<tag>-release.apk` 挂到 Release。
 * 手搓 HttpURLConnection 而非引网络库：只有一个 GET，项目保持零第三方网络依赖。
 */
object UpdateChecker {

    private const val LATEST_URL =
        "https://api.github.com/repos/Tech--man/zcode-mobile/releases/latest"

    /** 降级探测端点：github.com 的 latest 恒 302 到 /releases/tag/<tag>，不走 API 配额。 */
    private const val LATEST_PAGE = "https://github.com/Tech--man/zcode-mobile/releases/latest"

    /** 与 release.yml 的产物命名保持一致：ZCode-<tag>-release.apk。 */
    private const val APK_URL_FMT =
        "https://github.com/Tech--man/zcode-mobile/releases/download/%s/ZCode-%s-release.apk"

    /**
     * token 非空时以 Bearer 请求 API（私有仓库必需；公开仓库留空即可）。
     * 降级探测端点（github.com 302）只对公开仓库有效，私有仓库会 404。
     */
    suspend fun check(currentVersion: String, token: String? = null): UpdateResult {
        val viaApi = try {
            checkViaApi(currentVersion, token)
        } catch (e: Exception) {
            UpdateResult.Failure(e.message ?: "网络错误")
        }
        if (viaApi !is UpdateResult.Failure) return viaApi
        // 未认证 API 是 60 次/小时/IP，共享出口 IP 常被打满 → 用重定向探测兜底
        return try {
            checkViaRedirect(currentVersion)
        } catch (e: Exception) {
            UpdateResult.Failure(viaApi.message)
        }
    }

    private suspend fun checkViaApi(currentVersion: String, token: String?): UpdateResult =
        withContext(Dispatchers.IO) {
            val conn = (URL(LATEST_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                // GitHub API 无 User-Agent 直接 403；Accept 声明 JSON 防 302 到 HTML 页
                setRequestProperty("User-Agent", "ZCode-android/$currentVersion")
                setRequestProperty("Accept", "application/vnd.github+json")
                if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return@withContext UpdateResult.Failure("版本服务返回 $code")
            }
            val json = conn.inputStream.bufferedReader().use { JSONObject(it.readText()) }

            val tag = json.optString("tag_name")
            val version = tag.removePrefix("v").removePrefix("V")
            if (!isNewer(version, currentVersion)) return@withContext UpdateResult.UpToDate

            // 只认 .apk 附件，不猜 tag → URL 的拼接规则
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var apkSize = -1L
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.optString("browser_download_url")
                    apkSize = a.optLong("size", -1L)
                    break
                }
            }
            val url = apkUrl
                ?: return@withContext UpdateResult.Failure("最新 Release 未附 APK，请到项目主页下载")
            UpdateResult.NewVersion(UpdateInfo(tag, version, cleanNotes(json.optString("body")), url, apkSize))
        }

    /** 无 API 配额的兜底：302 Location 末段即 tag；拿不到更新说明与大小，其余等价。 */
    private suspend fun checkViaRedirect(currentVersion: String): UpdateResult =
        withContext(Dispatchers.IO) {
            val conn = (URL(LATEST_PAGE).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                requestMethod = "GET"
                setRequestProperty("User-Agent", "ZCode-android/$currentVersion")
            }
            val code = conn.responseCode
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code !in 300..399 || location.isNullOrBlank()) {
                return@withContext UpdateResult.Failure("版本服务暂不可用（$code）")
            }
            val tag = location.substringBefore('?').substringAfter("/releases/tag/").takeIf { it.isNotEmpty() }
                ?: return@withContext UpdateResult.Failure("版本服务返回异常")
            val version = tag.removePrefix("v").removePrefix("V")
            if (!isNewer(version, currentVersion)) return@withContext UpdateResult.UpToDate
            UpdateResult.NewVersion(
                UpdateInfo(tag, version, "", APK_URL_FMT.format(tag, tag), -1L),
            )
        }

    /** 点分数字版本逐段比较：远端 > 当前 才算有新版本（段数不齐按 0 补）。 */
    fun isNewer(remote: String, current: String): Boolean {
        val r = remote.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val c = current.split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, c.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** Release notes 是 Markdown，直接展示带语法噪音：去图片/链接/标题/强调符号，截断长文。 */
    private fun cleanNotes(raw: String): String {
        var s = raw
            .replace(Regex("!\\[[^\\]]*]\\([^)]*\\)"), "")
            .replace(Regex("\\[([^\\]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("[`*_]"), "")
            .lines()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString("\n")
        if (s.length > 600) s = s.take(600) + "…"
        return s
    }
}
