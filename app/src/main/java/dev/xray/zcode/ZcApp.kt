package dev.xray.zcode

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.camera.core.CameraXConfig
import androidx.camera.camera2.Camera2Config
import dev.xray.zcode.data.ServerStore
import dev.xray.zcode.data.ThemeMode

object ThemeCtx {
    /**
     * 按用户偏好覆盖 uiMode。Application 与 Activity 都要套：
     * Compose 主题读 Activity 配置，WebView 的 prefers-color-scheme 读应用级配置。
     */
    fun wrap(base: Context): Context {
        val mode = ServerStore.readThemeMode(base)
        if (mode == ThemeMode.SYSTEM) return base
        val want = if (mode == ThemeMode.DARK) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        return withUiMode(base, want)
    }

    /** 强制浅色上下文（网页浅色兼容模式专用：只给 WebView 用，壳不受影响）。 */
    fun lightWrap(base: Context): Context = withUiMode(base, Configuration.UI_MODE_NIGHT_NO)

    private fun withUiMode(base: Context, want: Int): Context {
        val current = base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        if (current == want) return base
        val config = Configuration(base.resources.configuration)
        config.uiMode = want or (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv())
        return base.createConfigurationContext(config)
    }
}

class ZcApp : Application(), CameraXConfig.Provider {
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(ThemeCtx.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        // 日志文件必须在 Application 层打开：调试 Activity 等非 Main 入口同样要落盘
        dev.xray.zcode.web.WebLog.init(this)
    }

    /** 显式提供 Camera2 实现，绕开反射式配置解析（否则间歇性 "not configured properly" 崩溃）。 */
    override fun getCameraXConfig(): CameraXConfig =
        CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig()).build()
}
