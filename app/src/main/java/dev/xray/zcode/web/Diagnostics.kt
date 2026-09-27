package dev.xray.zcode.web

import android.content.Context
import android.webkit.WebView
import android.widget.Toast

/**
 * 视口探针：在**同一次求值**里取 innerHeight/clientHeight 与 vh/vw/dvh/svh/lvh/% 的解析值，
 * 并附 Android 侧视图尺寸与 Resources 度量。
 *
 * 关键区分（真机实测已确认二者可以背离）：
 *  - innerHeight / documentElement.clientHeight / visualViewport → FrameView 尺寸
 *  - vh / vw / dvh / 根元素 height:100% → 渲染进程里的 ICB / layout_size
 * 「黑屏但无障碍可读」= 后者为 0 而前者正常。
 */
private const val VIEWPORT_PROBE_JS = """
(function(){
var de=document.documentElement,host=document.body||de;
function measure(pos,css){var d=document.createElement('div');
d.style.cssText='position:'+pos+';left:0;top:0;'+css+';visibility:hidden;pointer-events:none';
host.appendChild(d);var r=d.getBoundingClientRect();d.parentNode.removeChild(d);return r.width+'/'+r.height}
var root=document.getElementById('root'),hd=document.querySelector('.h-dvh');
return 'ih='+window.innerHeight+' iw='+window.innerWidth+
' vv='+(window.visualViewport?window.visualViewport.height:-1)+
' deClient='+de.clientWidth+'x'+de.clientHeight+' deCssH='+getComputedStyle(de).height+
' fixedVH='+measure('fixed','height:100vh')+
' absVH='+measure('absolute','height:100vh')+
' dvh='+measure('absolute','height:100dvh').split('/')[1]+
' svh='+measure('absolute','height:100svh').split('/')[1]+
' lvh='+measure('absolute','height:100lvh').split('/')[1]+
' absPct='+measure('absolute','height:100%').split('/')[1]+
' fixedPct='+measure('fixed','height:100%').split('/')[1]+
' rootH='+(root?Math.round(root.getBoundingClientRect().height):-1)+
' hdvhH='+(hd?Math.round(hd.getBoundingClientRect().height):-1)+
' bodyLen='+(document.body?document.body.innerText.length:-1);
})()
"""

/** 探针落盘：Android 侧 + JS 侧同一时刻成对写入；toast 非空时把结果现场显示出来。 */
internal fun probeViewport(view: WebView, at: String, toast: Context? = null) {
    val dm = view.resources.displayMetrics
    val cfg = view.resources.configuration
    val vf = android.graphics.Rect()
    view.getWindowVisibleDisplayFrame(vf)
    val native =
        "view=${view.width}x${view.height} win=${view.windowVisibility}" +
            " attached=${view.isAttachedToWindow}" +
            " visFrame=${vf.width()}x${vf.height()}@$vf" +
            " dm=${dm.widthPixels}x${dm.heightPixels} dpr=${dm.density}" +
            " cfgHdp=${cfg.screenHeightDp} ctx=${view.context.javaClass.simpleName}"
    view.evaluateJavascript(VIEWPORT_PROBE_JS) { r ->
        val js = (r ?: "null").trim().removeSurrounding("\"").replace("\\n", " ")
        WebLog.log("vport", "$at build=${buildVersion()} $native | $js")
        if (toast != null) {
            runCatching { Toast.makeText(toast, "$at\n$js", Toast.LENGTH_LONG).show() }
        }
    }
}
