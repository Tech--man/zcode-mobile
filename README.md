# ZCode 手机客户端（Android WebView 壳）

原生 Android 壳承载 ZCode Web 端：无浏览器地址栏干扰、扫码/粘贴链接连接、历史一键重连，配合专注模式获得干净的屏幕利用率。

## 功能

- **连接**：扫码（CameraX + ML Kit）/ 粘贴链接 / 历史记录一键重连，多服务器本地保存
- **专注模式**：系统栏常驻，应用顶栏默认隐藏；网页内**下拉唤出**（半透明浮层）、**上滑立即隐没**；页面加载时自动浮现显示进度条
- **会话保持**：WebView 池化复用，跨页面导航不打断 ZCode 会话
- **菜单工具**：刷新 / 桌面 UA / 网页浅色兼容 / 视口探针 / 外部打开 / 复制链接 / 清除站点数据 / 调试日志
- **主题**：浅色 / 深色 / 跟随系统

## 构建

```bash
./gradlew assembleDebug
```

- 产物：`app/build/outputs/apk/debug/app-debug.apk`
- `dist/` 存放当前版本 APK 归档（不入库，旧版本随手清理）
- 环境：compileSdk/targetSdk 35，minSdk 26
- Release 签名：本地 `release.keystore` + `keystore.properties`（随机生成，不入库）；或推送 `v*` tag，由 GitHub Actions 用仓库 secrets 签名构建并发布 Release

## 架构约束（改 UI 前必读）

1. **WebView 不得放进 Compose 树**（包括 `AndroidView`）：该挂载方式下 Blink 的 CSS 视口
   高度恒为 0，页面整体塌陷成"黑屏但无障碍可读"（v2.9 已结案的根因）。WebView 必须挂在
   `WebViewLayer.container`——窗口内容层里 `ComposeView` 之下的一层经典 `FrameLayout`。
2. **会话页 Compose 根层必须保持透明**，否则会盖住其下的 WebView。
3. **`values-v35` 的 edge-to-edge opt-out 保留**：重新开启会触发 dvh=0 塌陷（黑屏复现）。

## 项目结构

```
app/src/main/java/dev/xray/zcode/
├── MainActivity.kt          # 页面导航 + WebView 宿主层挂载
├── HomeScreen.kt            # 服务器列表 / 连接
├── web/
│   ├── WebScreen.kt         # 会话页：顶栏 / 专注模式 / 菜单 + WebPool
│   ├── WebViewLayer.kt      # WebView 宿主（含黑屏约束文档注释）
│   ├── LogScreen.kt         # 调试日志页
│   ├── WebLog.kt / Diagnostics.kt / DebugViewportActivity.kt
├── scan/ScanScreen.kt       # 扫码
├── ui/                      # Theme / 组件
└── data/                    # ServerStore（偏好）/ UrlUtils
```

## 版本纪要

| 版本 | 要点 |
| --- | --- |
| v0.0.1 | 首个版本：连接管理 / 扫码 / 专注模式 / 主题 / 会话保持；release 本地签名 + tag 触发 GitHub Actions 发布 |

> 发布前的内部迭代曾按 v1.x–v2.10 编号（要点：v2.1 WebView 壳与 dvh 塌陷 shim → v2.9 黑屏根因修复 → v2.10 专注模式），该线已归档，版本自 0.0.1 重新起算。
