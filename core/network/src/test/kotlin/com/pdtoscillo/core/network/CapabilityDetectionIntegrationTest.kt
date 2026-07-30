package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.BusType
import com.pdtoscillo.core.model.CapabilityDetectionSource
import com.pdtoscillo.core.model.ModelFamily
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.scpi.TektronixCapabilityDetector
import com.pdtoscillo.core.scpi.TektronixCommands
import com.pdtoscillo.simulator.SimulatedModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Capability 検出の統合テスト。
 *
 * 2 つの経路を必ず両方検証する。
 * 1. `CONFIGuration:*?` が使える機種（B / C 世代を模したモデル）
 * 2. 使えない機種（無印世代を模したモデル）→ モデル名からのフォールバック
 *
 * 無印世代に `CONFIGuration:*?` が存在するかは実機で未確認のため、
 * どちらの経路も壊れないことをテストで保証する。
 */
class CapabilityDetectionIntegrationTest {

    @Test
    fun `MDO4104C は CONFIGuration クエリから機能を検出する`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.MDO4104C).use { harness ->
            harness.client.connect(harness.config())
            harness.client.identify()

            val steps = mutableListOf<TektronixCapabilityDetector.Progress>()
            val capabilities = harness.client.detectCapabilities { steps += it }

            assertEquals(CapabilityDetectionSource.CONFIGURATION_QUERIES, capabilities.detectionSource)
            assertTrue(capabilities.supportsConfigurationQueries)
            assertEquals("MDO4104C", capabilities.model)
            assertEquals(ModelFamily.GEN3_MDO4000BC, capabilities.family)
            assertEquals(4, capabilities.analogChannelCount)
            assertEquals(16, capabilities.digitalChannelCount)
            assertTrue(capabilities.hasSpectrumAnalyzer)
            assertTrue(capabilities.hasAfg)
            assertTrue(capabilities.hasDvm)
            assertTrue(capabilities.hasBusDecode)
            assertEquals(4, capabilities.referenceWaveformCount)
            assertNotNull(capabilities.maxSampleRate)
            assertTrue(capabilities.supportedRecordLengths.contains(10_000L))
            assertTrue(capabilities.undeterminedFeatures.isEmpty())
            assertTrue(steps.isNotEmpty())
        }
    }

    @Test
    fun `MDO4104C では RF 波形ソースが有効になる`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.MDO4104C).use { harness ->
            harness.client.connect(harness.config())
            val capabilities = harness.client.detectCapabilities()

            assertTrue(capabilities.supports(WaveformSource.RF_NORMAL))
            assertTrue(capabilities.supports(WaveformSource.RF_AMPLITUDE))
            assertTrue(capabilities.supports(WaveformSource.D15))
            assertTrue(capabilities.supports(WaveformSource.CH4))
        }
    }

    @Test
    fun `オプション搭載を反映したバス種別だけが有効になる`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.MDO4104C).use { harness ->
            harness.client.connect(harness.config())
            val capabilities = harness.client.detectCapabilities()

            // 疑似モデルでは I2C / SPI / RS232 / CAN / LIN を搭載、USB / Ethernet は非搭載としている。
            assertTrue(capabilities.supportedBusTypes.contains(BusType.I2C))
            assertTrue(capabilities.supportedBusTypes.contains(BusType.CAN))
            assertFalse(capabilities.supportedBusTypes.contains(BusType.USB))
            assertFalse(capabilities.supportedBusTypes.contains(BusType.ETHERNET))
        }
    }

    @Test
    fun `無印世代では未定義ヘッダーを検出してモデル名から推定する`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.DPO4054).use { harness ->
            harness.client.connect(harness.config())
            harness.client.identify()

            val capabilities = harness.client.detectCapabilities()

            assertEquals(CapabilityDetectionSource.MODEL_NAME_FALLBACK, capabilities.detectionSource)
            assertFalse(capabilities.supportsConfigurationQueries)
            assertEquals(ModelFamily.GEN1_DPO_MSO_4000, capabilities.family)
            // DPO4054 → アナログ 4 ch、デジタルなし、RF なし。
            assertEquals(4, capabilities.analogChannelCount)
            assertEquals(0, capabilities.digitalChannelCount)
            assertFalse(capabilities.hasSpectrumAnalyzer)
        }
    }

    @Test
    fun `フォールバック時はオプションを不明として無効化する`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.DPO4054).use { harness ->
            harness.client.connect(harness.config())
            val capabilities = harness.client.detectCapabilities()

            // 過大評価しない。搭載を確認できないものは有効化しない。
            assertFalse(capabilities.hasAfg)
            assertFalse(capabilities.hasDvm)
            assertFalse(capabilities.hasBusDecode)

            // 「不明」であることを記録し、UI で理由を示せるようにする。
            assertTrue(capabilities.isUndetermined(TektronixCapabilityDetector.FEATURE_AFG))
            assertTrue(capabilities.isUndetermined(TektronixCapabilityDetector.FEATURE_DVM))
            assertTrue(capabilities.isUndetermined(TektronixCapabilityDetector.FEATURE_BUS))
        }
    }

    @Test
    fun `2ch モデルのチャンネル数を正しく推定する`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.DPO4032).use { harness ->
            harness.client.connect(harness.config())
            val capabilities = harness.client.detectCapabilities()

            assertEquals(2, capabilities.analogChannelCount)
            assertTrue(capabilities.supports(WaveformSource.CH2))
            assertFalse(capabilities.supports(WaveformSource.CH3))
        }
    }

    @Test
    fun `MSO 無印はデジタル16chを推定する`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.MSO4104).use { harness ->
            harness.client.connect(harness.config())
            val capabilities = harness.client.detectCapabilities()

            assertEquals(CapabilityDetectionSource.MODEL_NAME_FALLBACK, capabilities.detectionSource)
            assertEquals(4, capabilities.analogChannelCount)
            assertEquals(16, capabilities.digitalChannelCount)
            assertTrue(capabilities.supports(WaveformSource.D0))
        }
    }

    @Test
    fun `検出は本体設定を変更しない`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.MDO4104C).use { harness ->
            harness.client.connect(harness.config())

            val beforeScale = harness.client.queryValue(TektronixCommands.Vertical.scaleQuery(1))
            val beforeRecordLength = harness.client.queryValue(TektronixCommands.Horizontal.RECORD_LENGTH_QUERY)
            val beforeAcquire = harness.client.queryValue(TektronixCommands.Acquisition.STATE_QUERY)

            // 読み取り専用モードのままで検出できることも同時に確認する。
            assertTrue(harness.client.readOnlyMode.value)
            harness.client.detectCapabilities()

            assertEquals(beforeScale, harness.client.queryValue(TektronixCommands.Vertical.scaleQuery(1)))
            assertEquals(
                beforeRecordLength,
                harness.client.queryValue(TektronixCommands.Horizontal.RECORD_LENGTH_QUERY),
            )
            assertEquals(beforeAcquire, harness.client.queryValue(TektronixCommands.Acquisition.STATE_QUERY))
        }
    }
}
