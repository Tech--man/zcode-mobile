package dev.xray.zcode

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import dev.xray.zcode.data.ServerStore
import dev.xray.zcode.data.ThemeMode
import dev.xray.zcode.scan.ScanScreen
import dev.xray.zcode.ui.ZcTheme
import dev.xray.zcode.ui.zcPalette
import dev.xray.zcode.web.WebScreen
import kotlinx.coroutines.delay

sealed class Screen {
    data object Home : Screen()
    data object Scan : Screen()
    data object Logs : Screen()
    data class Session(val url: String) : Screen()
}

class MainActivity : ComponentActivity() {

    private lateinit var store: ServerStore
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            filePathCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
            filePathCallback = null
        }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(ThemeCtx.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ServerStore(this)
        dev.xray.zcode.web.WebLog.log(
            "app",
            "start build=${dev.xray.zcode.web.buildVersion()}" +
                " model=${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}" +
                " android=${android.os.Build.VERSION.RELEASE} webview=${dev.xray.zcode.web.webViewVersion()}",
        )
        // 系统栏由主题管理（真机实测：edge-to-edge 与否都不影响 CSS 视口，黑屏根因见 WebViewLayer 注释）
        setContent {
            ZcTheme {
                AppRoot(
                    store = store,
                    webWarning = webViewWarning(),
                    onThemeCycle = {
                        val next = store.cycleTheme()
                        val label = when (next) {
                            ThemeMode.SYSTEM -> "跟随系统"
                            ThemeMode.LIGHT -> "浅色"
                            ThemeMode.DARK -> "深色"
                        }
                        Toast.makeText(this, "主题：$label", Toast.LENGTH_SHORT).show()
                        // 重建前清掉 WebView 池，避免持有旧 Activity 上下文的实例跨重建复用
                        dev.xray.zcode.web.WebPool.destroy()
                        recreate()
                    },
                    launchFilePicker = { callback ->
                        filePathCallback = callback
                        filePicker.launch("*/*")
                    },
                )
            }
        }
        // WebView 宿主层必须插在 ComposeView **之下**、作为窗口内容层的经典 View：
        // Compose AndroidView interop 会让 Blink 的 CSS 视口高度恒为 0（详见 WebViewLayer 注释）。
        val webLayer = android.widget.FrameLayout(this)
        dev.xray.zcode.web.WebViewLayer.container = webLayer
        findViewById<android.view.ViewGroup>(android.R.id.content)
            .addView(webLayer, 0, android.view.ViewGroup.LayoutParams(-1, -1))
    }

    override fun onDestroy() {
        // 任何销毁（含 recreate）都清池：WebView 持有旧 Activity 上下文时复用会整页黑屏
        dev.xray.zcode.web.WebPool.destroy()
        super.onDestroy()
    }

    /** 系统 WebView 大版本过旧时（旧内核跑不动现代 SPA，会白/黑屏）返回提示文案。 */
    private fun webViewWarning(): String? = try {
        val major = WebView.getCurrentWebViewPackage()
            ?.versionName?.substringBefore('.')?.toIntOrNull()
        if (major != null && major < 100) {
            "系统 WebView 版本过旧（v$major），ZCode 页面可能无法渲染。请到应用商店更新「Android System WebView」或「Chrome」。"
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }
}

@Composable
private fun AppRoot(
    store: ServerStore,
    webWarning: String?,
    onThemeCycle: () -> Unit,
    launchFilePicker: (ValueCallback<Array<Uri>>) -> Unit,
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    // 会话页时根层必须透明：WebView 在 ComposeView 之下，opaque 背景会把它整个盖掉
    val transparentRoot = screen is Screen.Session

    Box(
        Modifier
            .fillMaxSize()
            .background(if (transparentRoot) Color.Transparent else zcPalette().bg),
    ) {
        when (val s = screen) {
            is Screen.Home -> HomeScreen(
                store = store,
                webViewWarning = webWarning,
                onConnect = { url ->
                    store.recordConnection(url)
                    dev.xray.zcode.web.WebLog.log("app", "connect $url")
                    screen = Screen.Session(url)
                },
                onScan = { screen = Screen.Scan },
                onThemeCycle = onThemeCycle,
                onOpenLogs = { screen = Screen.Logs },
            )

            is Screen.Logs -> dev.xray.zcode.web.LogScreen(onBack = { screen = Screen.Home })

            is Screen.Scan -> ScanScreen(
                onResult = { url ->
                    if (url == null) {
                        screen = Screen.Home
                    } else {
                        store.recordConnection(url)
                        screen = Screen.Session(url)
                    }
                },
                onBack = { screen = Screen.Home },
            )

            is Screen.Session -> WebScreen(
                url = s.url,
                store = store,
                launchFilePicker = launchFilePicker,
                onExit = { screen = Screen.Home },
                onOpenLogs = { screen = Screen.Logs },
                onRescan = { screen = Screen.Scan },
            )
        }

        // 启动视觉：复刻 Web 端启动 logo（黑渐变方壳 + 白色标识）
        StartupOverlay()
    }
}

@Composable
private fun StartupOverlay() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(750)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(280)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(zcPalette().bg),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(96.dp)
                        .alpha(0.98f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                listOf(
                                    androidx.compose.ui.graphics.Color(0xFF000000),
                                    androidx.compose.ui.graphics.Color(0xFF151718),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_fg),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                    )
                }
            }
        }
    }
}
