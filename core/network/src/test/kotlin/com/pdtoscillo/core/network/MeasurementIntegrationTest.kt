package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.MeasurementStatistics
import com.pdtoscillo.core.model.MeasurementType
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.scpi.MeasurementController
import com.pdtoscillo.core.scpi.ScpiClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementIntegrationTest {

    private suspend fun SimulatorHarness.controller(): MeasurementController {
        connectAndUnlock()
        client.detectCapabilities()
        return MeasurementController(client)
    }

    @Test
    fun `測定を割り当てて読み出せる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val controller = harness.controller()

            val result = controller.configureSlot(
                slot = 1,
                type = MeasurementType.FREQUENCY,
                source = WaveformSource.CH1,
            )

            assertTrue("想定外の結果: $result", result is ScpiClient.ApplyResult.Applied)

            val slot = controller.readSlot(1)
            assertTrue(slot.enabled)
            assertEquals(MeasurementType.FREQUENCY, slot.type)
            assertEquals(WaveformSource.CH1, slot.source)
            assertNotNull(slot.statistics.current)
        }
    }

    @Test
    fun `統計値を読み出せる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val controller = harness.controller()
            controller.configureSlot(1, MeasurementType.FREQUENCY, WaveformSource.CH1)

            val slot = controller.readSlot(1, withStatistics = true)

            assertNotNull(slot.statistics.mean)
            assertNotNull(slot.statistics.minimum)
            assertNotNull(slot.statistics.maximum)
            assertNotNull(slot.statistics.standardDeviation)
            assertNotNull(slot.statistics.sampleCount)
        }
    }

    @Test
    fun `統計を切ると余分な問い合わせをしない`() = runBlocking {
        SimulatorHarness().use { harness ->
            val controller = harness.controller()
            controller.configureSlot(1, MeasurementType.FREQUENCY, WaveformSource.CH1)

            val slot = controller.readSlot(1, withStatistics = false)

            assertNotNull(slot.statistics.current)
            // 統計を取らない設定では問い合わせ自体を行わない。
            assertEquals(null, slot.statistics.mean)
            assertEquals(null, slot.statistics.sampleCount)
        }
    }

    @Test
    fun `測定を外せる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val controller = harness.controller()
            controller.configureSlot(1, MeasurementType.PERIOD, WaveformSource.CH1)
            assertTrue(controller.readSlot(1).enabled)

            controller.disableSlot(1)

            assertFalse(controller.readSlot(1).enabled)
        }
    }

    @Test
    fun `複数スロットを同時に扱える`() = runBlocking {
        SimulatorHarness().use { harness ->
            val controller = harness.controller()

            controller.configureSlot(1, MeasurementType.FREQUENCY, WaveformSource.CH1)
            controller.configureSlot(2, MeasurementType.PEAK_TO_PEAK, WaveformSource.CH1)
            controller.configureSlot(3, MeasurementType.RMS, WaveformSource.CH1)

            val slots = controller.readAll(slotCount = 4)

            assertEquals(4, slots.size)
            assertTrue(slots[0].enabled)
            assertTrue(slots[1].enabled)
            assertTrue(slots[2].enabled)
            assertFalse(slots[3].enabled)
            assertEquals(MeasurementType.PEAK_TO_PEAK, slots[1].type)
        }
    }

    @Test
    fun `測定不可を示す値を判別できる`() {
        // マニュアル記載: 測定できない場合は 9.91e37 が返る。
        assertTrue(MeasurementStatistics.isNotANumber(9.91e37))
        assertTrue(MeasurementStatistics.isNotANumber(Double.NaN))
        assertFalse(MeasurementStatistics.isNotANumber(1000.0))
        assertFalse(MeasurementStatistics.isNotANumber(-5.0))
    }

    @Test
    fun `即時測定はスロットを消費しない`() = runBlocking {
        SimulatorHarness().use { harness ->
            val controller = harness.controller()

            val (value, unit) = controller.measureImmediate(MeasurementType.MEAN, WaveformSource.CH1)

            assertNotNull(value)
            assertNotNull(unit)
            // スロットは空のまま。
            assertFalse(controller.readSlot(1).enabled)
        }
    }

    @Test
    fun `読み取り専用モードでは測定を追加できない`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.client.connect(harness.config())
            harness.client.detectCapabilities()
            val controller = MeasurementController(harness.client)

            val result = controller.configureSlot(1, MeasurementType.FREQUENCY, WaveformSource.CH1)

            assertTrue("想定外の結果: $result", result is ScpiClient.ApplyResult.Rejected)
            assertFalse(controller.readSlot(1).enabled)
        }
    }
}
