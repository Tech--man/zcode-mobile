package dev.xray.zcode.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.res.painterResource
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import dev.xray.zcode.R
import dev.xray.zcode.data.UrlUtils
import dev.xray.zcode.ui.ZcButton
import dev.xray.zcode.ui.ZcIconButton
import dev.xray.zcode.ui.ZcText
import dev.xray.zcode.ui.zcPalette
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

@Composable
fun ScanScreen(
    onResult: (String?) -> Unit,
    onBack: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    val context = LocalContext.current
    val p = zcPalette()
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        var camFailed by remember { mutableStateOf(false) }
        if (granted) {
            CameraPreview(
                onQr = { raw ->
                    val url = UrlUtils.normalize(raw)
                    if (url != null) {
                        onResult(url)
                    } else {
                        Toast.makeText(context, "二维码内容不是链接", Toast.LENGTH_SHORT).show()
                    }
                },
                onCamError = { camFailed = true },
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_camera),
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    colorFilter = ColorFilter.tint(p.textFaint),
                )
                Spacer(Modifier.height(14.dp))
                ZcText("需要相机权限", size = 16.sp, color = Color(0xFFE5E5E5), weight = androidx.compose.ui.text.font.FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                ZcText("扫码连接需要使用相机", size = 13.sp, color = Color(0xFFA3A3A3))
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ZcButton("重新授权", onClick = { launcher.launch(Manifest.permission.CAMERA) })
                    ZcButton("前往设置", primary = false, onClick = { openAppSettings(context) })
                }
            }
        }

        // 顶部返回栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZcIconButton(
                icon = R.drawable.ic_back,
                contentDescription = "返回",
                onClick = onBack,
                tint = Color.White,
            )
            ZcText("扫码连接", size = 16.sp, color = Color.White, weight = androidx.compose.ui.text.font.FontWeight.Medium)
        }

        if (granted) {
            // 取景框提示
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(230.dp)
                    .border(2.dp, Color(0xB3FFFFFF), RoundedCornerShape(18.dp)),
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)) {
                ZcText(
                    if (camFailed) "相机启动失败，请退出重进或检查相机权限" else "对准桌面端展示的二维码",
                    size = 13.sp,
                    color = if (camFailed) Color(0xFFF87171) else Color(0xCCFFFFFF),
                )
            }
        }
    }
}

private fun openAppSettings(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null),
            ),
        )
    } catch (_: Exception) {
    }
}

@Composable
private fun CameraPreview(onQr: (String) -> Unit, onCamError: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val lastSeen = remember { AtomicLong(0L) }
    val providerRef = remember { java.util.concurrent.atomic.AtomicReference<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            providerRef.get()?.unbindAll()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            try {
                val providerFuture = ProcessCameraProvider.getInstance(ctx)
                providerFuture.addListener({
                    try {
                        val provider = providerFuture.get()
                        providerRef.set(provider)
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val scanner = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                                .build(),
                        )
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { image ->
                            processFrame(scanner, image) { raw ->
                                val now = System.currentTimeMillis()
                                if (now - lastSeen.get() > 1500 && raw.isNotBlank()) {
                                    lastSeen.set(now)
                                    previewView.post { onQr(raw) }
                                }
                            }
                        }
                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            analysis,
                        )
                    } catch (t: Throwable) {
                        dev.xray.zcode.web.WebLog.log("fatal", "camerax bind: $t")
                        previewView.post { onCamError() }
                    }
                }, ContextCompat.getMainExecutor(ctx))
            } catch (t: Throwable) {
                // getInstance 同步抛（如进程异常死亡后的残留状态）也不能带崩应用
                dev.xray.zcode.web.WebLog.log("fatal", "camerax init: $t")
                previewView.post { onCamError() }
            }
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun processFrame(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    image: ImageProxy,
    onRaw: (String) -> Unit,
) {
    try {
        val mediaImage = image.image
        if (mediaImage == null) {
            image.close()
            return
        }
        val input = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
        scanner.process(input)
            .addOnSuccessListener { barcodes ->
                barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.let { onRaw(it.rawValue!!) }
            }
            .addOnCompleteListener { image.close() }
    } catch (_: Exception) {
        image.close()
    }
}
