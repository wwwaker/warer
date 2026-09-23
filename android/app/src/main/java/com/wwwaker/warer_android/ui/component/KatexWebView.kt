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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme

@Composable
fun KatexWebView(
    latex: String,
    displayMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface
    val webView = remember {
        WebView(context).apply {
            @SuppressLint("SetJavaScriptEnabled")
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            setBackgroundColor(Color.TRANSPARENT)
        }
    }

    val html = buildKatexHtml(latex, displayMode, textColor.toArgb())

    DisposableEffect(latex, displayMode, isDark) {
        webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        onDispose { }
    }

    AndroidView(
        factory = { webView },
        modifier = modifier
    )
}

private fun buildKatexHtml(latex: String, displayMode: Boolean, textColorArgb: Int): String {
    val display = if (displayMode) "true" else "false"
    val escapedLatex = latex
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")

    val hexColor = String.format("#%06X", 0xFFFFFF and textColorArgb)

    return """
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
    <script src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            font-family: KaTeX_Main, 'Times New Roman', serif;
            display: flex; align-items: center;
            min-height: 48px;
            padding: 8px;
            overflow-x: auto;
            overflow-y: hidden;
            background: transparent;
            color: $hexColor;
        }
        .katex { font-size: 1.2em; color: $hexColor; }
        .katex .mathnormal { color: inherit; }
        .error { color: #d32f2f; font-size: 14px; font-family: sans-serif; }
    </style>
</head>
<body>
    <div id="math"></div>
    <script>
        try {
            katex.render('$escapedLatex', document.getElementById('math'), {
                displayMode: $display,
                throwOnError: false,
                output: 'html'
            });
        } catch(e) {
            document.getElementById('math').innerHTML = '<span class="error">' + e.message + '</span>';
        }
    </script>
</body>
</html>
""".trimIndent()
}
