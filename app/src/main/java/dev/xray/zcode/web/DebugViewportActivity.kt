package dev.xray.zcode.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import dev.xray.zcode.ui.ZcTheme

/**
 * 视口隔离实验台（仅调试用，adb am start 驱动）。
 *
 * 目的：真机上 CSS「视口高度」相关量全部为 0（vh/dvh/svh/lvh、根元素 height:100%），
 * 而 innerHeight/clientHeight 正常，且与 useWideViewPort/loadWithOverviewMode 无关。
 * 这里在同一 APK 内逐个切换宿主层变量，定位归零发生在哪一层：
 *   mode=plain|compose   宿主是裸 WebView 还是 Compose AndroidView（含 WebScreen 的 padding 链）
 *   softinput=resize|pan|nothing|unspecified
 *   e2e=true|false       decorFitsSystemWindows
 *   ime=true|false       加载时是否唤起软键盘
 */
class DebugViewportActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 该入口 exported（adb 要能直接拉起）且可加载任意 URL：仅限可调试构建，发布版直接退出。
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            finish()
            return
        }
        val mode = intent.getStringExtra("mode") ?: "plain"
        val soft = intent.getStringExtra("softinput") ?: "resize"
        val e2e = intent.getBooleanExtra("e2e", true)
        val raiseIme = intent.getBooleanExtra("ime", false)
        val pad = intent.getStringExtra("pad") ?: "all"
        val nudge = intent.getBooleanExtra("nudge", false)
        val poke = intent.getStringExtra("poke") ?: "none"
        val url = intent.getStringExtra("url") ?: "http://127.0.0.1:8899/dvh-replica.html"

        window.setSoftInputMode(
            when (soft) {
                "pan" -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                "nothing" -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
                "unspecified" -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_UNSPECIFIED
                else -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            },
        )
        WindowCompat.setDecorFitsSystemWindows(window, !e2e)
        WebView.setWebContentsDebuggingEnabled(true)
        WebLog.log("dbgvp", "create mode=$mode softinput=$soft e2e=$e2e ime=$raiseIme pad=$pad nudge=$nudge poke=$poke url=$url")

        val make: (Context) -> WebView = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(AndroidColor.parseColor("#161616"))
                if (poke != "none") {
                    addOnAttachStateChangeListener(
                        object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) {
                                v.post {
                                    if (poke == "vis" || poke == "both") {
                                        v.visibility = android.view.View.INVISIBLE
                                        v.visibility = android.view.View.VISIBLE
                                    }
                                    if (poke == "resume" || poke == "both") {
                                        (v as WebView).onResume()
                                    }
                                    WebLog.log("dbgvp", "poke=$poke applied")
                                }
                            }

                            override fun onViewDetachedFromWindow(v: android.view.View) {}
                        },
                    )
                }
                if (nudge) {
                    addOnAttachStateChangeListener(
                        object : android.view.View.OnAttachStateChangeListener {
                            override fun onViewAttachedToWindow(v: android.view.View) {
                                v.post {
                                    WebLog.log("dbgvp", "nudge re-layout ${v.width}x${v.height}")
                                    v.layout(v.left, v.top, v.right - 1, v.bottom - 1)
                                    v.layout(v.left, v.top, v.right, v.bottom)
                                }
                            }

                            override fun onViewDetachedFromWindow(v: android.view.View) {}
                        },
                    )
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, u: String?) {
                        probeViewport(view, "dbg:finish")
                        view.postDelayed({ probeViewport(view, "dbg:+3s") }, 3000)
                    }
                }
                loadUrl(url)
            }
        }

        when {
            mode == "compose" -> setContent {
                ZcTheme {
                    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val chain: Modifier = when (pad) {
                        "none" -> Modifier.fillMaxSize()
                        "top" -> Modifier.fillMaxSize().padding(top = top + 46.dp)
                        "nav" -> Modifier.fillMaxSize().navigationBarsPadding()
                        "ime" -> Modifier.fillMaxSize().imePadding()
                        "nav+ime" -> Modifier.fillMaxSize().navigationBarsPadding().imePadding()
                        else -> Modifier
                            .fillMaxSize()
                            .padding(top = top + 46.dp)
                            .navigationBarsPadding()
                            .imePadding()
                    }
                    Box(Modifier.fillMaxSize().background(Color(0xFF171717))) {
                        AndroidView(factory = make, modifier = chain)
                    }
                }
            }

            mode == "linear" -> {
                // 非 Compose：LinearLayout 里放一个 weight=1 的 WebView（与 Compose fillMaxSize 等价的经典 View 布局）
                val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                root.addView(make(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                setContentView(root)
            }

            mode == "nobox" -> setContent {
                ZcTheme { AndroidView(factory = make, modifier = Modifier.fillMaxSize()) }
            }

            raiseIme -> {
                val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
                val input = EditText(this).apply { hint = "tap to raise IME" }
                root.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0f))
                root.addView(make(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                setContentView(root)
                input.post {
                    input.requestFocus()
                    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
                }
            }

            else -> setContentView(make(this))
        }
    }
}
