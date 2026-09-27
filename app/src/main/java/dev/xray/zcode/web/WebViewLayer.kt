package dev.xray.zcode.web

import android.widget.FrameLayout

/**
 * WebView 宿主层：由 MainActivity 创建，位于 ComposeView **之下**的经典 View 容器。
 *
 * 为什么 WebView 不能放进 Compose 树（真机实测结论，Xiaomi 23116PN5BC / Android 16 /
 * WebView 151，模拟器 WebView 153 同现）：
 * WebView 一旦作为 Compose `AndroidView` 的 interop 子 View 挂载，Blink 的
 * **CSS 视口高度恒为 0** —— `100vh / 100dvh / 100svh / 100lvh / 100vw` 与根元素
 * `height:100%` 全部解析为 0，而 `window.innerHeight`、`documentElement.clientHeight`、
 * `visualViewport`、fixed 元素 `height:100%` 与 Android 侧视图尺寸全部正常。
 * 后果：任何按视口高度布局的页面（如 z.ai 远程页的 `h-dvh`）整体塌陷为 0 高，
 * 只剩 body 背景被绘制为画布底色 —— 表现为「黑屏，但无障碍/屏幕识别能读到完整内容」。
 *
 * 已逐项实测排除：useWideViewPort / loadWithOverviewMode（四种组合同样为 0）、
 * 系统栏 padding 链（去掉全部 padding 仍为 0）、edge-to-edge、attach 时机、
 * visibility 翻转、onResume、强制重排。经典 View 宿主（setContentView 或
 * LinearLayout+weight）同一页面同一设置下 vh 正常 = 914.28。
 */
object WebViewLayer {
    var container: FrameLayout? = null
}
