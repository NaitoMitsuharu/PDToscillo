package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.waveform.BinaryFormat
import com.pdtoscillo.core.waveform.ByteOrder
import com.pdtoscillo.core.waveform.WaveformEncoding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WfmOutpreParserTest {

    /** マニュアル記載の応答例（ヘッダあり）。 */
    private val headeredResponse = ":WFMOUTPRE:BYT_NR 2;BIT_NR 16;ENCDG ASCII;BN_FMT RI;BYT_OR MSB;" +
        """WFID "Ch1, DC coupling, 100.0mV/div, 4.000us/div, 10000 points, Sample mode";""" +
        "NR_PT 10000;PT_FMT Y;XUNIT \"s\";XINCR 4.0000E-9;XZERO -20.0000E-6;PT_OFF 0;" +
        "YUNIT \"V\";YMULT 15.6250E-6;YOFF 6.4000E+3;YZERO 0.0000"

    /** 同じ内容をヘッダなしで並べたもの。 */
    private val positionalResponse = "1;8;BINARY;RI;MSB;" +
        """"Ch1, DC coupling, 100.0mV/div, 4.000us/div, 10000 points, Sample mode";""" +
        "10000;Y;\"s\";4.0000E-9;-20.0000E-6;0;\"V\";15.6250E-6;0.0000;0.0000"

    @Test
    fun `ヘッダ付き応答を解析する`() {
        val preamble = WfmOutpreParser.parse(headeredResponse)

        assertEquals(2, preamble.bytesPerPoint)
        assertEquals(16, preamble.bitsPerPoint)
        assertEquals(WaveformEncoding.ASCII, preamble.encoding)
        assertEquals(BinaryFormat.SIGNED_INTEGER, preamble.binaryFormat)
        assertEquals(ByteOrder.MSB_FIRST, preamble.byteOrder)
        assertEquals(10_000, preamble.pointCount)
        assertEquals("Y", preamble.pointFormat)
        assertEquals("s", preamble.xUnit)
        assertEquals(4.0e-9, preamble.xIncrement!!, 1e-21)
        assertEquals(-20.0e-6, preamble.xZero!!, 1e-18)
        assertEquals(0, preamble.pointOffset)
        assertEquals("V", preamble.yUnit)
        assertEquals(15.625e-6, preamble.yMultiplier!!, 1e-18)
        assertEquals(6400.0, preamble.yOffset!!, 1e-9)
        assertEquals(0.0, preamble.yZero!!, 1e-12)
    }

    @Test
    fun `WFID のカンマで壊れない`() {
        val preamble = WfmOutpreParser.parse(headeredResponse)

        // WFID にはカンマが含まれる。ここで分割してしまうと以降の値が全部ずれる。
        assertTrue(preamble.waveformId!!.contains("DC coupling"))
        assertTrue(preamble.waveformId!!.contains("10000 points"))
        assertEquals(10_000, preamble.pointCount)
    }

    @Test
    fun `ヘッダなし応答を位置で解析する`() {
        val preamble = WfmOutpreParser.parse(positionalResponse)

        assertEquals(1, preamble.bytesPerPoint)
        assertEquals(8, preamble.bitsPerPoint)
        assertEquals(WaveformEncoding.BINARY, preamble.encoding)
        assertEquals(BinaryFormat.SIGNED_INTEGER, preamble.binaryFormat)
        assertEquals(ByteOrder.MSB_FIRST, preamble.byteOrder)
        assertEquals(10_000, preamble.pointCount)
        assertEquals(4.0e-9, preamble.xIncrement!!, 1e-21)
        assertEquals(15.625e-6, preamble.yMultiplier!!, 1e-18)
    }

    @Test
    fun `Little Endian を判別する`() {
        val response = headeredResponse.replace("BYT_OR MSB", "BYT_OR LSB")

        assertEquals(ByteOrder.LSB_FIRST, WfmOutpreParser.parse(response).byteOrder)
    }

    @Test
    fun `符号なしと浮動小数を判別する`() {
        assertEquals(
            BinaryFormat.UNSIGNED_INTEGER,
            WfmOutpreParser.parse(headeredResponse.replace("BN_FMT RI", "BN_FMT RP")).binaryFormat,
        )
        assertEquals(
            BinaryFormat.FLOATING_POINT,
            WfmOutpreParser.parse(headeredResponse.replace("BN_FMT RI", "BN_FMT FP")).binaryFormat,
        )
    }

    @Test
    fun `Envelope 形式を判別する`() {
        val response = headeredResponse.replace("PT_FMT Y", "PT_FMT ENV")

        assertTrue(WfmOutpreParser.parse(response).isEnvelope)
        assertFalse(WfmOutpreParser.parse(headeredResponse).isEnvelope)
    }

    @Test
    fun `スケーリングに必要な値が揃っているか判定できる`() {
        val complete = WfmOutpreParser.parse(headeredResponse)

        assertTrue(complete.hasVerticalScaling)
        assertTrue(complete.hasHorizontalScaling)
        assertTrue(complete.missingFields().isEmpty())
    }

    @Test
    fun `波形が非表示のときの短い応答を検出する`() {
        // 波形が表示されていないと転送パラメータだけが返る。
        val partial = ":WFMOUTPRE:BYT_NR 1;BIT_NR 8;ENCDG BINARY;BN_FMT RI;BYT_OR MSB"

        val preamble = WfmOutpreParser.parse(partial)

        assertFalse(preamble.hasVerticalScaling)
        assertFalse(preamble.hasHorizontalScaling)
        val missing = preamble.missingFields()
        assertTrue(missing.contains("YMULT"))
        assertTrue(missing.contains("XINCR"))
    }

    @Test
    fun `空の応答でも例外にしない`() {
        val preamble = WfmOutpreParser.parse("")

        assertTrue(preamble.missingFields().isNotEmpty())
    }

    @Test
    fun `生応答を保持する`() {
        assertEquals(headeredResponse, WfmOutpreParser.parse(headeredResponse).raw)
    }
}
