package dev.xray.zcode.web

import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.xray.zcode.R
import dev.xray.zcode.ui.ZcIconButton
import dev.xray.zcode.ui.ZcText
import dev.xray.zcode.ui.zcPalette

@Composable
fun LogScreen(onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val p = zcPalette()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var rev by remember { mutableStateOf(0) }
    val lines = remember(rev) { WebLog.snapshot() }
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.scrollToItem(lines.size - 1)
    }

    val deviceInfo = buildString {
        append("device: ${Build.MANUFACTURER} ${Build.MODEL}")
        append(" | android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        append(" | webview ${webViewVersion()}")
        append(" | app ${buildVersion()}")
    }

    Column(Modifier.fillMaxSize().background(p.bg).statusBarsPadding()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            ZcIconButton(icon = R.drawable.ic_back, contentDescription = "返回", onClick = onBack)
            ZcText("调试日志", size = 16.sp, weight = FontWeight.Medium, modifier = Modifier.weight(1f))
            ZcIconButton(icon = R.drawable.ic_copy, contentDescription = "复制全部", onClick = {
                clipboard.setText(AnnotatedString(deviceInfo + "\n" + lines.joinToString("\n")))
                Toast.makeText(context, "已复制 ${lines.size} 条日志", Toast.LENGTH_SHORT).show()
            })
            ZcIconButton(icon = R.drawable.ic_external, contentDescription = "分享", onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, deviceInfo + "\n" + lines.joinToString("\n"))
                }
                context.startActivity(Intent.createChooser(intent, "分享日志"))
            })
            ZcIconButton(icon = R.drawable.ic_trash, contentDescription = "清空", onClick = {
                WebLog.clear()
                rev++
            })
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(p.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ZcText(deviceInfo, size = 11.sp, mono = true, color = p.textMuted)
        }

        Spacer(Modifier.height(6.dp))
        if (lines.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(60.dp))
                ZcText("暂无日志", size = 14.sp, weight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                ZcText("连接一次服务器后，这里会记录加载过程", size = 12.sp, color = p.textMuted)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
            ) {
                items(lines) { line ->
                    ZcText(
                        line,
                        size = 10.5.sp,
                        mono = true,
                        color = p.textMuted,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
