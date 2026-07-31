package com.pdtoscillo.feature.measurement

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.scpi.MeasurementSlot
import com.pdtoscillo.core.ui.component.BusyIndicator
import com.pdtoscillo.core.ui.component.ErrorCard
import com.pdtoscillo.core.ui.component.SectionCard
import com.pdtoscillo.core.ui.component.UnavailableNotice
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** UI テスト用の目印。 */
const val MEASUREMENT_LIST_TAG = "measurementScreenList"

/**
 * 測定画面。
 *
 * 測定できない状態（マニュアル記載の 9.91e37）は数値として出さず、
 * 「測定不可」と示す。極端に大きい数値をそのまま見せない。
 */
@Composable
fun MeasurementScreen(viewModel: MeasurementViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onVisible()
        onPauseOrDispose { viewModel.onHidden() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(MEASUREMENT_LIST_TAG)
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

        if (state.readOnlyMode) {
            item {
                UnavailableNotice(
                    "読み取り専用モードです。測定値の確認のみできます。追加・削除には解除が必要です。",
                )
            }
        }

        item { AddMeasurementSection(state, viewModel) }

        item {
            SectionCard(
                title = "測定値",
                trailing = { TextButton(onClick = viewModel::refresh) { Text("更新") } },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("統計を取る", modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.statisticsEnabled,
                        onCheckedChange = viewModel::setStatisticsEnabled,
                        enabled = !state.readOnlyMode && !state.busy,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自動更新", modifier = Modifier.weight(1f))
                    Switch(checked = state.autoRefresh, onCheckedChange = viewModel::setAutoRefresh)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MeasurementUiState.INTERVALS.forEach { interval ->
                        FilterChip(
                            selected = state.refreshIntervalMillis == interval,
                            onClick = { viewModel.setRefreshInterval(interval) },
                            label = { Text(if (interval >= 1000) "${interval / 1000} s" else "$interval ms") },
                        )
                    }
                }
            }
        }

        if (state.activeSlots.isEmpty()) {
            item { UnavailableNotice("測定が割り当てられていません。上の「追加」から選んでください。") }
        }

        items(state.activeSlots, key = { it.slot }) { slot ->
            MeasurementCard(slot = slot, state = state, viewModel = viewModel)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AddMeasurementSection(state: MeasurementUiState, viewModel: MeasurementViewModel) {
    SectionCard(title = "測定を追加") {
        Text("種類", style = MaterialTheme.typography.labelLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
            state.availableTypes.forEach { type ->
                FilterChip(
                    selected = state.pendingType == type,
                    onClick = { viewModel.setPendingType(type) },
                    label = { Text(type.displayName) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("ソース", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.availableSources.forEach { source ->
                FilterChip(
                    selected = state.pendingSource == source,
                    onClick = { viewModel.setPendingSource(source) },
                    label = { Text(source.displayName) },
                )
            }
        }

        if (state.pendingType.requiresSecondSource) {
            Spacer(Modifier.height(8.dp))
            Text("2 つ目のソース", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.availableSources.forEach { source ->
                    FilterChip(
                        selected = state.pendingSecondSource == source,
                        onClick = { viewModel.setPendingSecondSource(source) },
                        label = { Text(source.displayName) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::addMeasurement,
            enabled = !state.readOnlyMode && !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget),
        ) { Text("追加") }
        Text(
            text = "同時測定数: ${state.activeSlots.size} / ${state.maxSlots}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MeasurementCard(slot: MeasurementSlot, state: MeasurementUiState, viewModel: MeasurementViewModel) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            // 削除ボタン（右上）
            TextButton(
                onClick = { viewModel.removeMeasurement(slot.slot) },
                enabled = !state.readOnlyMode && !state.busy,
                modifier = Modifier.align(Alignment.TopEnd),
            ) { Text("削除", style = MaterialTheme.typography.labelSmall) }

            androidx.compose.foundation.layout.Column {
                // 測定名 + ソース
                Text(
                    text = slot.type?.displayName ?: slot.typeRaw ?: "測定 ${slot.slot}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = slot.source?.displayName ?: "?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                if (slot.isNotMeasurable) {
                    UnavailableNotice("波形が表示されているか、信号が測定条件を満たしているか確認してください。")
                } else {
                    val unit = slot.unit ?: slot.type?.quantity?.defaultUnit ?: ""
                    val formatted = formatMeasValue(slot.statistics.current, unit)
                    // 現在値を大きく表示
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = formatted.first,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            text = formatted.second,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (state.statisticsEnabled) {
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            MiniStat("平均", slot.statistics.mean, unit)
                            MiniStat("最小", slot.statistics.minimum, unit)
                            MiniStat("最大", slot.statistics.maximum, unit)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableSources.forEach { source ->
                        FilterChip(
                            selected = slot.source == source,
                            onClick = { viewModel.changeSource(slot.slot, source) },
                            label = { Text(source.displayName) },
                            enabled = !state.readOnlyMode && !state.busy,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStat(label: String, value: Double?, unit: String) {
    val (v, u) = formatMeasValue(value, unit)
    androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$v $u".trim(), style = MaterialTheme.typography.bodySmall)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatMeasValue(value: Double?, unit: String): Pair<String, String> {
    if (value == null) return "---" to ""
    if (com.pdtoscillo.core.model.MeasurementStatistics.isNotANumber(value)) return "測定不可" to ""
    val eng = EngineeringUnits.format(value, unit)
    return eng.value to eng.unit
}
