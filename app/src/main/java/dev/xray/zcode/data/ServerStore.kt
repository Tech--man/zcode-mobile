package dev.xray.zcode.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ServerEntry(val url: String, val lastConnected: Long)

/**
 * 服务器历史 + 偏好持久化（SharedPreferences + JSON，无 Room）。
 * Compose 可观察：servers / themeMode / desktopUa 均为 state。
 */
class ServerStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var servers by mutableStateOf(loadServers())
        private set
    var themeMode: ThemeMode
        by mutableStateOf(intToMode(prefs.getInt(KEY_THEME, 0)))
        private set
    var desktopUa by mutableStateOf(prefs.getBoolean(KEY_DESKTOP_UA, false))
        private set
    // 默认开启：小米等 OEM 系统层深色处理会把深色网页渲染成黑底黑字（肉眼不可见、
    // 无障碍可读）。页面按浅色渲染后即使被系统再加深也保持可读。菜单可关。
    // 注：z.ai 远程页自带深色主题（html.dark），此开关对其无效，仅影响其他站点。
    var webLightScheme by mutableStateOf(prefs.getBoolean(KEY_WEB_LIGHT, false))
        private set
    // 沉浸模式（隐藏系统栏）默认关闭：视觉更接近全屏，但系统栏仍可由用户唤回。
    var immersive by mutableStateOf(prefs.getBoolean(KEY_IMMERSIVE, false))
        private set

    fun recordConnection(url: String) {
        val list = servers.filter { it.url != url } + ServerEntry(url, System.currentTimeMillis())
        servers = list.sortedByDescending { it.lastConnected }.take(MAX_SERVERS)
        persist()
    }

    fun remove(url: String) {
        servers = servers.filter { it.url != url }
        persist()
    }

    fun applyThemeMode(mode: ThemeMode) {
        themeMode = mode
        prefs.edit().putInt(KEY_THEME, mode.ordinal).apply()
    }

    fun cycleTheme(): ThemeMode {
        val next = when (themeMode) {
            ThemeMode.SYSTEM -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.SYSTEM
        }
        applyThemeMode(next)
        return next
    }

    fun applyDesktopUa(enabled: Boolean) {
        desktopUa = enabled
        prefs.edit().putBoolean(KEY_DESKTOP_UA, enabled).apply()
    }

    /** 网页浅色兼容：WebView 独立用浅色 uiMode（页面渲染浅色主题），壳主题不变。 */
    fun applyWebLightScheme(enabled: Boolean) {
        webLightScheme = enabled
        prefs.edit().putBoolean(KEY_WEB_LIGHT, enabled).apply()
    }

    fun applyImmersive(enabled: Boolean) {
        immersive = enabled
        prefs.edit().putBoolean(KEY_IMMERSIVE, enabled).apply()
    }

    private fun persist() {
        val arr = JSONArray()
        servers.forEach {
            val o = org.json.JSONObject()
            o.put("url", it.url)
            o.put("t", it.lastConnected)
            arr.put(o)
        }
        prefs.edit().putString(KEY_SERVERS, arr.toString()).apply()
    }

    private fun loadServers(): List<ServerEntry> {
        val raw = prefs.getString(KEY_SERVERS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val seen = mutableSetOf<String>()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val url = o.optString("url")
                if (url.isNotEmpty() && seen.add(url)) ServerEntry(url, o.optLong("t", 0L)) else null
            }.sortedByDescending { it.lastConnected }
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val PREFS = "zcode_store"
        private const val KEY_SERVERS = "servers"
        private const val KEY_THEME = "themeMode"
        private const val KEY_DESKTOP_UA = "desktopUa"
        private const val KEY_WEB_LIGHT = "webLightScheme"
        private const val KEY_IMMERSIVE = "immersive"
        private const val MAX_SERVERS = 20

        /** attachBaseContext 阶段读取主题（store 尚未创建）。 */
        fun readThemeMode(base: Context): ThemeMode =
            intToMode(base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_THEME, 0))

        private fun intToMode(v: Int) = when (v) {
            1 -> ThemeMode.LIGHT
            2 -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }
}

fun formatRelative(ts: Long): String {
    if (ts <= 0) return "未连接过"
    val diff = System.currentTimeMillis() - ts
    val min = diff / 60_000
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "$min 分钟前"
        min < 60 * 24 -> "${min / 60} 小时前"
        min < 60 * 24 * 7 -> "${min / 60 / 24} 天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(ts))
    }
}
