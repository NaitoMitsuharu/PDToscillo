package com.pdtoscillo.core.waveform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MinMaxDecimatorTest {

    @Test
    fun `点数が目標以下なら間引かない`() {
        val times = doubleArrayOf(0.0, 1.0, 2.0)
        val values = doubleArrayOf(1.0, 2.0, 3.0)

        val result = MinMaxDecimator.decimate(times, values, targetPointCount = 10)

        assertEquals(3, result.pointCount)
        assertFalse(result.isDecimated)
    }

    @Test
    fun `目標の区間数まで間引く`() {
        val values = DoubleArray(1000) { it.toDouble() }
        val times = DoubleArray(1000) { it * 1e-9 }

        val result = MinMaxDecimator.decimate(times, values, targetPointCount = 100)

        assertEquals(100, result.pointCount)
        assertTrue(result.isDecimated)
        assertEquals(1000, result.originalPointCount)
    }

    @Test
    fun `1点だけのピークを失わない`() {
        // 等間隔サンプリングだと消えてしまう単発のグリッチ。
        val values = DoubleArray(1000) { 0.0 }
        values[517] = 5.0
        val times = DoubleArray(1000) { it * 1e-9 }

        val result = MinMaxDecimator.decimate(times, values, targetPointCount = 50)

        // 最大値の配列のどこかに 5.0 が残っていなければならない。
        assertEquals(5.0, result.maxValue, 1e-12)
    }

    @Test
    fun `1点だけの負のグリッチも失わない`() {
        val values = DoubleArray(10_000) { 1.0 }
        values[9_999] = -3.0
        val times = DoubleArray(10_000) { it * 1e-9 }

        val result = MinMaxDecimator.decimate(times, values, targetPointCount = 200)

        assertEquals(-3.0, result.minValue, 1e-12)
    }

    @Test
    fun `全体の最大最小が保存される`() {
        val values = DoubleArray(5000) { kotlin.math.sin(it * 0.01) * 3.0 }
        val times = DoubleArray(5000) { it * 1e-9 }

        val result = MinMaxDecimator.decimate(times, values, targetPointCount = 300)

        assertEquals(values.max(), result.maxValue, 1e-12)
        assertEquals(values.min(), result.minValue, 1e-12)
    }

    @Test
    fun `空の入力を安全に扱う`() {
        val result = MinMaxDecimator.decimate(DoubleArray(0), DoubleArray(0), targetPointCount = 100)

        assertEquals(0, result.pointCount)
    }

    @Test
    fun `目標が0以下でも落ちない`() {
        val values = DoubleArray(100) { it.toDouble() }
        val times = DoubleArray(100) { it.toDouble() }

        val result = MinMaxDecimator.decimate(times, values, targetPointCount = 0)

        assertEquals(1, result.pointCount)
    }

    @Test
    fun `表示範囲を絞ってから間引く`() {
        val values = DoubleArray(1000) { it.toDouble() }
        val times = DoubleArray(1000) { it.toDouble() }

        val result = MinMaxDecimator.decimateRange(
            times = times,
            values = values,
            startTime = 100.0,
            endTime = 199.0,
            targetPointCount = 50,
        )

        assertEquals(50, result.pointCount)
        // 範囲外の値が混ざっていないこと。
        assertTrue(result.minValue >= 100.0)
        assertTrue(result.maxValue <= 199.0)
    }

    @Test
    fun `範囲外を指定した場合は空になる`() {
        val values = DoubleArray(100) { it.toDouble() }
        val times = DoubleArray(100) { it.toDouble() }

        val result = MinMaxDecimator.decimateRange(times, values, 1000.0, 2000.0, 50)

        assertEquals(0, result.pointCount)
    }

    @Test
    fun `拡大すると元の解像度に近づく`() {
        val values = DoubleArray(10_000) { it.toDouble() }
        val times = DoubleArray(10_000) { it.toDouble() }

        // 20 点分だけを 50 区間で見る → 間引かれない。
        val zoomed = MinMaxDecimator.decimateRange(times, values, 100.0, 119.0, 50)

        assertEquals(20, zoomed.pointCount)
        assertFalse(zoomed.isDecimated)
    }

    @Test
    fun `1000万点でも実用的な時間で間引ける`() {
        val count = 10_000_000
        val values = DoubleArray(count) { (it % 1000).toDouble() }
        val times = DoubleArray(count) { it * 1e-10 }

        val started = System.currentTimeMillis()
        val result = MinMaxDecimator.decimate(times, values, targetPointCount = 1080)
        val elapsed = System.currentTimeMillis() - started

        assertEquals(1080, result.pointCount)
        // 全点描画は不可能だが、間引き自体は 1 秒以内で終わる必要がある。
        assertTrue("間引きに $elapsed ms かかりました", elapsed < 1_000)
    }
}
