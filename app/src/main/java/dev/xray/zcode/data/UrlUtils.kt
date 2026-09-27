package dev.xray.zcode.data

import android.net.Uri
import java.util.Locale

/**
 * URL 归一化与校验：仅接受 http/https。
 * 纯 host[:port][/path] 形式自动补 http:// 前缀；其余 scheme（javascript/data/file/intent/content 等）一律拒绝。
 * 局域网 / 私有地址放行——手机经局域网直连桌面端是本应用的核心场景。
 */
object UrlUtils {

    private val hostPortPath = Regex(
        "^[A-Za-z0-9._~%+-]+(?::\\d{1,5})?(?:/[\\w\\-./~%?&=+#]*)?$"
    )

    private val bannedPrefixes = listOf(
        "javascript:", "data:", "file:", "intent:", "content:", "about:", "blob:", "ws:", "wss:",
    )

    fun normalize(raw: String): String? {
        val s = raw.trim()
            .removePrefix("\uFEFF")
            .trim('\n', '\r', '\t', ' ')
        if (s.isEmpty()) return null
        val lower = s.lowercase(Locale.ROOT)
        return when {
            lower.startsWith("http://") || lower.startsWith("https://") -> validate(s)
            bannedPrefixes.any { lower.startsWith(it) } -> null
            hostPortPath.matches(s) && s.contains('.') -> validate("http://$s")
            else -> null
        }
    }

    private fun validate(u: String): String? = try {
        val uri = Uri.parse(u)
        val host = uri.host
        if (host.isNullOrBlank()) null else u
    } catch (_: Exception) {
        null
    }

    fun hostOf(u: String): String = try {
        Uri.parse(u).host ?: u
    } catch (_: Exception) {
        u
    }

    fun schemeOf(u: String): String = try {
        (Uri.parse(u).scheme ?: "http").lowercase(Locale.ROOT)
    } catch (_: Exception) {
        "http"
    }
}
