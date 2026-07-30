package com.pdtoscillo.feature.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.ui.component.BusyIndicator
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テスト用の目印。 */
const val FILES_LIST_TAG = "filesScreenList"

/**
 * 本体のファイル操作画面。
 *
 * 削除と設定の呼び出しは取り消せないため、必ず確認を挟む。
 */
@Composable
fun FilesScreen(viewModel: FilesViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { }
    }

    state.pendingConfirmation?.let { confirmation ->
        val (title, message) = when (confirmation) {
            is FileConfirmation.Delete ->
                "${confirmation.name} を削除しますか？" to "本体からファイルを削除します。取り消せません。"

            is FileConfirmation.RecallSetup ->
                "${confirmation.name} を呼び出しますか？" to
                    "本体の設定が保存内容へ置き換わります。現在の設定は失われます。"
        }
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmation,
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::confirmPending) { Text("実行") } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirmation) { Text("キャンセル") } },
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(FILES_LIST_TAG)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }
        item { BusyIndicator(visible = state.busy, label = state.busyLabel) }

        state.error?.let { error ->
            item {
                ErrorCard(
                    message = error.detail ?: error::class.simpleName.orEmpty(),
                    remedy = state.errorRemedy,
                    action = { TextButton(onClick = viewModel::clearError) { Text("閉じる") } },
                )
            }
        }

        state.notice?.let { notice ->
            item {
                SectionCard(
                    title = "結果",
                    trailing = { TextButton(onClick = viewModel::clearNotice) { Text("閉じる") } },
                ) {
                    Text(notice, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item {
            SectionCard(
                title = "本体のストレージ",
                trailing = { TextButton(onClick = viewModel::refresh) { Text("更新") } },
            ) {
                LabeledValue("現在のディレクトリ", state.currentDirectory ?: "不明")
                LabeledValue(
                    "空き容量",
                    state.freeSpaceBytes?.let { EngineeringUnits.formatBytes(it) } ?: "不明",
                )
            }
        }

        item {
            SectionCard(title = "本体へ保存") {
                if (state.readOnlyMode) {
                    UnavailableNotice("読み取り専用モードです。保存するには接続画面で解除してください。")
                }
                OutlinedTextField(
                    value = state.saveNameInput,
                    onValueChange = viewModel::onSaveNameChange,
                    label = { Text("ファイル名") },
                    placeholder = { Text("setup1.set") },
                    singleLine = true,
                    enabled = !state.readOnlyMode,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "親ディレクトリ参照 (..)、引用符、改行、セミコロンは使えません。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = viewModel::saveSetup,
                        enabled = !state.readOnlyMode && !state.busy && state.saveNameInput.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) { Text("設定") }
                    OutlinedButton(
                        onClick = viewModel::saveImage,
                        enabled = !state.readOnlyMode && !state.busy && state.saveNameInput.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) { Text("画面") }
                    OutlinedButton(
                        onClick = { viewModel.saveWaveform("CH1") },
                        enabled = !state.readOnlyMode && !state.busy && state.saveNameInput.isNotBlank(),
                        modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
                    ) { Text("波形") }
                }
            }
        }

        item {
            SectionCard(title = "本体のファイル") {
                if (state.files.isEmpty()) {
                    UnavailableNotice("ファイル一覧を取得できていません。「更新」を押してください。")
                }
            }
        }

        items(state.files, key = { it.raw }) { file ->
            SectionCard(title = file.name.ifBlank { file.raw }) {
                LabeledValue("種類", if (file.isDirectory) "ディレクトリ" else "ファイル")
                if (!file.isDirectory) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { viewModel.download(file.name) },
                            enabled = !state.busy,
                        ) { Text("取り込む") }
                        TextButton(
                            onClick = { viewModel.requestRecallSetup(file.name) },
                            enabled = !state.readOnlyMode && !state.busy,
                        ) { Text("呼び出し") }
                        TextButton(
                            onClick = { viewModel.requestDelete(file.name) },
                            enabled = !state.readOnlyMode && !state.busy,
                        ) { Text("削除") }
                    }
                }
            }
        }

        if (state.downloads.isNotEmpty()) {
            item {
                SectionCard(title = "取り込み履歴") {
                    state.downloads.forEach { download ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(download.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${EngineeringUnits.formatBytes(download.sizeBytes)} / " +
                                    "SHA-256 ${download.sha256.take(SHA_PREVIEW)}",
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private const val SHA_PREVIEW = 12
