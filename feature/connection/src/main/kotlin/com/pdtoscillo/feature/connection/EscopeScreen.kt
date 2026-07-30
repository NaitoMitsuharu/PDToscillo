package com.pdtoscillo.feature.connection

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * e*Scope（LXI Web UI）をアプリ内で開く。
 *
 * 用途は本体画面の遠隔確認と、ネイティブ画面に無い操作の補助。
 * **e*Scope の画面を解析して非公式 API として利用することはしない。** 表示するだけ。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EscopeScreen(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    // 対応するブラウザが無い端末でクラッシュさせない。
                    if (intent.resolveActivity(context.packageManager) != null ||
                        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                            .isNotEmpty()
                    ) {
                        context.startActivity(intent)
                    }
                },
            ) { Text("外部ブラウザで開く") }
        }

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                WebView(viewContext).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // 計測器のローカル Web UI を表示するだけなので、
                    // ファイルアクセスやコンテンツプロバイダへのアクセスは許可しない。
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    loadUrl(url)
                }
            },
            update = { webView ->
                if (webView.url != url) webView.loadUrl(url)
            },
        )
    }
}
