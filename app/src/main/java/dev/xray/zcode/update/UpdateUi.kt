package dev.xray.zcode.update

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.xray.zcode.BuildConfig
import dev.xray.zcode.data.ServerStore
import dev.xray.zcode.ui.ZcButton
import dev.xray.zcode.ui.ZcField
import dev.xray.zcode.ui.ZcText
import dev.xray.zcode.ui.zcPalette
import dev.xray.zcode.web.WebLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

/** 下载/安装推进状态（检查结果不含在内——发现新版即可重开弹窗，无需记录）。 */
private enum class UpdatePhase { IDLE, DOWNLOADING, READY }

/**
 * 首页更新区块：版本行（点击手动检查 / 长按配置令牌）+ 新版本卡片 + 更新弹窗（下载/安装）。
 * 自动检查在进入首页时节流触发（12h 一次，见 ServerStore）；失败静默，不打扰。
 */
@Composable
fun UpdateSection(store: ServerStore) {
    val p = zcPalette()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var available by remember { mutableStateOf<UpdateInfo?>(null) }
    var dialogOpen by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(UpdatePhase.IDLE) }
    var progress by remember { mutableStateOf(-1f) } // 0..1；-1 = 总大小未知
    var readyFile by remember { mutableStateOf<File?>(null) }
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var tokenDialogOpen by remember { mutableStateOf(false) }

    // 「允许安装未知应用」开关在系统设置里，回来后自动续装（本地函数允许自引用，闭包持有自身）
    var retryInstall by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { retryInstall?.invoke() }

    fun doInstall(fromSettings: Boolean = false) {
        val file = readyFile ?: return
        if (UpdateManager.canInstall(context)) {
            if (UpdateManager.install(context, file)) {
                dialogOpen = false
                Toast.makeText(context, "已开始安装，请在提示中确认", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(
                    context,
                    "无法拉起安装器，可在应用私有目录 update/ 中手动安装",
                    Toast.LENGTH_LONG,
                ).show()
            }
        } else {
            // 从设置页返回仍未授权就不再自动重开（避免返回即循环跳设置），交给用户手动重试
            if (fromSettings) {
                Toast.makeText(context, "仍未允许「安装未知应用」，可重新点「安装更新」", Toast.LENGTH_LONG).show()
                return
            }
            retryInstall = { doInstall(fromSettings = true) }
            try {
                permLauncher.launch(UpdateManager.unknownSourceSettings(context))
            } catch (_: Exception) {
                Toast.makeText(context, "请在系统设置中允许本应用「安装未知应用」后重试", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun manualCheck() {
        if (checking) return
        checking = true
        scope.launch {
            val token = store.githubToken.ifBlank { null }
            when (val r = UpdateChecker.check(BuildConfig.VERSION_NAME, token)) {
                is UpdateResult.NewVersion -> {
                    WebLog.log("update", "new version ${r.info.tag}")
                    available = r.info
                    dialogOpen = true
                }
                UpdateResult.UpToDate -> {
                    WebLog.log("update", "up to date")
                    Toast.makeText(context, "已是最新版本 v${BuildConfig.VERSION_NAME}", Toast.LENGTH_SHORT).show()
                }
                is UpdateResult.Failure -> {
                    WebLog.log("update", "check: ${r.message}")
                    val hint = if (token == null && r.message.contains("404")) {
                        // 私有仓库的典型表现：匿名请求一律 404
                        "仓库不可见（私有？）— 长按「版本」可配置访问令牌"
                    } else {
                        r.message
                    }
                    Toast.makeText(context, "检查失败：$hint", Toast.LENGTH_LONG).show()
                }
            }
            checking = false
        }
    }

    fun startDownload() {
        val info = available ?: return
        if (downloadJob?.isActive == true) return
        phase = UpdatePhase.DOWNLOADING
        progress = -1f
        downloadJob = scope.launch {
            try {
                val token = store.githubToken.ifBlank { null }
                val f = UpdateManager.download(context, info, token) { pr -> progress = pr }
                readyFile = f
                phase = UpdatePhase.READY
                WebLog.log("update", "downloaded ${f.name} ${f.length()}B")
                doInstall()
            } catch (e: CancellationException) {
                phase = UpdatePhase.IDLE
                throw e
            } catch (e: Exception) {
                WebLog.log("update", "download: ${e.message}")
                phase = UpdatePhase.IDLE
                Toast.makeText(context, "下载失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 启动静默检查：节流由 store 控制，任何失败都不提示
    LaunchedEffect(Unit) {
        if (!store.shouldAutoCheckUpdate()) return@LaunchedEffect
        store.markUpdateChecked()
        when (val r = UpdateChecker.check(BuildConfig.VERSION_NAME, store.githubToken.ifBlank { null })) {
            is UpdateResult.NewVersion -> {
                WebLog.log("update", "new version ${r.info.tag}")
                available = r.info
            }
            UpdateResult.UpToDate -> WebLog.log("update", "up to date")
            is UpdateResult.Failure -> WebLog.log("update", "auto check: ${r.message}")
        }
    }

    Column(Modifier.fillMaxWidth()) {
        available?.let { info ->
            if (!dialogOpen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(p.brandSoft)
                        .border(1.dp, p.brand.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { dialogOpen = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        ZcText(
                            "发现新版本 v${info.version}",
                            size = 14.sp,
                            color = p.brand,
                            weight = FontWeight.Medium,
                        )
                        val sub = buildString {
                            append(fmtSize(info.apkSize))
                            info.notes.lineSequence().firstOrNull()?.let {
                                if (isNotEmpty()) append(" · ")
                                append(it)
                            }
                        }
                        if (sub.isNotBlank()) {
                            Spacer(Modifier.height(3.dp))
                            ZcText(sub, size = 12.sp, color = p.textMuted, maxLines = 1)
                        }
                    }
                    ZcText("查看", size = 13.sp, color = p.brand, weight = FontWeight.Medium)
                }
                Spacer(Modifier.height(14.dp))
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    // 点击 = 手动检查；长按 = 令牌配置入口（私有仓库才需要，不占常规 UI）
                    detectTapGestures(
                        onTap = { manualCheck() },
                        onLongPress = { tokenDialogOpen = true },
                    )
                }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZcText(
                "版本 v${BuildConfig.VERSION_NAME}" + if (store.githubToken.isNotBlank()) " ·已配置令牌" else "",
                size = 12.sp,
                color = p.textFaint,
                mono = true,
            )
            Spacer(Modifier.weight(1f))
            ZcText(
                if (checking) "检查中…" else "检查更新",
                size = 12.sp,
                color = p.brand,
                weight = FontWeight.Medium,
            )
        }
    }

    if (dialogOpen) {
        available?.let { info ->
            UpdateDialog(
                info = info,
                phase = phase,
                progress = progress,
                onDismiss = {
                    // 下载中关弹窗即取消任务（协程取消 → 连接中断 → part 文件清理）
                    if (phase == UpdatePhase.DOWNLOADING) downloadJob?.cancel()
                    dialogOpen = false
                },
                onPrimary = {
                    when (phase) {
                        UpdatePhase.IDLE -> startDownload()
                        UpdatePhase.DOWNLOADING -> Unit // 下载中主按钮禁用
                        UpdatePhase.READY -> doInstall()
                    }
                },
            )
        }
    }

    if (tokenDialogOpen) {
        TokenDialog(
            current = store.githubToken,
            onSave = { store.applyGithubToken(it); tokenDialogOpen = false },
            onDismiss = { tokenDialogOpen = false },
        )
    }
}

@Composable
private fun UpdateDialog(
    info: UpdateInfo,
    phase: UpdatePhase,
    progress: Float,
    onDismiss: () -> Unit,
    onPrimary: () -> Unit,
) {
    val p = zcPalette()
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.surface)
                    .border(1.dp, p.border, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // 拦截点击冒泡到遮罩
                    )
                    .padding(18.dp),
            ) {
                ZcText("发现新版本", size = 17.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                ZcText(
                    buildString {
                        append("v${BuildConfig.VERSION_NAME} → v${info.version}")
                        val size = fmtSize(info.apkSize)
                        if (size.isNotEmpty()) append(" · $size")
                    },
                    size = 13.sp,
                    color = p.textMuted,
                    mono = true,
                )
                if (info.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    ZcText("更新说明", size = 12.sp, color = p.textFaint, letterSpacing = 1.sp)
                    Spacer(Modifier.height(4.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(168.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.surfaceAlt)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        ZcText(info.notes, size = 12.sp, color = p.textMuted)
                    }
                }

                if (phase == UpdatePhase.DOWNLOADING) {
                    Spacer(Modifier.height(14.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(p.surfaceAlt),
                    ) {
                        if (progress >= 0f) {
                            Box(
                                Modifier
                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(p.brand),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    ZcText(
                        if (progress >= 0f) "下载中 ${(progress * 100).toInt()}%" else "下载中…",
                        size = 12.sp,
                        color = p.textMuted,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZcButton(
                        label = "稍后",
                        primary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ZcButton(
                        label = when (phase) {
                            UpdatePhase.IDLE -> "立即更新"
                            UpdatePhase.DOWNLOADING -> "下载中…"
                            UpdatePhase.READY -> "安装更新"
                        },
                        enabled = phase != UpdatePhase.DOWNLOADING,
                        onClick = onPrimary,
                        modifier = Modifier.weight(1.4f),
                    )
                }
            }
        }
    }
}

/** GitHub 令牌配置：私有仓库的检查与下载都需要。输入框留空保存即清除。 */
@Composable
private fun TokenDialog(
    current: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val p = zcPalette()
    var value by remember { mutableStateOf(current) }
    Popup(
        alignment = Alignment.TopStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(p.surface)
                    .border(1.dp, p.border, RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
                    .padding(18.dp),
            ) {
                ZcText("GitHub 访问令牌", size = 16.sp, weight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                ZcText(
                    "仓库私有时用于检查与下载更新。建议细粒度 PAT（仅本仓库 · 只读 Contents），仅存本机。留空保存即清除。",
                    size = 12.sp,
                    color = p.textMuted,
                )
                Spacer(Modifier.height(12.dp))
                ZcField(
                    value = value,
                    onValueChange = { value = it },
                    placeholder = "github_pat_…（留空清除）",
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZcButton(
                        label = "取消",
                        primary = false,
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    )
                    ZcButton(
                        label = "保存",
                        onClick = { onSave(value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun fmtSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes >= 100L * 1024 * 1024 -> "${bytes / 1024 / 1024} MB"
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024f / 1024f)
    else -> "${bytes / 1024} KB"
}
