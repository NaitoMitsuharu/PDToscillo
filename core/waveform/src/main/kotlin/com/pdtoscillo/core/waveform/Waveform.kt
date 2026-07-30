package com.pdtoscillo.core.waveform

import com.pdtoscillo.core.model.WaveformSource

/**
 * 取得した波形。
 *
 * アナログ電圧、デジタル論理、RF 周波数領域を**別の型**として扱う。
 * 同じ型にまとめると、デジタル波形や dBm 値を電圧としてスケーリングする誤りが起きる。
 */
sealed interface Waveform {
    val source: WaveformSource
    val preamble: WaveformPreamble
    val capturedAtEpochMillis: Long
    val pointCount: Int
}

/**
 * 電圧対時間のアナログ波形。
 *
 * 元データ（[rawValues]）を保持したまま、表示用の間引きは別に行う。
 * 間引いた結果で上書きすると、保存や測定でピークが失われる。
 */
class AnalogWaveform(
    override val source: WaveformSource,
    override val preamble: WaveformPreamble,
    override val capturedAtEpochMillis: Long,
    /** 量子化された生の値。 */
    val rawValues: IntArray,
    /** 秒。`Xn = XZEro + XINcr (n - PT_Off)` で算出済み。 */
    val times: DoubleArray,
    /** ボルト。`Yn = YZEro + YMUlt (yn - YOFf)` で算出済み。 */
    val volts: DoubleArray,
) : Waveform {
    override val pointCount: Int get() = volts.size

    val minVolts: Double get() = volts.minOrNull() ?: 0.0
    val maxVolts: Double get() = volts.maxOrNull() ?: 0.0
    val startTime: Double get() = times.firstOrNull() ?: 0.0
    val endTime: Double get() = times.lastOrNull() ?: 0.0

    /** 指定時刻に最も近い点の添字。カーソル操作で使う。 */
    fun indexOfTime(time: Double): Int {
        if (times.isEmpty()) return 0
        val increment = preamble.xIncrement ?: return 0
        if (increment == 0.0) return 0
        val index = ((time - times.first()) / increment).toInt()
        return index.coerceIn(0, times.lastIndex)
    }
}

/**
 * Peak Detect / Envelope で取得した波形。
 *
 * 1 つの時刻に最大値と最小値の対を持つ。通常の波形として描画すると
 * 時間軸が 2 倍にずれるため、型で区別する。
 */
class EnvelopeWaveform(
    override val source: WaveformSource,
    override val preamble: WaveformPreamble,
    override val capturedAtEpochMillis: Long,
    val times: DoubleArray,
    val minVoltsPerPoint: DoubleArray,
    val maxVoltsPerPoint: DoubleArray,
) : Waveform {
    override val pointCount: Int get() = times.size

    val minVolts: Double get() = minVoltsPerPoint.minOrNull() ?: 0.0
    val maxVolts: Double get() = maxVoltsPerPoint.maxOrNull() ?: 0.0
}

/** デジタルチャンネル 1 本の論理波形。 */
class DigitalWaveform(
    override val source: WaveformSource,
    override val preamble: WaveformPreamble,
    override val capturedAtEpochMillis: Long,
    val times: DoubleArray,
    /** 0 または 1。 */
    val levels: IntArray,
) : Waveform {
    override val pointCount: Int get() = levels.size
}

/**
 * 全デジタルチャンネルをまとめた Digital Collection。
 *
 * 1 点あたり 4 または 8 バイトで、各ビットがチャンネルに対応する。
 */
class DigitalCollectionWaveform(
    override val source: WaveformSource,
    override val preamble: WaveformPreamble,
    override val capturedAtEpochMillis: Long,
    val times: DoubleArray,
    /** 各点のビットパターン。 */
    val bitPatterns: LongArray,
) : Waveform {
    override val pointCount: Int get() = bitPatterns.size

    /** 指定ビットだけを取り出す。 */
    fun extractBit(bitIndex: Int): IntArray = IntArray(bitPatterns.size) { index -> ((bitPatterns[index] shr bitIndex) and 1L).toInt() }
}

/**
 * RF 周波数領域トレース（スペクトラム）。
 *
 * 縦軸は電圧ではなく dBm などの単位。アナログ波形と同じ扱いをしてはならない。
 */
class SpectrumTrace(
    override val source: WaveformSource,
    override val preamble: WaveformPreamble,
    override val capturedAtEpochMillis: Long,
    /** ヘルツ。 */
    val frequencies: DoubleArray,
    /** プリアンブルの `YUNIT` が示す単位の値（通常 dBm）。 */
    val amplitudes: DoubleArray,
) : Waveform {
    override val pointCount: Int get() = amplitudes.size

    val unit: String get() = preamble.yUnit?.trim('"') ?: "dBm"
}
