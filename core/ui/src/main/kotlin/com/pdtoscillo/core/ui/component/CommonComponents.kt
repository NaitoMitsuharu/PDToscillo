package com.pdtoscillo.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pdtoscillo.core.common.EngineeringUnits
import com.pdtoscillo.core.ui.theme.MinTouchTarget

/** 状態を示す小さなラベル。接続状態や機能の有効・無効に使う。 */
@Composable
fun StatusChip(text: String, color: Color, modifier: Modifier = Modifier, showDot: Boolean = true) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = color.copy(alpha = 0.16f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, CircleShape),
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = color,
            )
        }
    }
}

/**
 * 項目名と値の 1 行。
 *
 * 値は等幅で表示し、更新のたびに行が揺れないようにする。
 */
@Composable
fun LabeledValue(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = valueColor,
            textAlign = TextAlign.End,
        )
        if (unit != null) {
            Spacer(Modifier.width(4.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 数値と単位を分けて表示する。工学表記を使う。 */
@Composable
fun EngineeringValue(label: String, value: Double?, unit: String, modifier: Modifier = Modifier) {
    val formatted = value?.let { EngineeringUnits.format(it, unit) }
    LabeledValue(
        label = label,
        value = formatted?.value ?: "---",
        unit = formatted?.unit ?: unit,
        modifier = modifier,
    )
}

/** 見出し付きの区画。 */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                trailing?.invoke()
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

/**
 * エラー表示。
 *
 * **エラー内容と対処方法を必ず同時に表示する。** 何が起きたかだけ見せても利用者は次の行動が取れない。
 */
@Composable
fun ErrorCard(message: String, remedy: String?, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (!remedy.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "対処: $remedy",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (action != null) {
                Spacer(Modifier.height(12.dp))
                action()
            }
        }
    }
}

/**
 * 機能が使えない理由を示す表示。
 *
 * 未対応機能はクラッシュさせず、なぜ使えないのかを示して無効化する。
 */
@Composable
fun UnavailableNotice(reason: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** 通信中を示す表示。何も起きていないように見える時間を作らない。 */
@Composable
fun BusyIndicator(visible: Boolean, label: String, modifier: Modifier = Modifier) {
    if (!visible) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * 工学表記を受け付ける数値入力。
 *
 * `1.5n` や `2.5mV` のような入力を解釈し、範囲外は入力段階で知らせる。
 */
@Composable
fun EngineeringValueField(
    label: String,
    text: String,
    unit: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Double>? = null,
    enabled: Boolean = true,
) {
    val parsed = EngineeringUnits.parse(text, unit)
    val outOfRange = parsed != null && range != null && parsed !in range
    val invalid = text.isNotBlank() && parsed == null

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(label) },
            suffix = { Text(unit) },
            enabled = enabled,
            isError = invalid || outOfRange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        when {
            invalid -> Text(
                text = "数値として解釈できません（例: 1.5n, 2.5m, 500k）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            outOfRange && range != null -> Text(
                text = "範囲外です（${EngineeringUnits.formatToString(range.start, unit)} 〜 " +
                    "${EngineeringUnits.formatToString(range.endInclusive, unit)}）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )

            parsed != null -> Text(
                text = "= ${EngineeringUnits.formatToString(parsed, unit)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
