# ZCode 手机客户端（原生 Android WebView 壳）

解决手机浏览器访问 ZCode Web 端不够沉浸的问题：无地址栏遮挡、真全屏、扫码/粘贴链接连接、历史记录一键重连。

## 构建

```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
./gradlew assembleDebug
```

APK 产物：`app/build/outputs/apk/debug/app-debug.apk`（`dist/` 里有已构建的版本）。

## 当前状态与已知问题

见 `docs/DEBUGGING-黑屏.md`（精准问题描述与调试指南）。
