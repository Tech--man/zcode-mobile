package dev.xray.zcode.web

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.webkit.ConsoleMessage
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceResponse
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import dev.xray.zcode.R
import dev.xray.zcode.data.ServerStore
import dev.xray.zcode.data.UrlUtils
import dev.xray.zcode.ui.ZcButton
import dev.xray.zcode.ui.ZcIconButton
import dev.xray.zcode.ui.ZcMenu
import dev.xray.zcode.ui.ZcMenuItem
import dev.xray.zcode.ui.ZcText
import dev.xray.zcode.ui.zcPalette

private const val DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
private const val BAR_HEIGHT = 46

/** 跨页面导航复用 WebView 实例，保持 ZCode 会话不断。 */
object WebPool {
    var webView: WebView? = null
        private set
    var loadedUrl: String? = null
        private set

    @SuppressLint("SetJavaScriptEnabled")
    fun obtain(
        context: Context,
        url: String,
        desktopUa: Boolean,
        forceLightScheme: Boolean,
        fileChooser: (ValueCallback<Array<Uri>>) -> Unit,
        onError: (String?) -> Unit,
        onProgress: (Int) -> Unit,
        onHistoryChange: () -> Unit,
        onSoftHint: (String) -> Unit = {},
    ): WebView {
        val existing = webView
        if (existing != null && loadedUrl == url && existing.context == context) {
            applyUa(existing, desktopUa)
            return existing
        }
        existing?.let { destroy() }
        // debug 构建开放 chrome://inspect 远程调试，便于真机排查
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        // 网页浅色兼容：WebView 独立用浅色 uiMode，页面渲染浅色主题（壳主题不变）
        val wvContext = if (forceLightScheme) dev.xray.zcode.ThemeCtx.lightWrap(context) else context
        val wv = WebView(wvContext)
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            useWideViewPort = true
            loadWithOverviewMode = true
            textZoom = 100
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            mediaPlaybackRequiresUserGesture = false
            // 系统「强制暗色」会把浅色页面整页反成黑底（OEM 上常见），主题统一由壳的 uiMode 同步控制
            if (android.os.Build.VERSION.SDK_INT >= 29 && android.os.Build.VERSION.SDK_INT < 33) {
                @Suppress("DEPRECATION") forceDark = WebSettings.FORCE_DARK_OFF
            }
            // Android 13+ 老接口失效：必须用 compat API 关闭算法加深。
            // 小米等 OEM 的 WebView 可能默认开启——页面自带深色主题时被二次反转成"黑底黑字"
            // （无障碍可见、肉眼不可见）。显式声明不允许。
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                try {
                    androidx.webkit.WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, false)
                    WebLog.log("web", "algorithmic darkening off")
                } catch (t: Throwable) {
                    WebLog.log("web", "darkening set fail: $t")
                }
            }
        }
        applyUa(wv, desktopUa)
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        WebLog.log(
            "web",
            "obtain url=$url desktopUa=$desktopUa lightScheme=$forceLightScheme " +
                "build=${buildVersion()} webview=${webViewVersion()}",
        )
        WebLog.log("web", "ua=${wv.settings.userAgentString}")
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                return if (scheme == "http" || scheme == "https") {
                    false
                } else {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, request.url))
                    } catch (_: ActivityNotFoundException) {
                    }
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                WebLog.log("page", "start $url")
                onError(null)
                onProgress(5)
                probeViewport(view, "start")
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val u = request.url.toString()
                if (!u.startsWith("data:")) {
                    WebLog.log("req", "${request.method} ${u.take(180)}")
                }
                return null
            }

            override fun onPageFinished(view: WebView, url: String?) {
                onProgress(100)
                onHistoryChange()
                probeViewport(view, "finish")
                // 空/卡加载壳 + 失效链接诊断：finish 抓 body 长度与前 300 字符
                view.evaluateJavascript(
                    "(function(){var t=document.body?document.body.innerText:'';return t.length+'|'+t.slice(0,300);})()",
                ) { r ->
                    val s = r?.trim()?.removeSurrounding("\"") ?: ""
                    val idx = s.indexOf('|')
                    val len = if (idx >= 0) s.substring(0, idx) else s
                    val body = if (idx >= 0) s.substring(idx + 1) else ""
                    WebLog.log("page", "finish bodyLen=$len body=[$body] url=$url")
                    val stale = body.contains("Invalid connection") ||
                        body.contains("no longer valid") ||
                        body.contains("失效")
                    if (stale) {
                        onError("远程会话链接已失效：旧链接一次性有效，请在桌面端重新生成二维码并重新扫码")
                    }
                }
                // 卡加载壳检测：8 秒后 body 仍少于 80 字符 → 非阻塞提示（不覆盖页面）
                view.postDelayed({
                    if (WebPool.webView !== view) return@postDelayed
                    view.evaluateJavascript("(document.body?document.body.innerText.length:0)") { r2 ->
                        val len2 = r2?.trim()?.removeSurrounding("\"")?.toIntOrNull() ?: 0
                        if (len2 in 1..80) {
                            WebLog.log("page", "stuck-check bodyLen=$len2")
                            onSoftHint("页面长时间停在加载中（仅 $len2 字符），可能中继连接失败——可重试或重新扫码")
                        }
                    }
                }, 8000)
                // 配对完成/挂载后是黑屏的实际观测时刻，分别取样避免"跨时刻比较"的老问题
                view.postDelayed({
                    if (WebPool.webView !== view) return@postDelayed
                    probeViewport(view, "t20s")
                }, 20000)
                view.postDelayed({
                    if (WebPool.webView !== view) return@postDelayed
                    probeViewport(view, "t40s")
                }, 40000)
                // 中继配对超时：20 秒后仍停在「正在连接中转服务」→ 明确错误卡（该页面无可用交互，阻塞无副作用）
                view.postDelayed({
                    if (WebPool.webView !== view) return@postDelayed
                    view.evaluateJavascript("(document.body?document.body.innerText:'')") { r3 ->
                        val body3 = r3?.trim()?.removeSurrounding("\"") ?: ""
                        val stuck = body3.contains("正在连接中转服务") || body3.contains("connecting to relay", true)
                        WebLog.log("page", "relay-check stuck=$stuck len=${body3.length}")
                        if (stuck) {
                            // 探测 z.ai 同源可达性，区分「网络不通」与「凭证失效」
                            view.evaluateJavascript(
                                "(function(){return new Promise(function(res){var t0=Date.now();fetch(location.origin+'/favicon.ico',{cache:'no-store'}).then(function(r){res('reachable status='+r.status+' ms='+(Date.now()-t0))}).catch(function(e){res('unreachable ms='+(Date.now()-t0)+' '+e)})})})()",
                            ) { r4 ->
                                WebLog.log("net", "relay-origin probe: ${r4?.trim()?.removeSurrounding("\"")}")
                            }
                            onError("连接中转服务超时：桌面端的二维码/链接会定期刷新，旧链接无法配对——请在桌面端重新生成二维码并重新扫码；若新码仍卡住，可尝试菜单「清除站点数据」")
                        }
                    }
                }, 20000)
            }

            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                WebLog.log("nav", "$url")
                onHistoryChange()
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                WebLog.log(
                    "err",
                    "main=${request.isForMainFrame} code=${error.errorCode} ${error.description} ${request.url}",
                )
                if (request.isForMainFrame) onError(error.description?.toString() ?: "网络错误")
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse,
            ) {
                WebLog.log(
                    "http",
                    "main=${request.isForMainFrame} status=${errorResponse.statusCode} ${request.url}",
                )
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                WebLog.log("ssl", "primary=${error.primaryError} url=${error.url}")
                // 不静默放行：自签名/不受信证书让失败可见，而不是整页空白
                handler.cancel()
                onError("SSL 证书校验失败（自签名或不受信任的证书）")
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                WebLog.log("fatal", "renderer gone didCrash=${detail.didCrash()}")
                // 渲染进程崩溃时 WebView 会整页黑屏：销毁旧实例，重试时用当前上下文重建
                view.post { destroy() }
                onError("渲染进程崩溃，请点重试重建页面")
                return true
            }
        }
        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                if (newProgress == 0 || newProgress == 100 || newProgress % 20 == 0) {
                    WebLog.log("prog", "$newProgress")
                }
                onProgress(newProgress)
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                WebLog.log(
                    "js",
                    "${consoleMessage.messageLevel()} ${consoleMessage.message()}" +
                        " @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}",
                )
                return true
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams,
            ): Boolean {
                fileChooser(callback)
                return true
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?,
            ) {
                callback?.invoke(origin, false, false)
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                val window = (context as? android.app.Activity)?.window ?: return
                view.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                window.addContentView(
                    view,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
                customView = view
                customViewCallback = callback
            }

            override fun onHideCustomView() {
                val window = (context as? android.app.Activity)?.window
                customView?.let { v ->
                    (window?.decorView as? ViewGroup)?.removeView(v)
                }
                window?.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
            }
        }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            download(context, url, contentDisposition, mimetype)
        }
        wv.loadUrl(url)
        webView = wv
        loadedUrl = url
        return wv
    }

    var customView: View? = null
        private set
    var customViewCallback: WebChromeClient.CustomViewCallback? = null
        private set

    fun applyUa(wv: WebView, desktopUa: Boolean) {
        val target = if (desktopUa) DESKTOP_UA else null
        if (wv.settings.userAgentString != target) {
            wv.settings.userAgentString = target
        }
    }

    private fun download(context: Context, url: String, disposition: String?, mime: String?) {
        try {
            val name = URLUtil.guessFileName(url, disposition, mime)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
                setMimeType(mime)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
            }
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            dm.enqueue(request)
            Toast.makeText(context, "开始下载 $name", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: Exception) {
            }
        }
    }

    fun destroy() {
        customView = null
        customViewCallback = null
        webView?.stopLoading()
        webView?.destroy()
        webView = null
    }
}

@Composable
fun WebScreen(
    url: String,
    store: ServerStore,
    launchFilePicker: (ValueCallback<Array<Uri>>) -> Unit,
    onExit: () -> Unit,
    onOpenLogs: () -> Unit,
    onRescan: () -> Unit,
) {
    val p = zcPalette()
    val context = LocalContext.current
    val view = LocalView.current
    val clipboard = LocalClipboardManager.current
    val host = UrlUtils.hostOf(url)

    var immersive by remember { mutableStateOf(store.immersive) }
    var menuOpen by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var webRef by remember { mutableStateOf<WebView?>(null) }
    var desktopUa by remember(store.desktopUa) { mutableStateOf(store.desktopUa) }
    // 渲染进程崩溃等场景需要整体重建 WebView 实例：换 key 触发 factory 重跑
    var reloadKey by remember { mutableStateOf(0) }
    var softHint by remember { mutableStateOf<String?>(null) }

    val insetsController = remember {
        val window = (context as? android.app.Activity)?.window
        window?.let { WindowCompat.getInsetsController(it, view) }
    }

    // 沉浸模式：隐藏系统栏，滑动临时唤回
    LaunchedEffect(immersive) {
        val controller = insetsController ?: return@LaunchedEffect
        if (immersive) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler(enabled = canGoBack) {
        webRef?.goBack()
    }
    // 无网页历史可后退时，返回键回主页而不是退出应用
    BackHandler(enabled = !canGoBack) {
        onExit()
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val density = LocalDensity.current
    // WebView 在 ComposeView 之下，靠经典 View 的 margin 复刻原来的内缩链
    val topPx = if (immersive) 0 else with(density) { (statusBarTop + BAR_HEIGHT.dp).roundToPx() }
    val bottomPx = with(density) {
        (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
            WindowInsets.ime.asPaddingValues().calculateBottomPadding()).roundToPx()
    }

    // 本层必须透明：WebView 不在 Compose 树里，opaque 背景会把它整个盖住；
    // 页面加载期的底色由宿主层承担。
    SideEffect { WebViewLayer.container?.setBackgroundColor(p.bg.toArgb()) }

    Box(Modifier.fillMaxSize()) {
        DisposableEffect(reloadKey, url) {
            val layer = WebViewLayer.container
            if (layer == null) {
                WebLog.log("web", "WebViewLayer.container 未就绪，无法挂载 WebView")
                return@DisposableEffect onDispose {}
            }
            val wv = WebPool.obtain(
                context,
                url,
                desktopUa,
                store.webLightScheme,
                launchFilePicker,
                onError = { error = it },
                onProgress = { progress = it },
                onHistoryChange = { canGoBack = (webRef ?: WebPool.webView)?.canGoBack() == true },
                onSoftHint = { softHint = it },
            )
            webRef = wv
            (wv.parent as? ViewGroup)?.removeView(wv)
            layer.addView(
                wv,
                0,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ).apply {
                    topMargin = topPx
                    bottomMargin = bottomPx
                },
            )
            onDispose {
                (wv.parent as? ViewGroup)?.removeView(wv)
                webRef = null
            }
        }
        LaunchedEffect(topPx, bottomPx) {
            val lp = (webRef?.layoutParams as? FrameLayout.LayoutParams) ?: return@LaunchedEffect
            if (lp.topMargin != topPx || lp.bottomMargin != bottomPx) {
                lp.topMargin = topPx
                lp.bottomMargin = bottomPx
                webRef?.requestLayout()
            }
        }

        // 顶栏（沉浸时浮层，非沉浸时实体）
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (immersive) p.topBar else p.surface),
        ) {
            if (!immersive) Box(Modifier.statusBarsPadding())
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZcIconButton(icon = R.drawable.ic_back, contentDescription = "返回", onClick = onExit)
                ZcText(
                    host,
                    size = 14.sp,
                    mono = true,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp),
                )
                ZcIconButton(
                    icon = R.drawable.ic_refresh,
                    contentDescription = "刷新",
                    onClick = {
                        error = null
                        webRef?.reload()
                    },
                )
                Box {
                    ZcIconButton(
                        icon = R.drawable.ic_more,
                        contentDescription = "菜单",
                        onClick = { menuOpen = !menuOpen },
                    )
                    if (menuOpen) {
                        ZcMenu(
                            items = listOf(
                                ZcMenuItem("刷新"),
                                ZcMenuItem("沉浸模式(隐藏系统栏)", checked = immersive),
                                ZcMenuItem("桌面 UA", checked = desktopUa),
                                ZcMenuItem("网页浅色兼容", checked = store.webLightScheme),
                                ZcMenuItem("诊断:视口探针"),
                                ZcMenuItem("在浏览器打开"),
                                ZcMenuItem("复制链接"),
                                ZcMenuItem("清除站点数据并刷新"),
                                ZcMenuItem("调试日志"),
                            ),
                            onDismiss = { menuOpen = false },
                            onSelect = { index ->
                                menuOpen = false
                                when (index) {
                                    0 -> webRef?.reload()
                                    1 -> {
                                        immersive = !immersive
                                        store.applyImmersive(immersive)
                                        WebLog.log("web", "immersive=$immersive")
                                    }
                                    2 -> {
                                        desktopUa = !desktopUa
                                        store.applyDesktopUa(desktopUa)
                                        WebPool.webView?.let {
                                            WebPool.applyUa(it, desktopUa)
                                            it.reload()
                                        }
                                    }
                                    3 -> {
                                        // 网页浅色兼容：WebView 独立浅色上下文，需整体重建
                                        store.applyWebLightScheme(!store.webLightScheme)
                                        WebPool.destroy()
                                        webRef = null
                                        reloadKey++
                                        WebLog.log("web", "light compat toggled: ${store.webLightScheme}")
                                        Toast.makeText(
                                            context,
                                            if (store.webLightScheme) "网页浅色兼容：开" else "网页浅色兼容：关",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    4 -> {
                                        val wv = webRef ?: WebPool.webView
                                        if (wv == null) {
                                            Toast.makeText(context, "尚无 WebView 实例", Toast.LENGTH_SHORT).show()
                                        } else {
                                            probeViewport(wv, "manual", context)
                                        }
                                    }
                                    5 -> try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: ActivityNotFoundException) {
                                    }
                                    6 -> {
                                        clipboard.setText(AnnotatedString(url))
                                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                                    }
                                    7 -> {
                                        // 清 ServiceWorker/Cache/localStorage/Cookie，排除站点级坏状态
                                        WebPool.webView?.evaluateJavascript(
                                            "(function(){try{if(navigator.serviceWorker){navigator.serviceWorker.getRegistrations().then(function(rs){rs.forEach(function(r){r.unregister()})})}if(window.caches){caches.keys().then(function(ks){ks.forEach(function(k){caches.delete(k)})})}localStorage.clear();sessionStorage.clear()}catch(e){}return 'ok'})()",
                                            null,
                                        )
                                        try {
                                            android.webkit.WebStorage.getInstance().deleteAllData()
                                        } catch (_: Exception) {
                                        }
                                        WebPool.webView?.clearCache(true)
                                        CookieManager.getInstance().removeAllCookies(null)
                                        CookieManager.getInstance().flush()
                                        WebLog.log("web", "site data cleared, reloading")
                                        WebPool.webView?.reload()
                                        Toast.makeText(context, "已清除站点数据并刷新", Toast.LENGTH_SHORT).show()
                                    }
                                    8 -> onOpenLogs()
                                }
                            },
                        )
                    }
                }
            }
            if (!immersive) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(p.border),
                )
            }
            if (progress in 1..99) {
                Box(
                    Modifier
                        .fillMaxWidth(progress / 100f)
                        .height(2.dp)
                        .background(p.brand),
                )
            }
        }

        // 错误页
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(p.bg),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(28.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(p.surface)
                        .border(1.dp, p.border, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ZcText("无法连接服务器", size = 17.sp, weight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    error?.let {
                        ZcText(it, size = 13.sp, color = p.textMuted, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                    }
                    ZcText(url, size = 12.sp, color = p.textFaint, mono = true, maxLines = 2)
                    Spacer(Modifier.height(18.dp))
                    ZcButton("重试", onClick = {
                        error = null
                        // 池里的实例可能已死（渲染进程崩溃/上下文失效），整体重建
                        WebPool.destroy()
                        webRef = null
                        reloadKey++
                    })
                    Spacer(Modifier.height(8.dp))
                    ZcButton("重新扫码", primary = false, onClick = {
                        error = null
                        onRescan()
                    })
                    Spacer(Modifier.height(8.dp))
                    ZcButton("复制链接", primary = false, onClick = {
                        clipboard.setText(AnnotatedString(url))
                        Toast.makeText(context, "已复制链接", Toast.LENGTH_SHORT).show()
                    })
                    Spacer(Modifier.height(8.dp))
                    ZcButton("返回", primary = false, onClick = onExit)
                }
            }
        }

        // 非阻塞软提示（卡加载壳等），不覆盖页面内容
        androidx.compose.animation.AnimatedVisibility(
            visible = softHint != null && error == null,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(p.surfaceAlt)
                    .border(1.dp, p.border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ZcText(
                    softHint ?: "",
                    size = 12.sp,
                    color = p.textMuted,
                    modifier = Modifier.weight(1f),
                )
                ZcIconButton(
                    icon = R.drawable.ic_close,
                    contentDescription = "关闭提示",
                    onClick = { softHint = null },
                    size = 28.dp,
                )
            }
        }
    }
}
