package com.pdtoscillo.simulator

import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 疑似波形の生成。
 *
 * 実機の `CURVe?` と同じ「量子化された生データ」を返す。電圧へ戻すのは受信側の仕事なので、
 * ここでは意図的に生データのままにしておく（受信側のスケーリング実装を検証するため）。
 */
object WaveformFactory {
    /** 生成した生データとスケーリング係数。 */
    data class Generated(
        val raw: IntArray,
        val yMultiplier: Double,
        val yOffset: Double,
        val yZero: Double,
        val xIncrement: Double,
        val xZero: Double,
        val pointOffset: Int,
    ) {
        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }

    /**
     * @param bytesPerPoint 1 点あたりのバイト数（1 または 2）。
     * @param signed 符号付き（RI）なら true、符号なし（RP）なら false。
     */
    @Suppress("LongParameterList")
    fun generate(
        shape: WaveformShape,
        pointCount: Int,
        bytesPerPoint: Int,
        signed: Boolean,
        secondsPerPoint: Double = DEFAULT_SECONDS_PER_POINT,
        voltsPerLevel: Double = DEFAULT_VOLTS_PER_LEVEL,
        seed: Int = 0,
    ): Generated {
        val bits = bytesPerPoint * BITS_PER_BYTE
        val fullScale = (1 shl (bits - 1)) - 1
        val center = if (signed) 0 else (1 shl (bits - 1))
        val amplitude = (fullScale * AMPLITUDE_RATIO).roundToInt()
        val random = Random(seed)

        val raw = IntArray(pointCount) { index ->
            val phase = TWO_PI * CYCLES_IN_RECORD * index / pointCount
            val normalized = when (shape) {
                WaveformShape.SINE -> sin(phase)
                WaveformShape.SQUARE -> if (sin(phase) >= 0.0) 1.0 else -1.0
                WaveformShape.NOISE -> random.nextDouble(-1.0, 1.0)
                WaveformShape.PULSE -> if (index in pointCount / 3 until pointCount / 3 + pointCount / 10) 1.0 else -1.0
            }
            val value = center + (normalized * amplitude).roundToInt()
            // 符号なしの場合に下限を割らないよう丸める。
            if (signed) value.coerceIn(-fullScale - 1, fullScale) else value.coerceIn(0, (1 shl bits) - 1)
        }

        return Generated(
            raw = raw,
            yMultiplier = voltsPerLevel,
            yOffset = if (signed) 0.0 else center.toDouble(),
            yZero = 0.0,
            xIncrement = secondsPerPoint,
            xZero = -secondsPerPoint * pointCount / 2,
            pointOffset = pointCount / 2,
        )
    }

    /**
     * デジタルチャンネル 1 本の波形。0 / 1 のみ。
     * アナログ波形と同じ型で返さないよう、呼び出し側で用途を分けること。
     */
    fun generateDigitalBit(pointCount: Int, bitIndex: Int): IntArray {
        val period = (pointCount / (CYCLES_IN_RECORD * (bitIndex + 1))).coerceAtLeast(2)
        return IntArray(pointCount) { index -> if ((index / (period / 2)) % 2 == 0) 1 else 0 }
    }

    /**
     * RF 周波数領域トレース。マニュアル記載どおり 4 バイト浮動小数で返す想定のため、
     * ここでは dBm 相当の Float 値を生成する。
     */
    fun generateSpectrum(pointCount: Int, seed: Int = 0): FloatArray {
        val random = Random(seed)
        val peakIndex = pointCount / 3
        return FloatArray(pointCount) { index ->
            val distance = kotlin.math.abs(index - peakIndex).toDouble() / pointCount
            val base = SPECTRUM_NOISE_FLOOR_DBM + random.nextDouble(-2.0, 2.0)
            val peak = SPECTRUM_PEAK_DBM * kotlin.math.exp(-distance * SPECTRUM_PEAK_SHARPNESS)
            (base + peak).toFloat()
        }
    }

    private const val BITS_PER_BYTE = 8
    private const val TWO_PI = 2.0 * PI
    private const val CYCLES_IN_RECORD = 3
    private const val AMPLITUDE_RATIO = 0.8
    private const val DEFAULT_SECONDS_PER_POINT = 4.0e-9
    private const val DEFAULT_VOLTS_PER_LEVEL = 4.0e-3
    private const val SPECTRUM_NOISE_FLOOR_DBM = -90.0
    private const val SPECTRUM_PEAK_DBM = 70.0
    private const val SPECTRUM_PEAK_SHARPNESS = 60.0
}
