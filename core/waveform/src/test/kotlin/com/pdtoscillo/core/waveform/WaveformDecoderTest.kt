package com.pdtoscillo.core.waveform

import com.pdtoscillo.core.model.WaveformSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WaveformDecoderTest {

    private fun preamble(
        bytesPerPoint: Int = 1,
        binaryFormat: BinaryFormat = BinaryFormat.SIGNED_INTEGER,
        byteOrder: ByteOrder = ByteOrder.MSB_FIRST,
        pointFormat: String = "Y",
        xIncrement: Double = 4.0e-9,
        xZero: Double = -20.0e-6,
        pointOffset: Int = 0,
        yMultiplier: Double = 15.625e-6,
        yOffset: Double = 0.0,
        yZero: Double = 0.0,
    ) = WaveformPreamble(
        bytesPerPoint = bytesPerPoint,
        bitsPerPoint = bytesPerPoint * 8,
        encoding = WaveformEncoding.BINARY,
        binaryFormat = binaryFormat,
        byteOrder = byteOrder,
        pointCount = null,
        pointFormat = pointFormat,
        waveformId = "Ch1",
        xUnit = "s",
        xIncrement = xIncrement,
        xZero = xZero,
        pointOffset = pointOffset,
        yUnit = "V",
        yMultiplier = yMultiplier,
        yOffset = yOffset,
        yZero = yZero,
        raw = "test",
    )

    @Test
    fun `8ビット符号付きをデコードする`() {
        // 0x00=0, 0x7F=127, 0x80=-128, 0xFF=-1
        val payload = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())

        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(yMultiplier = 1.0),
            payload,
        ) as AnalogWaveform

        assertEquals(4, waveform.pointCount)
        assertEquals(0.0, waveform.volts[0], 1e-12)
        assertEquals(127.0, waveform.volts[1], 1e-12)
        assertEquals(-128.0, waveform.volts[2], 1e-12)
        assertEquals(-1.0, waveform.volts[3], 1e-12)
    }

    @Test
    fun `8ビット符号なしをデコードする`() {
        val payload = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())

        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(binaryFormat = BinaryFormat.UNSIGNED_INTEGER, yMultiplier = 1.0),
            payload,
        ) as AnalogWaveform

        assertEquals(0.0, waveform.volts[0], 1e-12)
        assertEquals(127.0, waveform.volts[1], 1e-12)
        assertEquals(128.0, waveform.volts[2], 1e-12)
        assertEquals(255.0, waveform.volts[3], 1e-12)
    }

    @Test
    fun `16ビット Big Endian をデコードする`() {
        // 0x0100 = 256, 0xFF00 = -256（符号付き）
        val payload = byteArrayOf(0x01, 0x00, 0xFF.toByte(), 0x00)

        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(bytesPerPoint = 2, yMultiplier = 1.0),
            payload,
        ) as AnalogWaveform

        assertEquals(2, waveform.pointCount)
        assertEquals(256.0, waveform.volts[0], 1e-12)
        assertEquals(-256.0, waveform.volts[1], 1e-12)
    }

    @Test
    fun `16ビット Little Endian をデコードする`() {
        // LSB first なので 0x00,0x01 が 256 になる。
        val payload = byteArrayOf(0x00, 0x01, 0x00, 0xFF.toByte())

        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(bytesPerPoint = 2, byteOrder = ByteOrder.LSB_FIRST, yMultiplier = 1.0),
            payload,
        ) as AnalogWaveform

        assertEquals(256.0, waveform.volts[0], 1e-12)
        assertEquals(-256.0, waveform.volts[1], 1e-12)
    }

    @Test
    fun `バイト順を取り違えると値が変わることを確認する`() {
        val payload = byteArrayOf(0x01, 0x02)

        val big = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(bytesPerPoint = 2, yMultiplier = 1.0),
            payload,
        ) as AnalogWaveform
        val little = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(bytesPerPoint = 2, byteOrder = ByteOrder.LSB_FIRST, yMultiplier = 1.0),
            payload,
        ) as AnalogWaveform

        assertEquals(258.0, big.volts[0], 1e-12)
        assertEquals(513.0, little.volts[0], 1e-12)
    }

    @Test
    fun `マニュアル記載の式でスケーリングする`() {
        // Yn = YZEro + YMUlt (yn - YOFf)
        val payload = byteArrayOf(100)
        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(yMultiplier = 4.0e-3, yOffset = 20.0, yZero = 0.5),
            payload,
        ) as AnalogWaveform

        // 0.5 + 0.004 * (100 - 20) = 0.5 + 0.32 = 0.82
        assertEquals(0.82, waveform.volts[0], 1e-12)
    }

    @Test
    fun `マニュアル記載の式で時間軸を作る`() {
        // Xn = XZEro + XINcr (n - PT_Off)
        val payload = byteArrayOf(0, 0, 0)
        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(xIncrement = 4.0e-9, xZero = -20.0e-6, pointOffset = 1),
            payload,
        ) as AnalogWaveform

        assertEquals(-20.0e-6 + 4.0e-9 * -1, waveform.times[0], 1e-18)
        assertEquals(-20.0e-6, waveform.times[1], 1e-18)
        assertEquals(-20.0e-6 + 4.0e-9, waveform.times[2], 1e-18)
    }

    @Test
    fun `Envelope 形式は最大最小の対として扱う`() {
        // 4 バイト = 2 対。PT_FMT が ENV。
        val payload = byteArrayOf(10, -10, 20, -20)

        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(pointFormat = "ENV", yMultiplier = 1.0),
            payload,
        )

        assertTrue("Envelope として扱われていません: $waveform", waveform is EnvelopeWaveform)
        val envelope = waveform as EnvelopeWaveform
        // 点数はデータ数の半分。ここを間違えると時間軸が 2 倍にずれる。
        assertEquals(2, envelope.pointCount)
        assertEquals(-10.0, envelope.minVoltsPerPoint[0], 1e-12)
        assertEquals(10.0, envelope.maxVoltsPerPoint[0], 1e-12)
        assertEquals(-20.0, envelope.minVoltsPerPoint[1], 1e-12)
        assertEquals(20.0, envelope.maxVoltsPerPoint[1], 1e-12)
    }

    @Test
    fun `デジタル波形は電圧へ変換しない`() {
        val payload = byteArrayOf(0, 1, 0, 1)

        val waveform = WaveformDecoder.decodeBinary(WaveformSource.D0, preamble(), payload)

        assertTrue("デジタル波形として扱われていません: $waveform", waveform is DigitalWaveform)
        val digital = waveform as DigitalWaveform
        assertEquals(listOf(0, 1, 0, 1), digital.levels.toList())
    }

    @Test
    fun `RF 周波数領域は浮動小数として読む`() {
        // -90.5 dBm と -10.25 dBm を 4 バイト float（Big Endian）で並べる。
        val payload = floatArrayOf(-90.5f, -10.25f).let { values ->
            val bytes = ByteArray(values.size * 4)
            values.forEachIndexed { index, value ->
                val bits = value.toRawBits()
                for (byteIndex in 0 until 4) {
                    bytes[index * 4 + byteIndex] = ((bits shr ((3 - byteIndex) * 8)) and 0xFF).toByte()
                }
            }
            bytes
        }

        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.RF_NORMAL,
            preamble(bytesPerPoint = 4, binaryFormat = BinaryFormat.FLOATING_POINT),
            payload,
        )

        assertTrue("スペクトラムとして扱われていません: $waveform", waveform is SpectrumTrace)
        val spectrum = waveform as SpectrumTrace
        assertEquals(-90.5, spectrum.amplitudes[0], 1e-6)
        assertEquals(-10.25, spectrum.amplitudes[1], 1e-6)
    }

    @Test
    fun `Digital Collection は各ビットを取り出せる`() {
        // 4 バイト/点。0x00000005 = ビット 0 と 2 が 1。
        val payload = byteArrayOf(0, 0, 0, 5, 0, 0, 0, 2)

        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.DIGITAL_COLLECTION,
            preamble(bytesPerPoint = 4),
            payload,
        ) as DigitalCollectionWaveform

        assertEquals(2, waveform.pointCount)
        assertEquals(listOf(1, 0), waveform.extractBit(0).toList())
        assertEquals(listOf(0, 1), waveform.extractBit(1).toList())
        assertEquals(listOf(1, 0), waveform.extractBit(2).toList())
    }

    @Test
    fun `ASCII 応答をデコードする`() {
        val waveform = WaveformDecoder.decodeAscii(
            WaveformSource.CH1,
            preamble(yMultiplier = 1.0),
            "10,-10,20",
        ) as AnalogWaveform

        assertEquals(3, waveform.pointCount)
        assertEquals(10.0, waveform.volts[0], 1e-12)
        assertEquals(-10.0, waveform.volts[1], 1e-12)
    }

    @Test
    fun `ヘッダ付き ASCII 応答も読める`() {
        val waveform = WaveformDecoder.decodeAscii(
            WaveformSource.CH1,
            preamble(yMultiplier = 1.0),
            ":CURVE 1,2,3",
        ) as AnalogWaveform

        assertEquals(3, waveform.pointCount)
        assertEquals(1.0, waveform.volts[0], 1e-12)
    }

    @Test
    fun `プリアンブルが欠けている場合は明示的に失敗する`() {
        val incomplete = preamble().copy(yMultiplier = null, yOffset = null)

        try {
            WaveformDecoder.decodeBinary(WaveformSource.CH1, incomplete, byteArrayOf(1, 2, 3))
            fail("例外が投げられませんでした")
        } catch (error: WaveformDecodeException) {
            assertTrue(error.message!!.contains("YMULT"))
            assertTrue(error.message!!.contains("YOFF"))
        }
    }

    @Test
    fun `データ長が点あたりのバイト数で割り切れない場合は失敗する`() {
        try {
            // 2 バイト/点なのに 3 バイト。
            WaveformDecoder.decodeBinary(
                WaveformSource.CH1,
                preamble(bytesPerPoint = 2),
                byteArrayOf(1, 2, 3),
            )
            fail("例外が投げられませんでした")
        } catch (error: WaveformDecodeException) {
            assertTrue(error.message!!.contains("割り切れません"))
        }
    }

    @Test
    fun `元データを保持したまま電圧へ変換する`() {
        val payload = byteArrayOf(10, 20, 30)

        val waveform = WaveformDecoder.decodeBinary(
            WaveformSource.CH1,
            preamble(yMultiplier = 0.5),
            payload,
        ) as AnalogWaveform

        // 量子化された生の値も残す。保存とデバッグに必要。
        assertEquals(listOf(10, 20, 30), waveform.rawValues.toList())
        assertEquals(5.0, waveform.volts[0], 1e-12)
    }
}
