package dev.xray.zcode

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import dev.xray.zcode.data.ServerEntry
import dev.xray.zcode.data.ServerStore
import dev.xray.zcode.data.ThemeMode
import dev.xray.zcode.data.UrlUtils
import dev.xray.zcode.data.formatRelative
import dev.xray.zcode.ui.ZcButton
import dev.xray.zcode.ui.ZcField
import dev.xray.zcode.ui.ZcIconButton
import dev.xray.zcode.ui.ZcText
import dev.xray.zcode.ui.zcPalette

@Composable
fun HomeScreen(
    store: ServerStore,
    webViewWarning: String?,
    onConnect: (String) -> Unit,
    onScan: () -> Unit,
    onThemeCycle: () -> Unit,
    onOpenLogs: () -> Unit,
) {
    val p = zcPalette()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var input by remember { mutableStateOf("") }
    var inputError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(p.bg)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ZcText("ZCode", size = 26.sp, weight = FontWeight.Bold)
                ZcText("手机客户端", size = 13.sp, color = p.textMuted)
            }
            ZcIconButton(
                icon = R.drawable.ic_logs,
                contentDescription = "调试日志",
                onClick = onOpenLogs,
            )
            Spacer(Modifier.width(4.dp))
            ZcIconButton(
                icon = when (store.themeMode) {
                    ThemeMode.SYSTEM -> R.drawable.ic_auto
                    ThemeMode.LIGHT -> R.drawable.ic_sun
                    ThemeMode.DARK -> R.drawable.ic_moon
                },
                contentDescription = "切换主题",
                onClick = onThemeCycle,
            )
        }
        Spacer(Modifier.height(20.dp))

        // 连接面板
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(p.surface)
                .border(1.dp, p.border, RoundedCornerShape(14.dp))
                .padding(14.dp),
        ) {
            ZcText("连接到服务器", size = 13.sp, color = p.textMuted)
            Spacer(Modifier.height(10.dp))
            ZcField(
                value = input,
                onValueChange = { input = it; inputError = null },
                placeholder = "http://192.168.1.10:8080",
            )
            if (inputError != null) {
                Spacer(Modifier.height(6.dp))
                ZcText(inputError!!, size = 12.sp, color = p.danger)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ZcIconButton(
                    icon = R.drawable.ic_paste,
                    contentDescription = "粘贴",
                    onClick = {
                        val text = clipboard.getText()?.text ?: return@ZcIconButton
                        if (text.isNotBlank()) {
                            input = text.trim()
                            inputError = null
                        }
                    },
                )
                ZcButton(
                    label = "扫码",
                    primary = false,
                    icon = R.drawable.ic_scan,
                    onClick = onScan,
                    modifier = Modifier.weight(1f),
                )
                ZcButton(
                    label = "连接",
                    icon = R.drawable.ic_link,
                    onClick = {
                        val url = UrlUtils.normalize(input)
                        if (url == null) {
                            inputError = "链接无效：需为 http/https 地址（如 192.168.1.10:8080）"
                        } else {
                            onConnect(url)
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                )
            }
            Spacer(Modifier.height(10.dp))
            ZcText("扫码或粘贴桌面端展示的访问链接", size = 12.sp, color = p.textFaint)
            Spacer(Modifier.height(6.dp))
            ZcText("WebView ${dev.xray.zcode.web.webViewVersion()}", size = 11.sp, mono = true, color = p.textFaint)
        }

        if (webViewWarning != null) {
            Spacer(Modifier.height(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(p.surfaceAlt)
                    .border(1.dp, p.danger.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
            ) {
                ZcText("兼容性提示", size = 12.sp, color = p.danger, weight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                ZcText(webViewWarning, size = 12.sp, color = p.textMuted)
            }
        }

        Spacer(Modifier.height(24.dp))
        ZcText(
            "已保存的服务器",
            size = 12.sp,
            color = p.textMuted,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.height(8.dp))

        if (store.servers.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_fg),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).alpha(0.7f),
                )
                Spacer(Modifier.height(12.dp))
                ZcText("暂无记录", size = 14.sp, weight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                ZcText("扫码或粘贴链接，连接你的 ZCode", size = 13.sp, color = p.textMuted)
            }
        } else {
            store.servers.forEach { entry ->
                ServerRow(
                    entry = entry,
                    onClick = { onConnect(entry.url) },
                    onDelete = {
                        store.remove(entry.url)
                        Toast.makeText(context, "已删除", Toast.LENGTH_SHORT).show()
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ServerRow(entry: ServerEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    val p = zcPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(p.surfaceAlt)
            .border(1.dp, p.border, RoundedCornerShape(12.dp))
            .alpha(if (pressed) 0.7f else 1f)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            ZcText(UrlUtils.hostOf(entry.url), size = 15.sp, mono = true, weight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            ZcText(
                "${UrlUtils.schemeOf(entry.url)} · ${formatRelative(entry.lastConnected)}",
                size = 12.sp,
                color = p.textMuted,
            )
        }
        ZcIconButton(
            icon = R.drawable.ic_trash,
            contentDescription = "删除 ${UrlUtils.hostOf(entry.url)}",
            onClick = onDelete,
            tint = p.textFaint,
        )
    }
}
