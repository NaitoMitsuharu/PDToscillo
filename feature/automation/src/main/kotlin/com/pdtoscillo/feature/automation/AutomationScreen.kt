package com.pdtoscillo.feature.automation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.model.MeasurementType
import com.pdtoscillo.core.scpi.AutomationConfig
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.LabeledValue
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テスト用の目印。 */
const val AUTOMATION_LIST_TAG = "automationScreenList"

/**
 * 自動測定画面。
 *
 * 実行回数と上限時間を必ず指定させる。無制限の実行を作らない。
 */
@Composable
fun AutomationScreen(viewModel: AutomationViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(AUTOMATION_LIST_TAG)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Spacer(Modifier.height(4.dp)) }

        state.error?.let { error ->
            item {
                ErrorCard(
                    message = error.detail ?: error::class.simpleName.orEmpty(),
                    remedy = state.errorRemedy,
                    action = { TextButton(onClick = viewModel::clearError) { Text("閉じる") } },
                )
            }
        }

        if (state.readOnlyMode) {
            item {
                UnavailableNotice(
                    "読み取り専用モードです。自動測定は Single 実行など設定変更を伴うため、" +
                        "接続画面で解除してください。",
                )
            }
        }

        item { RunControlSection(state, viewModel) }
        item { ScheduleSection(state, viewModel) }
        item { TargetSection(state, viewModel) }
        item { OutputSection(state, viewModel) }

        if (state.iterations.isNotEmpty()) {
            item {
                SectionCard(title = "結果") {
                    LabeledValue("成功", state.iterations.count { it.succeeded }.toString())
                    LabeledValue("失敗", state.iterations.count { !it.succeeded }.toString())
                    LabeledValue("保存したファイル", state.savedFileCount.toString())
                }
            }
        }

        items(state.iterations, key = { it.index }) { iteration ->
            SectionCard(title = "${iteration.index} 回目（${iteration.elapsedMillis} ms）") {
                iteration.measurements.forEach { (name, value) ->
                    LabeledValue(name, value?.toString() ?: "---")
                }
                iteration.waveformPointCounts.forEach { (source, count) ->
                    LabeledValue("${source.displayName} 点数", count.toString())
                }
                iteration.error?.let {
                    Text(
                        text = "エラー: ${it.detail ?: it::class.simpleName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RunControlSection(state: AutomationUiState, viewModel: AutomationViewModel) {
    SectionCard(title = "実行") {
        if (state.running) {
            Text(state.currentStep, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = {
                    if (state.progressTotal > 0) {
                        state.progressIndex.toFloat() / state.progressTotal
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
        state.summary?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = viewModel::start,
                enabled = state.canStart,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("開始") }
            OutlinedButton(
                onClick = viewModel::stop,
                enabled = state.running,
                modifier = Modifier.weight(1f).heightIn(min = MinTouchTarget),
            ) { Text("停止") }
        }
    }
}

@Composable
private fun ScheduleSection(state: AutomationUiState, viewModel: AutomationViewModel) {
    SectionCard(title = "回数と時間") {
        OutlinedTextField(
            value = state.iterationsInput,
            onValueChange = viewModel::onIterationsChange,
            label = { Text("実行回数") },
            isError = !state.iterationsValid,
            supportingText = { Text("1〜${AutomationConfig.MAX_ITERATIONS}。無限ループを作らないための上限です。") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.intervalInput,
            onValueChange = viewModel::onIntervalChange,
            label = { Text("実行間隔") },
            suffix = { Text("ms") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.maxDurationMinutesInput,
            onValueChange = viewModel::onMaxDurationChange,
            label = { Text("上限時間") },
            suffix = { Text("分") },
            supportingText = { Text("この時間を超えたら、残り回数があっても打ち切ります。") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("エラーで停止する", modifier = Modifier.weight(1f))
            Switch(
                checked = state.stopOnError,
                onCheckedChange = viewModel::setStopOnError,
                enabled = !state.running,
            )
        }
    }
}

@Composable
private fun TargetSection(state: AutomationUiState, viewModel: AutomationViewModel) {
    SectionCard(title = "対象") {
        Text("チャンネル", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.availableSources.forEach { source ->
                FilterChip(
                    selected = source in state.selectedSources,
                    onClick = { viewModel.toggleSource(source) },
                    label = { Text(source.displayName) },
                    enabled = !state.running,
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("測定項目", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            MeasurementType.BASIC.forEach { type ->
                FilterChip(
                    selected = type in state.selectedMeasurements,
                    onClick = { viewModel.toggleMeasurement(type) },
                    label = { Text(type.displayName) },
                    enabled = !state.running,
                )
            }
        }
    }
}

@Composable
private fun OutputSection(state: AutomationUiState, viewModel: AutomationViewModel) {
    SectionCard(title = "取得と保存") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("測定値を取得", modifier = Modifier.weight(1f))
            Switch(
                checked = state.captureMeasurements,
                onCheckedChange = viewModel::setCaptureMeasurements,
                enabled = !state.running,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("波形を取得", modifier = Modifier.weight(1f))
            Switch(
                checked = state.captureWaveform,
                onCheckedChange = viewModel::setCaptureWaveform,
                enabled = !state.running,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("波形を CSV へ保存", modifier = Modifier.weight(1f))
            Switch(
                checked = state.saveCsv,
                onCheckedChange = viewModel::setSaveCsv,
                enabled = !state.running,
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.fileNameTemplate,
            onValueChange = viewModel::onTemplateChange,
            label = { Text("ファイル名テンプレート") },
            supportingText = { Text("{source} {index} {timestamp} が使えます。") },
            singleLine = true,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
