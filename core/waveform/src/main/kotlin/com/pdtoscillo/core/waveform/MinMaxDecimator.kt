package com.pdtoscillo.core.waveform

/**
 * 描画用に間引いた波形。
 *
 * 1 つの横ピクセルにつき最小値と最大値の対を持つ。単純な等間隔サンプリングだと
 * ピークやグリッチが消えるため、範囲内の min / max を必ず残す。
 */
class DecimatedWaveform(
    val times: DoubleArray,
    val minValues: DoubleArray,
    val maxValues: DoubleArray,
    /** 元の点数。間引き率の表示に使う。 */
    val originalPointCount: Int,
) {
    val pointCount: Int get() = times.size
    val isDecimated: Boolean get() = originalPointCount > pointCount

    val minValue: Double get() = minValues.minOrNull() ?: 0.0
    val maxValue: Double get() = maxValues.maxOrNull() ?: 0.0
}

/**
 * 画面の横ピクセル数に合わせて波形を間引く。
 *
 * 10 M 点を全点描画すると描画が止まる。かといって等間隔に間引くと、
 * 1 点だけのグリッチが表示から消え、見えない不具合になる。
 * そこで各ピクセル区間の最小値と最大値を残す。
 *
 * **元データは変更しない。** 間引くのは表示用の副本だけで、
 * 保存・測定・エクスポートは常に元データを使う。
 */
object MinMaxDecimator {

    /**
     * @param targetPointCount 出力する区間数。通常は描画領域の横ピクセル数。
     */
    fun decimate(times: DoubleArray, values: DoubleArray, targetPointCount: Int): DecimatedWaveform {
        require(times.size == values.size) { "時間と値の点数が一致しません" }
        if (values.isEmpty()) {
            return DecimatedWaveform(DoubleArray(0), DoubleArray(0), DoubleArray(0), 0)
        }

        val buckets = targetPointCount.coerceAtLeast(1)
        // 点数が目標以下なら間引かない。間引くと逆に情報が減る。
        if (values.size <= buckets) {
            return DecimatedWaveform(times.copyOf(), values.copyOf(), values.copyOf(), values.size)
        }

        val outTimes = DoubleArray(buckets)
        val outMin = DoubleArray(buckets)
        val outMax = DoubleArray(buckets)

        for (bucket in 0 until buckets) {
            val start = (bucket.toLong() * values.size / buckets).toInt()
            val end = ((bucket + 1).toLong() * values.size / buckets).toInt().coerceAtLeast(start + 1)
            var minimum = values[start]
            var maximum = values[start]
            for (index in start until end.coerceAtMost(values.size)) {
                val value = values[index]
                if (value < minimum) minimum = value
                if (value > maximum) maximum = value
            }
            outTimes[bucket] = times[start]
            outMin[bucket] = minimum
            outMax[bucket] = maximum
        }
        return DecimatedWaveform(outTimes, outMin, outMax, values.size)
    }

    /** アナログ波形を表示用に間引く。 */
    fun decimate(waveform: AnalogWaveform, targetPointCount: Int): DecimatedWaveform =
        decimate(waveform.times, waveform.volts, targetPointCount)

    /**
     * 時間範囲を絞ってから間引く。ズーム時に使う。
     *
     * 表示範囲外の点まで間引き対象にすると、拡大しても解像度が上がらない。
     */
    fun decimateRange(
        times: DoubleArray,
        values: DoubleArray,
        startTime: Double,
        endTime: Double,
        targetPointCount: Int,
    ): DecimatedWaveform {
        require(times.size == values.size) { "時間と値の点数が一致しません" }
        if (values.isEmpty()) {
            return DecimatedWaveform(DoubleArray(0), DoubleArray(0), DoubleArray(0), 0)
        }

        val startIndex = lowerBound(times, startTime)
        val endIndex = upperBound(times, endTime)
        if (endIndex <= startIndex) {
            return DecimatedWaveform(DoubleArray(0), DoubleArray(0), DoubleArray(0), values.size)
        }

        val slicedTimes = times.copyOfRange(startIndex, endIndex)
        val slicedValues = values.copyOfRange(startIndex, endIndex)
        return decimate(slicedTimes, slicedValues, targetPointCount)
    }

    /** [target] 以上となる最初の添字。 */
    private fun lowerBound(sorted: DoubleArray, target: Double): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val mid = (low + high) / 2
            if (sorted[mid] < target) low = mid + 1 else high = mid
        }
        return low.coerceIn(0, sorted.size)
    }

    /** [target] を超える最初の添字。 */
    private fun upperBound(sorted: DoubleArray, target: Double): Int {
        var low = 0
        var high = sorted.size
        while (low < high) {
            val mid = (low + high) / 2
            if (sorted[mid] <= target) low = mid + 1 else high = mid
        }
        return low.coerceIn(0, sorted.size)
    }
}
