package com.pdtoscillo.feature.settings

import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テスト用の目印。 */
const val SETTINGS_LIST_TAG = "settingsScreenList"

/** 対応する表示言語。既定は日本語。 */
enum class AppLanguage(val tag: String, val displayName: String) {
    JAPANESE("ja", "日本語"),
    ENGLISH("en", "English"),
}

/**
 * 設定画面。
 *
 * 表示言語の切り替え、通信ログの確認、接続情報の表示を行う。
 */
@Composable
fun SettingsScreen(session: InstrumentSession, modifier: Modifier = Modifier) {
    val logEntries by session.logRecorder.entries.collectAsStateWithLifecycle()
    val readOnly by session.client.readOnlyMode.collectAsStateWithLifecycle()
    val identity by session.client.identity.collectAsStateWithLifecycle()
    val capabilities by session.client.capabilities.collectAsStateWithLifecycle()

    var showRawCommands by remember { mutableStateOf(false) }
    var showGlossary by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(SETTINGS_LIST_TAG)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        item {
            SectionCard(title = "表示言語") {
                Text(
                    text = "既定は日本語です。切り替えるとアプリの表示のみが変わります。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                val current = AppCompatDelegate.getApplicationLocales()
                    .toLanguageTags()
                    .split(',')
                    .firstOrNull()
                    ?.take(2)
                    .orEmpty()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppLanguage.entries.forEach { language ->
                        FilterChip(
                            selected = current == language.tag,
                            onClick = {
                                AppCompatDelegate.setApplicationLocales(
                                    LocaleListCompat.forLanguageTags(language.tag),
                                )
                            },
                            label = { Text(language.displayName) },
                            modifier = Modifier.heightIn(min = MinTouchTarget),
                        )
                    }
                }
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    Spacer(Modifier.height(8.dp))
                    UnavailableNotice(
                        "この端末では AppCompat が言語設定を保持します。" +
                            "OS の「アプリごとの言語」設定は Android 13 以降で使えます。",
                    )
                }
            }
        }

        item {
            SectionCard(title = "接続中の機器") {
                LabeledValue("モデル", identity?.model?.ifBlank { "不明" } ?: "未接続")
                LabeledValue("ファームウェア", identity?.firmwareVersion ?: "不明")
                LabeledValue("見るだけモード（安全）", if (readOnly) "有効" else "解除中")
                capabilities?.let {
                    LabeledValue("検出方法", it.detectionSource.name)
                    if (it.undeterminedFeatures.isNotEmpty()) {
                        LabeledValue("判定できなかった機能", it.undeterminedFeatures.joinToString())
                    }
                }
            }
        }

        item {
            SectionCard(title = "用語集（オシロスコープの言葉）") {
                Text(
                    text = "画面に出てくる主な言葉を、短い説明で確認できます。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(
                    onClick = { showGlossary = !showGlossary },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                ) { Text(if (showGlossary) "用語集を閉じる" else "用語集を開く") }
                if (showGlossary) {
                    OSCILLOSCOPE_GLOSSARY.forEach { (term, description) ->
                        Spacer(Modifier.height(8.dp))
                        Text(term, style = MaterialTheme.typography.labelLarge)
                        Text(
                            description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SectionCard(
                title = "通信ログ",
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("生コマンド", style = MaterialTheme.typography.labelSmall)
                        Switch(checked = showRawCommands, onCheckedChange = { showRawCommands = it })
                    }
                },
            ) {
                Text(
                    text = "通常は生の SCPI コマンドを表示しません。診断が必要なときだけ有効にしてください。" +
                        "バイナリ応答は本体を保持せず、サイズとハッシュのみ記録します。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LabeledValue("記録件数", logEntries.size.toString())
                LabeledValue("失敗", logEntries.count { it.error != null }.toString())

                if (showRawCommands) {
                    Spacer(Modifier.height(8.dp))
                    logEntries.takeLast(LOG_PREVIEW_COUNT).forEach { entry ->
                        Text(
                            text = "${entry.kind} ${entry.command} " +
                                "(${entry.durationMillis ?: "-"} ms)" +
                                (entry.error?.let { " !! ${it::class.simpleName}" } ?: ""),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { session.logRecorder.clear() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
                ) { Text("ログを消去") }
            }
        }

        item {
            SectionCard(title = "このアプリについて") {
                Text(
                    text = "PDToscillo は Sony PDT-FP1 から Tektronix 4000 シリーズを LAN 経由で" +
                        "操作するアプリです。実装状況と実機未確認の項目は README と " +
                        "docs/hardware-validation.md に記載しています。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "計測器の遠隔操作は信頼できるネットワークでのみ行ってください。" +
                        "Socket Server の SCPI 通信は暗号化も認証もされません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private const val LOG_PREVIEW_COUNT = 20

private val OSCILLOSCOPE_GLOSSARY = listOf(
    "取り込み（Acquisition）" to "波形を何回、どうやって取り込むかの設定です。",
    "横軸（時間）" to "画面の横方向＝時間です。1 目盛りが何秒かを決めます。",
    "縦軸（電圧）" to "画面の縦方向＝電圧です。1 目盛りが何ボルトかを決めます。",
    "トリガ（波形を止める基準）" to "条件を決めて、波形を安定して表示するための基準点です。",
    "サンプルレート" to "1 秒間に測定する回数です。速い信号ほど大きい値が要ります。",
    "記録点数" to "1 回の取り込みで記録する点の数です。",
    "入力の結合（AC/DC）" to "DC は信号をそのまま、AC は直流成分を除いて表示します。",
    "プローブ倍率" to "実物のプローブに書かれた 1:1、10:1 などの倍率に合わせます。",
    "見るだけモード（安全）" to "誤操作で本体設定を変えないための保護です。変更するときだけ解除します。",
)
