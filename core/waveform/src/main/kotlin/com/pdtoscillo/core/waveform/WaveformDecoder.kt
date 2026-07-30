package com.pdtoscillo.core.waveform

import com.pdtoscillo.core.model.WaveformDomain
import com.pdtoscillo.core.model.WaveformSource

/** 波形のデコードに失敗した理由。 */
class WaveformDecodeException(message: String) : Exception(message)

/**
 * `CURVe?` の生データを波形へ変換する。
 *
 * 対応する形式:
 * - 8 / 16 / 32 / 64 bit
 * - 符号付き（RI）/ 符号なし（RP）/ 浮動小数（FP）
 * - MSB first / LSB first
 * - ASCII（カンマ区切り）
 *
 * スケーリングはマニュアル記載の式に従う。
 * ```text
 * Xn = XZEro + XINcr (n - PT_Off)
 * Yn = YZEro + YMUlt (yn - YOFf)
 * ```
 *
 * 領域（アナログ / デジタル / RF）を [WaveformSource.domain] で判断し、別の型を返す。
 * ここで型を分けることで、デジタル値や dBm を電圧として扱う誤りを防ぐ。
 */
object WaveformDecoder {

    /**
     * バイナリ応答を波形へ変換する。
     *
     * @param payload IEEE 488.2 ブロックから取り出した本体（ヘッダを含まない）。
     */
    fun decodeBinary(
        source: WaveformSource,
        preamble: WaveformPreamble,
        payload: ByteArray,
        capturedAtEpochMillis: Long = System.currentTimeMillis(),
    ): Waveform {
        val missing = preamble.missingFields()
        if (missing.isNotEmpty()) {
            throw WaveformDecodeException(
                "プリアンブルに必要な項目がありません: ${missing.joinToString()}。" +
                    "WFMOutpre? の応答を確認してください。",
            )
        }

        val bytesPerPoint = preamble.bytesPerPoint!!
        val format = preamble.binaryFormat ?: BinaryFormat.SIGNED_INTEGER
        val order = preamble.byteOrder ?: ByteOrder.MSB_FIRST

        if (bytesPerPoint !in SUPPORTED_BYTE_WIDTHS) {
            throw WaveformDecodeException("1 点あたり $bytesPerPoint バイトには対応していません。")
        }
        if (payload.size % bytesPerPoint != 0) {
            throw WaveformDecodeException(
                "データ長 ${payload.size} バイトが 1 点あたり $bytesPerPoint バイトで割り切れません。",
            )
        }

        return when (source.domain) {
            WaveformDomain.RF_FREQUENCY -> decodeSpectrum(source, preamble, payload, bytesPerPoint, order, format)
            WaveformDomain.DIGITAL_COLLECTION ->
                decodeDigitalCollection(source, preamble, payload, bytesPerPoint, order, capturedAtEpochMillis)

            WaveformDomain.DIGITAL -> {
                val values = readIntegers(payload, bytesPerPoint, order, format)
                DigitalWaveform(
                    source = source,
                    preamble = preamble,
                    capturedAtEpochMillis = capturedAtEpochMillis,
                    times = buildTimes(preamble, values.size),
                    // デジタルは電圧へ変換しない。0 以外は 1 とみなす。
                    levels = IntArray(values.size) { if (values[it] != 0) 1 else 0 },
                )
            }

            WaveformDomain.ANALOG_TIME, WaveformDomain.RF_TIME -> {
                val values = readIntegers(payload, bytesPerPoint, order, format)
                if (preamble.isEnvelope) {
                    buildEnvelope(source, preamble, values, capturedAtEpochMillis)
                } else {
                    buildAnalog(source, preamble, values, capturedAtEpochMillis)
                }
            }
        }.let { it }
    }

    /** ASCII 応答（カンマ区切りの数値列）を波形へ変換する。 */
    fun decodeAscii(
        source: WaveformSource,
        preamble: WaveformPreamble,
        response: String,
        capturedAtEpochMillis: Long = System.currentTimeMillis(),
    ): Waveform {
        val values = response
            .substringAfter(' ', missingDelimiterValue = response)
            .split(',')
            .mapNotNull { it.trim().takeIf(String::isNotEmpty)?.toDoubleOrNull() }
        if (values.isEmpty()) {
            throw WaveformDecodeException("ASCII 応答から数値を読み取れませんでした。")
        }

        return when (source.domain) {
            WaveformDomain.RF_FREQUENCY -> SpectrumTrace(
                source = source,
                preamble = preamble,
                capturedAtEpochMillis = capturedAtEpochMillis,
                frequencies = buildTimes(preamble, values.size),
                amplitudes = values.toDoubleArray(),
            )

            WaveformDomain.DIGITAL -> DigitalWaveform(
                source = source,
                preamble = preamble,
                capturedAtEpochMillis = capturedAtEpochMillis,
                times = buildTimes(preamble, values.size),
                levels = IntArray(values.size) { if (values[it] != 0.0) 1 else 0 },
            )

            else -> buildAnalog(
                source = source,
                preamble = preamble,
                rawValues = IntArray(values.size) { values[it].toInt() },
                capturedAtEpochMillis = capturedAtEpochMillis,
            )
        }
    }

    private fun buildAnalog(
        source: WaveformSource,
        preamble: WaveformPreamble,
        rawValues: IntArray,
        capturedAtEpochMillis: Long,
    ): AnalogWaveform {
        val yMultiplier = preamble.yMultiplier!!
        val yOffset = preamble.yOffset!!
        val yZero = preamble.yZero!!

        val volts = DoubleArray(rawValues.size) { index ->
            // Yn = YZEro + YMUlt (yn - YOFf)
            yZero + yMultiplier * (rawValues[index] - yOffset)
        }
        return AnalogWaveform(
            source = source,
            preamble = preamble,
            capturedAtEpochMillis = capturedAtEpochMillis,
            rawValues = rawValues,
            times = buildTimes(preamble, rawValues.size),
            volts = volts,
        )
    }

    /**
     * Envelope 形式のデコード。
     *
     * 最大・最小が交互に並ぶ。点数はデータ数の半分になる。
     */
    private fun buildEnvelope(
        source: WaveformSource,
        preamble: WaveformPreamble,
        rawValues: IntArray,
        capturedAtEpochMillis: Long,
    ): EnvelopeWaveform {
        val yMultiplier = preamble.yMultiplier!!
        val yOffset = preamble.yOffset!!
        val yZero = preamble.yZero!!
        val pairCount = rawValues.size / 2

        val minima = DoubleArray(pairCount)
        val maxima = DoubleArray(pairCount)
        for (index in 0 until pairCount) {
            val first = yZero + yMultiplier * (rawValues[index * 2] - yOffset)
            val second = yZero + yMultiplier * (rawValues[index * 2 + 1] - yOffset)
            minima[index] = minOf(first, second)
            maxima[index] = maxOf(first, second)
        }
        return EnvelopeWaveform(
            source = source,
            preamble = preamble,
            capturedAtEpochMillis = capturedAtEpochMillis,
            times = buildTimes(preamble, pairCount),
            minVoltsPerPoint = minima,
            maxVoltsPerPoint = maxima,
        )
    }

    private fun decodeSpectrum(
        source: WaveformSource,
        preamble: WaveformPreamble,
        payload: ByteArray,
        bytesPerPoint: Int,
        order: ByteOrder,
        format: BinaryFormat,
    ): SpectrumTrace {
        // RF 周波数領域は 4 バイト浮動小数で返る。整数として読むと桁違いの値になる。
        val amplitudes = if (format == BinaryFormat.FLOATING_POINT) {
            readFloats(payload, bytesPerPoint, order)
        } else {
            val values = readIntegers(payload, bytesPerPoint, order, format)
            val yMultiplier = preamble.yMultiplier ?: 1.0
            val yOffset = preamble.yOffset ?: 0.0
            val yZero = preamble.yZero ?: 0.0
            DoubleArray(values.size) { yZero + yMultiplier * (values[it] - yOffset) }
        }
        return SpectrumTrace(
            source = source,
            preamble = preamble,
            capturedAtEpochMillis = System.currentTimeMillis(),
            frequencies = buildTimes(preamble, amplitudes.size),
            amplitudes = amplitudes,
        )
    }

    private fun decodeDigitalCollection(
        source: WaveformSource,
        preamble: WaveformPreamble,
        payload: ByteArray,
        bytesPerPoint: Int,
        order: ByteOrder,
        capturedAtEpochMillis: Long,
    ): DigitalCollectionWaveform {
        val pointCount = payload.size / bytesPerPoint
        val patterns = LongArray(pointCount)
        for (index in 0 until pointCount) {
            var value = 0L
            for (byteIndex in 0 until bytesPerPoint) {
                val position = index * bytesPerPoint + byteIndex
                val byte = payload[position].toLong() and BYTE_MASK
                value = if (order.isBigEndian) {
                    (value shl BITS_PER_BYTE) or byte
                } else {
                    value or (byte shl (byteIndex * BITS_PER_BYTE))
                }
            }
            patterns[index] = value
        }
        return DigitalCollectionWaveform(
            source = source,
            preamble = preamble,
            capturedAtEpochMillis = capturedAtEpochMillis,
            times = buildTimes(preamble, pointCount),
            bitPatterns = patterns,
        )
    }

    /** `Xn = XZEro + XINcr (n - PT_Off)` */
    private fun buildTimes(preamble: WaveformPreamble, count: Int): DoubleArray {
        val increment = preamble.xIncrement ?: 1.0
        val zero = preamble.xZero ?: 0.0
        val offset = preamble.pointOffset ?: 0
        return DoubleArray(count) { index -> zero + increment * (index - offset) }
    }

    /** 指定バイト数・バイト順・符号で整数列を読む。 */
    internal fun readIntegers(payload: ByteArray, bytesPerPoint: Int, order: ByteOrder, format: BinaryFormat): IntArray {
        val count = payload.size / bytesPerPoint
        val result = IntArray(count)
        val signed = format == BinaryFormat.SIGNED_INTEGER

        for (index in 0 until count) {
            var value = 0L
            for (byteIndex in 0 until bytesPerPoint) {
                val position = index * bytesPerPoint + byteIndex
                val byte = payload[position].toLong() and BYTE_MASK
                value = if (order.isBigEndian) {
                    (value shl BITS_PER_BYTE) or byte
                } else {
                    value or (byte shl (byteIndex * BITS_PER_BYTE))
                }
            }
            result[index] = if (signed) signExtend(value, bytesPerPoint).toInt() else value.toInt()
        }
        return result
    }

    /**
     * 符号拡張。
     *
     * 8 bit の 0xFF は符号付きなら -1。バイト数から最上位ビットを見て符号を復元する。
     */
    private fun signExtend(value: Long, bytesPerPoint: Int): Long {
        val bits = bytesPerPoint * BITS_PER_BYTE
        if (bits >= Long.SIZE_BITS) return value
        val signBit = 1L shl (bits - 1)
        return if (value and signBit != 0L) value - (1L shl bits) else value
    }

    /** 4 バイト / 8 バイトの浮動小数列を読む。 */
    internal fun readFloats(payload: ByteArray, bytesPerPoint: Int, order: ByteOrder): DoubleArray {
        val count = payload.size / bytesPerPoint
        val result = DoubleArray(count)
        for (index in 0 until count) {
            var bits = 0L
            for (byteIndex in 0 until bytesPerPoint) {
                val position = index * bytesPerPoint + byteIndex
                val byte = payload[position].toLong() and BYTE_MASK
                bits = if (order.isBigEndian) {
                    (bits shl BITS_PER_BYTE) or byte
                } else {
                    bits or (byte shl (byteIndex * BITS_PER_BYTE))
                }
            }
            result[index] = when (bytesPerPoint) {
                Float.SIZE_BYTES -> Float.fromBits(bits.toInt()).toDouble()
                Double.SIZE_BYTES -> Double.fromBits(bits)
                else -> throw WaveformDecodeException("浮動小数は 4 または 8 バイトのみ対応します。")
            }
        }
        return result
    }

    private val SUPPORTED_BYTE_WIDTHS = setOf(1, 2, 4, 8)
    private const val BYTE_MASK = 0xFFL
    private const val BITS_PER_BYTE = 8
}
