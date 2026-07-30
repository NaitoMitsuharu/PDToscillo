package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.AcquisitionMode
import com.pdtoscillo.core.model.ChannelCoupling
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.StopAfterMode
import com.pdtoscillo.core.model.TriggerSlope
import com.pdtoscillo.core.model.TriggerSweepMode
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.scpi.ScpiClient
import com.pdtoscillo.core.scpi.Tektronix4000Driver
import com.pdtoscillo.simulator.SimulatedModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 高レベル操作の統合テスト。
 *
 * 「送った値」ではなく「本体が受理した値」を返しているかを重点的に確認する。
 */
class Tektronix4000DriverIntegrationTest {

    private suspend fun SimulatorHarness.driver(): Tektronix4000Driver {
        connectAndUnlock()
        client.detectCapabilities()
        return Tektronix4000Driver(client)
    }

    @Test
    fun `Acquisition の設定を読める`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val acquisition = driver.readAcquisition()

            assertEquals(true, acquisition.running)
            assertEquals(AcquisitionMode.SAMPLE, acquisition.mode)
            assertEquals(StopAfterMode.RUN_STOP, acquisition.stopAfter)
            assertEquals(16, acquisition.averageCount)
            assertNotNull(acquisition.acquisitionCount)
        }
    }

    @Test
    fun `Stop と Run で取得状態が変わる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            driver.stop()
            assertEquals(false, driver.readAcquisition().running)

            driver.run()
            assertEquals(true, driver.readAcquisition().running)
        }
    }

    @Test
    fun `単発取得は STOPAfter を SEQuence にしてから RUN する`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            driver.single()

            val acquisition = driver.readAcquisition()
            assertEquals(StopAfterMode.SEQUENCE, acquisition.stopAfter)
            assertEquals(true, acquisition.running)
        }
    }

    @Test
    fun `Acquisition モードを変更して受理値を読み戻す`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val result = driver.applyAcquisitionMode(AcquisitionMode.AVERAGE)

            assertTrue("想定外の結果: $result", result is ScpiClient.ApplyResult.Applied)
            val applied = result as ScpiClient.ApplyResult.Applied
            assertEquals(AcquisitionMode.SAMPLE, applied.previous)
            assertEquals(AcquisitionMode.AVERAGE, applied.accepted)
        }
    }

    @Test
    fun `Horizontal の設定を読める`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val horizontal = driver.readHorizontal()

            assertEquals(4.0e-6, horizontal.scaleSecondsPerDivision!!, 1e-12)
            assertEquals(10_000L, horizontal.recordLength)
            assertNotNull(horizontal.sampleRate)
            // 10 div ぶんの時間幅。
            assertEquals(40.0e-6, horizontal.totalTimeSpan!!, 1e-12)
        }
    }

    @Test
    fun `レコード長を変更できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val result = driver.applyRecordLength(100_000)

            val applied = result as ScpiClient.ApplyResult.Applied
            assertEquals(10_000L, applied.previous)
            assertEquals(100_000L, applied.accepted)
        }
    }

    @Test
    fun `チャンネル設定を読める`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val channel = driver.readChannel(1)

            assertEquals(1, channel.channel)
            assertEquals(true, channel.displayed)
            assertEquals(0.1, channel.verticalScale!!, 1e-9)
            assertEquals(ChannelCoupling.DC, channel.coupling)
            assertEquals(false, channel.inverted)
            // 10 div ぶんの電圧幅。
            assertEquals(1.0, channel.totalVoltageSpan!!, 1e-9)
        }
    }

    @Test
    fun `表示していないチャンネルは displayed が false になる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            assertEquals(false, driver.readChannel(2).displayed)

            driver.applyChannelDisplay(2, true)
            assertEquals(true, driver.readChannel(2).displayed)
        }
    }

    @Test
    fun `垂直スケールを変更して受理値を読み戻す`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val result = driver.applyVerticalScale(1, 0.5)

            val applied = result as ScpiClient.ApplyResult.Applied
            assertEquals(0.1, applied.previous!!, 1e-9)
            assertEquals(0.5, applied.accepted!!, 1e-9)
        }
    }

    @Test
    fun `カップリングを変更できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val result = driver.applyCoupling(1, ChannelCoupling.AC)

            val applied = result as ScpiClient.ApplyResult.Applied
            assertEquals(ChannelCoupling.DC, applied.previous)
            assertEquals(ChannelCoupling.AC, applied.accepted)
        }
    }

    @Test
    fun `DC と DCREJect を混同しない`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            driver.applyCoupling(1, ChannelCoupling.DC_REJECT)
            assertEquals(ChannelCoupling.DC_REJECT, driver.readChannel(1).coupling)

            driver.applyCoupling(1, ChannelCoupling.DC)
            assertEquals(ChannelCoupling.DC, driver.readChannel(1).coupling)
        }
    }

    @Test
    fun `ラベルの引用符は取り除いて送る`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            // 引用符をそのまま送るとコマンドが壊れる。
            val result = driver.applyLabel(1, """CLK"INJECT""")

            assertTrue("想定外の結果: $result", result is ScpiClient.ApplyResult.Applied)
            val accepted = (result as ScpiClient.ApplyResult.Applied).accepted
            assertEquals("CLKINJECT", accepted)
        }
    }

    @Test
    fun `プローブ減衰比は GAIN の逆数として扱う`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            driver.applyProbeAttenuation(1, 10.0)

            val channel = driver.readChannel(1)
            assertEquals(0.1, channel.probeGain!!, 1e-9)
            assertEquals(10.0, channel.probeAttenuation!!, 1e-6)
        }
    }

    @Test
    fun `トリガ設定を読める`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val trigger = driver.readTrigger()

            assertEquals(WaveformSource.CH1, trigger.edgeSource)
            assertEquals(TriggerSlope.RISE, trigger.slope)
            assertEquals(TriggerSweepMode.AUTO, trigger.sweepMode)
            assertNotNull(trigger.runState)
            assertNotNull(trigger.level)
        }
    }

    @Test
    fun `トリガレベルはソースごとに設定する`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val result = driver.applyTriggerLevel(1, 1.25)

            val applied = result as ScpiClient.ApplyResult.Applied
            assertEquals(1.25, applied.accepted!!, 1e-6)
            assertEquals(1.25, driver.readTrigger().level!!, 1e-6)
        }
    }

    @Test
    fun `トリガのスロープを変更できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            driver.applyTriggerSlope(TriggerSlope.FALL)

            assertEquals(TriggerSlope.FALL, driver.readTrigger().slope)
        }
    }

    @Test
    fun `スナップショットを一括取得できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val driver = harness.driver()

            val snapshot = driver.readSnapshot(harness.client.capabilities.value)

            // MDO4104C は 4 ch。
            assertEquals(4, snapshot.channels.size)
            assertEquals(1, snapshot.displayedChannels.size)
            assertNotNull(snapshot.horizontal.recordLength)
            assertNotNull(snapshot.trigger.edgeSource)
            assertTrue(snapshot.elapsedMillis >= 0)
        }
    }

    @Test
    fun `2ch モデルでは 3ch 以降を問い合わせない`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.DPO4032).use { harness ->
            val driver = harness.driver()

            val snapshot = driver.readSnapshot(harness.client.capabilities.value)

            assertEquals(2, snapshot.channels.size)
        }
    }

    @Test
    fun `読み取り専用モードでは設定変更が拒否され値が変わらない`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.client.connect(harness.config())
            harness.client.detectCapabilities()
            val driver = Tektronix4000Driver(harness.client)

            val before = driver.readChannel(1).verticalScale
            val result = driver.applyVerticalScale(1, 0.5)

            assertTrue("想定外の結果: $result", result is ScpiClient.ApplyResult.Rejected)
            val rejected = result as ScpiClient.ApplyResult.Rejected
            assertTrue(rejected.error is ScopeError.ReadOnlyModeRejected)
            assertEquals(before!!, driver.readChannel(1).verticalScale!!, 1e-12)
        }
    }
}
