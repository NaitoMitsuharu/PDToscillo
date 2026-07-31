package com.pdtoscillo.feature.console

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.StatusChip
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テスト用の目印。 */
const val CONSOLE_LOG_TAG = "consoleLog"
const val CONSOLE_INPUT_TAG = "consoleInput"

/**
 * SCPI コンソール。
 *
 * ネイティブ画面に専用 UI が無い操作でも、公式マニュアルに記載された SCPI であれば
 * ここから実行できる。未実装の機能があっても行き止まりにならないための逃げ道。
 */
@Composable
fun ConsoleScreen(viewModel: ConsoleViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showScript by remember { mutableStateOf(false) }

    state.pendingDangerousCommand?.let { command ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDangerousCommand,
            title = { Text("このコマンドを実行しますか？") },
            text = {
                Column {
                    Text(
                        text = command,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "本体の設定を大きく変える、または取り消せない操作です。" +
                            "実行前に現在の設定を控えておくことをおすすめします。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmDangerousCommand) { Text("実行") } },
            dismissButton = { TextButton(onClick = viewModel::dismissDangerousCommand) { Text("キャンセル") } },
        )
    }

    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusChip(
                text = if (state.readOnlyMode) "読み取り専用" else "設定変更可",
                color = if (state.readOnlyMode) Color(0xFFB0BEC5) else Color(0xFFFFD180),
            )
            Spacer(Modifier.fillMaxWidth(0.02f))
            Text(
                text = "設定変更を許可",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = !state.readOnlyMode,
                onCheckedChange = { viewModel.setReadOnlyMode(!it) },
            )
        }

        state.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            ErrorCard(
                message = error.detail ?: error::class.simpleName.orEmpty(),
                remedy = state.errorRemedy,
                action = { TextButton(onClick = viewModel::clearError) { Text("閉じる") } },
            )
        }

        state.notice?.let { notice ->
            Spacer(Modifier.height(8.dp))
            SectionCard(
                title = "確認",
                trailing = { TextButton(onClick = viewModel::clearNotice) { Text("閉じる") } },
            ) {
                Text(notice, style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(Modifier.height(8.dp))
        LogList(
            state = state,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        )

        Spacer(Modifier.height(4.dp))
        // よく使うコマンドのクイックボタン
        QuickCommandRow(viewModel)

        Spacer(Modifier.height(4.dp))
        FavoritesRow(state, viewModel)

        if (showScript) {
            ScriptSection(state, viewModel)
        }

        InputRow(
            state = state,
            viewModel = viewModel,
            showScript = showScript,
            onToggleScript = { showScript = !showScript },
        )
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LogList(state: ConsoleUiState, modifier: Modifier = Modifier) {
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // 新しい行が増えたら最後尾へ送る。手で追いかけなくてよいようにする。
    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) listState.animateScrollToItem(state.entries.lastIndex)
    }

    if (state.entries.isEmpty()) {
        UnavailableNotice(
            "コマンドを入力して実行してください。末尾が ? の場合は応答を待ちます。\n" +
                "公式 Programmer Manual に記載されたコマンドがそのまま使えます。",
        )
        return
    }

    LazyColumn(
        modifier = modifier.testTag(CONSOLE_LOG_TAG),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.entries, key = { it.id }) { entry ->
            ConsoleEntryRow(entry)
        }
    }
}

@Composable
private fun ConsoleEntryRow(entry: ConsoleEntry) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (entry.kind) {
                    ConsoleEntry.Kind.INFO -> "•"
                    else -> ">"
                },
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = " ${entry.command}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            if (entry.kind != ConsoleEntry.Kind.INFO) {
                Text(
                    text = "${entry.elapsedMillis} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        entry.response?.let { response ->
            Text(
                text = "  $response",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        entry.binarySummary?.let { summary ->
            Text(
                text = "  (バイナリ) $summary",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        entry.error?.let { error ->
            Text(
                text = "  ${error::class.simpleName}: ${error.detail ?: ""}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun QuickCommandRow(viewModel: ConsoleViewModel) {
    val quickCommands = listOf(
        "*IDN?" to "*IDN?",
        "Run" to "ACQuire:STATE RUN",
        "Stop" to "ACQuire:STATE STOP",
        "CH1 ON" to "CH1:DISplay ON",
        "CH2 ON" to "CH2:DISplay ON",
        "Autoset" to "AUTOSet EXECute",
        "エラー?" to "SYSTem:ERRor?",
        "*CLS" to "*CLS",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        quickCommands.forEach { (label, command) ->
            AssistChip(
                onClick = { viewModel.useFavorite(command) },
                label = { Text(label, fontFamily = FontFamily.Monospace) },
            )
        }
    }
}

@Composable
private fun FavoritesRow(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        state.favorites.forEach { command ->
            AssistChip(
                onClick = { viewModel.useFavorite(command) },
                label = { Text(command, fontFamily = FontFamily.Monospace) },
            )
        }
    }
}

@Composable
private fun ScriptSection(state: ConsoleUiState, viewModel: ConsoleViewModel) {
    SectionCard(title = "スクリプト") {
        Text(
            text = "1 行に 1 コマンド。# 以降は注釈。失敗した時点で中断します。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = state.scriptInput,
            onValueChange = viewModel::onScriptChange,
            label = { Text("コマンド列") },
            minLines = SCRIPT_MIN_LINES,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = viewModel::runScript,
            enabled = !state.busy && state.scriptInput.isNotBlank(),
            modifier = Modifier.fillMaxWidth().heightIn(min = MinTouchTarget),
        ) { Text(if (state.scriptRunning) "実行中" else "スクリプトを実行") }
    }
}

@Composable
private fun InputRow(state: ConsoleUiState, viewModel: ConsoleViewModel, showScript: Boolean, onToggleScript: () -> Unit) {
    Column {
        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::onInputChange,
            label = { Text("SCPI コマンド") },
            placeholder = { Text("*IDN?") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            supportingText = {
                Text(
                    when {
                        state.input.isBlank() -> "末尾が ? なら応答を待ちます"
                        state.inputDangerLevel == com.pdtoscillo.core.scpi.DangerLevel.DANGEROUS ->
                            "確認が必要なコマンドです"

                        state.inputIsQuery -> "問い合わせとして送ります"
                        state.readOnlyMode -> "設定変更は読み取り専用モードのため拒否されます"
                        else -> "設定変更として送ります"
                    },
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(CONSOLE_INPUT_TAG),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::submit,
                enabled = !state.busy && state.input.isNotBlank(),
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("実行") }
            OutlinedButton(
                onClick = viewModel::checkErrorQueue,
                enabled = !state.busy,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("エラー確認") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = viewModel::addFavorite, enabled = state.input.isNotBlank()) {
                Text("お気に入りへ追加")
            }
            TextButton(onClick = onToggleScript) {
                Text(if (showScript) "スクリプトを隠す" else "スクリプト")
            }
            TextButton(onClick = viewModel::clearEntries) { Text("ログを消去") }
        }
    }
}

private const val SCRIPT_MIN_LINES = 3
