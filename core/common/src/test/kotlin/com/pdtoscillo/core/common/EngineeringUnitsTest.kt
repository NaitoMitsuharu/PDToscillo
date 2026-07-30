package com.pdtoscillo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineeringUnitsTest {

    @Test
    fun `分量側の接頭辞へ変換する`() {
        assertEquals("1.5 ns", EngineeringUnits.formatToString(1.5e-9, "s"))
        assertEquals("2.5 mV", EngineeringUnits.formatToString(2.5e-3, "V"))
        assertEquals("4 µs", EngineeringUnits.formatToString(4.0e-6, "s"))
        assertEquals("100 ps", EngineeringUnits.formatToString(100.0e-12, "s"))
    }

    @Test
    fun `倍量側の接頭辞へ変換する`() {
        assertEquals("1.2 MHz", EngineeringUnits.formatToString(1.2e6, "Hz"))
        assertEquals("2.5 GHz", EngineeringUnits.formatToString(2.5e9, "Hz"))
        assertEquals("1 kHz", EngineeringUnits.formatToString(1000.0, "Hz"))
    }

    @Test
    fun `接頭辞が不要な範囲はそのまま表示する`() {
        assertEquals("3.3 V", EngineeringUnits.formatToString(3.3, "V"))
        assertEquals("999 V", EngineeringUnits.formatToString(999.0, "V"))
    }

    @Test
    fun `ゼロと非数を安全に扱う`() {
        assertEquals("0 V", EngineeringUnits.formatToString(0.0, "V"))
        assertEquals("--- V", EngineeringUnits.formatToString(Double.NaN, "V"))
        assertEquals("∞ V", EngineeringUnits.formatToString(Double.POSITIVE_INFINITY, "V"))
    }

    @Test
    fun `負の値も接頭辞へ変換する`() {
        assertEquals("-20 µs", EngineeringUnits.formatToString(-20.0e-6, "s"))
        assertEquals("-1.5 mV", EngineeringUnits.formatToString(-1.5e-3, "V"))
    }

    @Test
    fun `数値と単位を分離して返す`() {
        val formatted = EngineeringUnits.format(1.5e-9, "s")
        assertEquals("1.5", formatted.value)
        assertEquals("ns", formatted.unit)
    }

    @Test
    fun `工学表記の入力を数値へ戻す`() {
        assertEquals(1.5e-9, EngineeringUnits.parse("1.5n")!!, 1e-21)
        assertEquals(1.5e-9, EngineeringUnits.parse("1.5ns")!!, 1e-21)
        assertEquals(2.5e-3, EngineeringUnits.parse("2.5mV")!!, 1e-12)
        assertEquals(1000.0, EngineeringUnits.parse("1k")!!, 1e-9)
        assertEquals(1.0e6, EngineeringUnits.parse("1M")!!, 1e-3)
        assertEquals(3.3, EngineeringUnits.parse("3.3")!!, 1e-9)
        assertEquals(-3.3, EngineeringUnits.parse("-3.3")!!, 1e-9)
    }

    @Test
    fun `指数表記の e は接頭辞と混同しない`() {
        assertEquals(1.0e-6, EngineeringUnits.parse("1e-6")!!, 1e-18)
        assertEquals(4.0e-9, EngineeringUnits.parse("4.0000E-9")!!, 1e-21)
    }

    @Test
    fun `マイクロは u でも受け付ける`() {
        assertEquals(4.0e-6, EngineeringUnits.parse("4u")!!, 1e-18)
        assertEquals(4.0e-6, EngineeringUnits.parse("4µs")!!, 1e-18)
    }

    @Test
    fun `解釈できない入力は null を返す`() {
        assertNull(EngineeringUnits.parse(""))
        assertNull(EngineeringUnits.parse("abc"))
        assertNull(EngineeringUnits.parse("V"))
    }

    @Test
    fun `単位が分かっていれば食い違う入力を弾く`() {
        // 単位に V を期待しているのに Hz が来た場合は解釈しない。
        assertNull(EngineeringUnits.parse("1.5Hz", unit = "V"))
    }

    @Test
    fun `デルタ時間から周波数へ換算する`() {
        assertEquals(1000.0, EngineeringUnits.deltaTimeToFrequency(1.0e-3)!!, 1e-6)
        assertNull(EngineeringUnits.deltaTimeToFrequency(0.0))
    }

    @Test
    fun `バイト数を読める形にする`() {
        assertEquals("512 B", EngineeringUnits.formatBytes(512))
        assertTrue(EngineeringUnits.formatBytes(10_000).endsWith("KiB"))
        assertTrue(EngineeringUnits.formatBytes(5_000_000).endsWith("MiB"))
    }
}
