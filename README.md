# ZCode 手机客户端（原生 Android WebView 壳）

解决手机浏览器访问 ZCode Web 端不够沉浸的问题：无地址栏遮挡、真全屏、扫码/粘贴链接连接、历史记录一键重连。

## 构建

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew assembleDebug
```

APK 产物：`app/build/outputs/apk/debug/app-debug.apk`（`dist/` 里有已构建的版本）。

## 当前状态与已知问题

黑屏问题已结案（v2.9，2026-09-27）：根因是 **WebView 不能作为 Compose `AndroidView`
挂载**——那样 Blink 的 CSS 视口高度恒为 0，按视口高度布局的页面整体塌陷成"黑屏但无障碍
可读"。完整证据链、实测排除清单与复现工装见 `docs/DEBUGGING-黑屏.md`。

**架构约束（改动 UI 前必读）**：WebView 必须挂在 `WebViewLayer.container`（窗口内容层里
`ComposeView` 之下的一层经典 `FrameLayout`），不得放进 Compose 树；会话页时 Compose 根层
必须保持透明，否则会盖住 WebView。
