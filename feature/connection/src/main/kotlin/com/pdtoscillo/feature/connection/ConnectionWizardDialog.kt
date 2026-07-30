package com.pdtoscillo.feature.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 初回接続の手順案内。
 *
 * オシロスコープ側の設定が終わっていないと絶対に繋がらないため、
 * 何をどの順で確認すればよいかを画面上に置く。
 */
@Composable
fun ConnectionWizardDialog(suggestedPort: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        title = { Text("接続の手順") },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("閉じる") }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                WIZARD_STEPS.forEachIndexed { index, step ->
                    WizardStepRow(number = index + 1, text = step)
                }

                Spacer(Modifier.height(16.dp))
                Text("DHCP が使えない直結の場合", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "双方に静的 IP を設定します。以下は設定例で、この値でなければならないわけではありません。" +
                        "同じサブネットであれば任意の値で構いません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = """
                            PDT-FP1
                              IP           192.168.10.1
                              Subnet mask  255.255.255.0

                            オシロスコープ
                              IP           192.168.10.2
                              Subnet mask  255.255.255.0

                            Port           ${suggestedPort.ifBlank { "4000" }}
                        """.trimIndent(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text("補足", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Protocol を Terminal のままにすると、コマンドのエコーやプロンプトが応答へ混ざります。" +
                        "本アプリはこれを検出して警告しますが、None に設定してください。\n\n" +
                        "対応機種では、ブラウザで http://<オシロスコープの IP>/ を開くと e*Scope が使えます。" +
                        "ネイティブ画面に無い操作の補助として利用できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun WizardStepRow(number: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

private val WIZARD_STEPS = listOf(
    "PDT-FP1 とオシロスコープを LAN ケーブルで接続します。",
    "オシロスコープ前面の Utility ボタンを押します。",
    "Utility Page から I/O を選びます。",
    "Ethernet Network Settings（または LAN 設定）を開きます。",
    "Socket Server を選び、Enabled を On にします。",
    "Protocol を None に設定します（Terminal ではありません）。",
    "ポート番号を確認します。初期候補は 4000 です。",
    "オシロスコープの IP アドレスを確認します。",
    "この画面へ IP アドレスとポートを入力します。",
    "「接続」を押し、続けて「接続診断」で各段階を確認します。",
    "*IDN? による識別と対応機能の自動診断結果を確認します。",
)
