package com.wwwaker.warer_android.ui.component

import android.annotation.SuppressLint
import android.graphics.Color
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.wwwaker.warer_android.ui.viewmodel.GraphFn
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GraphWebView(
    functions: List<GraphFn>,
    xMin: Double = -10.0, xMax: Double = 10.0,
    yMin: Double = -10.0, yMax: Double = 10.0,
    modifier: Modifier = Modifier
) {
    val webViewRef = remember { mutableListOf<WebView>() }
    val isDark = isSystemInDarkTheme()

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                setBackgroundColor(Color.TRANSPARENT)

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        renderFunctions(view, functions, xMin, xMax, yMin, yMax, isDark)
                    }
                }

                loadUrl("file:///android_asset/graph.html")
                webViewRef.add(this)
            }
        },
        modifier = modifier,
        update = { view ->
            if (view.progress == 100) {
                renderFunctions(view, functions, xMin, xMax, yMin, yMax, isDark)
            }
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            webViewRef.firstOrNull()?.destroy()
        }
    }
}

private fun renderFunctions(
    view: WebView,
    functions: List<GraphFn>,
    xMin: Double, xMax: Double,
    yMin: Double, yMax: Double,
    isDark: Boolean
) {
    val json = buildFunctionsJson(functions)
    val escaped = json
        .replace("\\", "\\\\")
        .replace("'", "\\'")
    view.evaluateJavascript(
        "renderFunctions('$escaped', $xMin, $xMax, $yMin, $yMax, $isDark)",
        null
    )
}

private fun buildFunctionsJson(functions: List<GraphFn>): String {
    val arr = JSONArray()
    for (fn in functions) {
        val obj = JSONObject()
        obj.put("id", fn.id)
        obj.put("fnType", fn.fnType)
        obj.put("expr", fn.expr)
        obj.put("xExpr", fn.xExpr)
        obj.put("yExpr", fn.yExpr)
        obj.put("color", fn.color)
        obj.put("hidden", fn.hidden)
        arr.put(obj)
    }
    return arr.toString()
}
