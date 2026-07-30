package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.scpi.ScpiException
import com.pdtoscillo.core.scpi.WaveformTransfer
import com.pdtoscillo.core.scpi.WaveformTransferConfig
import com.pdtoscillo.core.waveform.AnalogWaveform
import com.pdtoscillo.core.waveform.MinMaxDecimator
import com.pdtoscillo.simulator.FaultMode
import com.pdtoscillo.simulator.WaveformShape
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 波形転送の統合テスト。
 *
 * 疑似オシロスコープから実際に TCP で波形を取得し、プリアンブルに従って
 * 電圧・時間へ戻せることを確認する。
 */
class WaveformTransferIntegrationTest {

    @Test
    fun `CH1 の波形を取得して電圧へ変換できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            val capture = transfer.capture(
                source = WaveformSource.CH1,
                config = WaveformTransferConfig(stopPoint = 1_000),
            )

            val waveform = capture.waveform as AnalogWaveform
            assertEquals(1_000, waveform.pointCount)
            assertEquals(1_000, capture.transferredBytes)

            // 正弦波なので正負両方の値を持つ。
            assertTrue("最大値が 0 以下です: ${waveform.maxVolts}", waveform.maxVolts > 0)
            assertTrue("最小値が 0 以上です: ${waveform.minVolts}", waveform.minVolts < 0)

            // 時間軸が単調増加していること。
            assertTrue(waveform.times.first() < waveform.times.last())
        }
    }

    @Test
    fun `プリアンブルの値でスケーリングされる`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            val capture = transfer.capture(WaveformSource.CH1, WaveformTransferConfig(stopPoint = 100))
            val waveform = capture.waveform as AnalogWaveform
            val preamble = capture.preamble

            // Yn = YZEro + YMUlt (yn - YOFf) を手計算で検証する。
            val expected = preamble.yZero!! + preamble.yMultiplier!! * (waveform.rawValues[0] - preamble.yOffset!!)
            assertEquals(expected, waveform.volts[0], 1e-12)

            // Xn = XZEro + XINcr (n - PT_Off)
            val expectedTime = preamble.xZero!! + preamble.xIncrement!! * (0 - preamble.pointOffset!!)
            assertEquals(expectedTime, waveform.times[0], 1e-18)
        }
    }

    @Test
    fun `16ビット転送でも正しく変換できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            val capture = transfer.capture(
                source = WaveformSource.CH1,
                config = WaveformTransferConfig(stopPoint = 500, bytesPerPoint = 2),
            )

            val waveform = capture.waveform as AnalogWaveform
            assertEquals(500, waveform.pointCount)
            // 500 点 × 2 バイト
            assertEquals(1_000, capture.transferredBytes)
            assertEquals(2, capture.preamble.bytesPerPoint)
            assertEquals(16, capture.preamble.bitsPerPoint)
        }
    }

    @Test
    fun `応答が分割されても波形を取得できる`() = runBlocking {
        SimulatorHarness(faultMode = FaultMode.SPLIT_RESPONSE, chunkSize = 11).use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            val capture = transfer.capture(WaveformSource.CH1, WaveformTransferConfig(stopPoint = 2_000))

            assertEquals(2_000, capture.waveform.pointCount)
        }
    }

    @Test
    fun `矩形波を取得できる`() = runBlocking {
        SimulatorHarness(waveformShape = WaveformShape.SQUARE).use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            val waveform = transfer.capture(
                WaveformSource.CH1,
                WaveformTransferConfig(stopPoint = 1_000),
            ).waveform as AnalogWaveform

            // 矩形波は 2 値に集中する。中間値の割合が小さいことを確認する。
            val amplitude = maxOf(kotlin.math.abs(waveform.maxVolts), kotlin.math.abs(waveform.minVolts))
            val middle = waveform.volts.count { kotlin.math.abs(it) < amplitude * 0.5 }
            assertTrue("中間値が多すぎます: $middle", middle < waveform.pointCount / 10)
        }
    }

    @Test
    fun `非表示チャンネルの取得は理由付きで失敗する`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            // CH2 は初期状態で非表示。
            try {
                transfer.capture(WaveformSource.CH2, WaveformTransferConfig(stopPoint = 100))
                fail("例外が投げられませんでした")
            } catch (exception: ScpiException) {
                assertTrue(
                    "想定外のエラー: ${exception.error}",
                    exception.error is ScopeError.WaveformNotAvailable ||
                        exception.error is ScopeError.ExecutionNotAllowed,
                )
            }
        }
    }

    @Test
    fun `表示すれば取得できるようになる`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            harness.client.write("SELect:CH2 ON")
            val capture = transfer.capture(WaveformSource.CH2, WaveformTransferConfig(stopPoint = 200))

            assertEquals(200, capture.waveform.pointCount)
        }
    }

    @Test
    fun `読み取り専用モードでも本体の現在設定のまま取得できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.client.connect(harness.config())
            harness.client.detectCapabilities()
            val transfer = WaveformTransfer(harness.client)

            // DATa:* を送らずに取得する。
            val capture = transfer.capture(WaveformSource.CH1, configureTransfer = false)

            assertNotNull(capture.waveform)
            assertTrue(capture.waveform.pointCount > 0)
            assertTrue(harness.client.readOnlyMode.value)
        }
    }

    @Test
    fun `不正なブロック長では黙って短い波形を返さない`() = runBlocking {
        SimulatorHarness(faultMode = FaultMode.BAD_BLOCK_LENGTH).use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            try {
                transfer.capture(WaveformSource.CH1, WaveformTransferConfig(stopPoint = 500))
                fail("例外が投げられませんでした")
            } catch (exception: ScpiException) {
                assertTrue(
                    "想定外のエラー: ${exception.error}",
                    exception.error is ScopeError.MalformedBinaryBlock ||
                        exception.error is ScopeError.ReadTimeout,
                )
            }
        }
    }

    @Test
    fun `取得した波形を画面幅に合わせて間引ける`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            val waveform = transfer.capture(
                WaveformSource.CH1,
                WaveformTransferConfig(stopPoint = 100_000),
            ).waveform as AnalogWaveform

            val decimated = MinMaxDecimator.decimate(waveform, targetPointCount = 1080)

            assertEquals(1080, decimated.pointCount)
            // 間引いてもピークは残る。
            assertEquals(waveform.maxVolts, decimated.maxValue, 1e-12)
            assertEquals(waveform.minVolts, decimated.minValue, 1e-12)
            // 元データは変わっていない。
            assertEquals(100_000, waveform.pointCount)
        }
    }

    @Test
    fun `連続して取得しても応答が混ざらない`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()
            val transfer = WaveformTransfer(harness.client)

            repeat(5) {
                val capture = transfer.capture(
                    WaveformSource.CH1,
                    WaveformTransferConfig(stopPoint = 1_000),
                )
                assertEquals(1_000, capture.waveform.pointCount)
                assertEquals(1, capture.preamble.bytesPerPoint)
            }
        }
    }
}
