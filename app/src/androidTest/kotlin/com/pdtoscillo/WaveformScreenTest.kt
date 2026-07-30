package com.pdtoscillo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdtoscillo.core.database.export.WaveformExporter
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.ui.theme.PdtTheme
import com.pdtoscillo.core.waveform.AnalogWaveform
import com.pdtoscillo.feature.waveform.WAVEFORM_CANVAS_TAG
import com.pdtoscillo.feature.waveform.WaveformScreen
import com.pdtoscillo.feature.waveform.WaveformViewModel
import com.pdtoscillo.simulator.ScopeSimulator
import com.pdtoscillo.simulator.SimulatedModel
import com.pdtoscillo.simulator.SimulatorConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 波形画面の UI テスト。
 *
 * 疑似オシロスコープから実際に波形を取得し、描画と保存まで通す。
 */
@RunWith(AndroidJUnit4::class)
class WaveformScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var simulator: ScopeSimulator
    private lateinit var session: InstrumentSession
    private lateinit var viewModel: WaveformViewModel
    private lateinit var exporter: WaveformExporter
    private var port: Int = 0

    @Before
    fun setUp() = runBlocking {
        simulator = ScopeSimulator(SimulatorConfig(port = 0, model = SimulatedModel.MDO4104C))
        port = simulator.start()

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        session = InstrumentSession(context)
        exporter = WaveformExporter(context)

        // 波形画面は接続済みが前提。先に接続と Capability 検出まで済ませる。
        session.client.connect(
            ConnectionConfig(
                host = "127.0.0.1",
                port = port,
                bindStrategy = SocketBindStrategy.SYSTEM_DEFAULT,
            ),
        )
        session.client.detectCapabilities()
        session.client.setReadOnlyMode(false)

        viewModel = WaveformViewModel(session, exporter)
    }

    @After
    fun tearDown() = runBlocking {
        session.client.disconnect()
        simulator.close()
    }

    private fun setScreen() {
        composeRule.setContent {
            PdtTheme {
                WaveformScreen(viewModel = viewModel)
            }
        }
    }

    @Test
    fun `波形が無いときは案内を表示する`() {
        setScreen()

        composeRule.onNodeWithText("波形がありません。「取得」を押してください。").assertIsDisplayed()
    }

    @Test
    fun `取得すると波形が描画される`() {
        setScreen()

        composeRule.onNodeWithText("取得").performClick()

        composeRule.waitUntil(timeoutMillis = 30_000) {
            viewModel.uiState.value.traces.any { it.waveform != null }
        }
        composeRule.waitForIdle()

        val waveform = viewModel.uiState.value.traces.first { it.waveform != null }.waveform as AnalogWaveform
        assertTrue("点数が 0 です", waveform.pointCount > 0)
        // 正弦波なので上下に振れる。
        assertTrue(waveform.maxVolts > 0)
        assertTrue(waveform.minVolts < 0)

        composeRule.onNodeWithTag(WAVEFORM_CANVAS_TAG).assertIsDisplayed()
    }

    @Test
    fun `表示用データは画面幅に合わせて間引かれ元データは保持される`() {
        setScreen()

        composeRule.onNodeWithText("取得").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            viewModel.renderData.value.any { it.times.isNotEmpty() }
        }

        val original = viewModel.uiState.value.traces.first { it.waveform != null }.waveform!!
        val rendered = viewModel.renderData.value.first { it.times.isNotEmpty() }

        // 表示用は画面幅程度まで間引かれている。
        assertTrue("間引かれていません: ${rendered.times.size}", rendered.times.size <= original.pointCount)
        // 元データは減っていない。保存と測定はこちらを使う。
        assertEquals(10_000, original.pointCount)
    }

    @Test
    fun `チャンネルの表示を切り替えられる`() {
        setScreen()

        composeRule.onNodeWithText("CH2").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(viewModel.uiState.value.traces.first { it.source.scpiValue == "CH2" }.visible)
    }

    @Test
    fun `カーソルを有効にすると差分が表示される`() {
        setScreen()

        composeRule.onNodeWithText("取得").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            viewModel.uiState.value.traces.any { it.waveform != null }
        }

        composeRule.onNodeWithText("垂直バー").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Δt").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1/Δt").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `CSV へ保存できる`() {
        setScreen()

        composeRule.onNodeWithText("取得").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            viewModel.uiState.value.traces.any { it.waveform != null }
        }

        composeRule.onNodeWithText("CSV").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            viewModel.uiState.value.exports.isNotEmpty()
        }

        val exported = viewModel.uiState.value.exports.first()
        assertTrue("ファイルがありません", exported.file.exists())
        assertTrue("サイズが 0 です", exported.sizeBytes > 0)
        // 行数は点数 + ヘッダ 1 行。
        assertEquals(exported.pointCount + 1, exported.file.readLines().size)
    }

    @Test
    fun `PNG へ保存できる`() {
        setScreen()

        composeRule.onNodeWithText("取得").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            viewModel.uiState.value.traces.any { it.waveform != null }
        }

        composeRule.onNodeWithText("PNG").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 20_000) {
            viewModel.uiState.value.exports.any { it.format.extension == "png" }
        }

        val exported = viewModel.uiState.value.exports.first { it.format.extension == "png" }
        assertTrue(exported.file.exists())
        assertTrue(exported.sizeBytes > 0)
    }

    @Test
    fun `自動スケールで表示範囲が波形に合う`() {
        setScreen()

        composeRule.onNodeWithText("取得").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            viewModel.uiState.value.window != null
        }

        composeRule.onNodeWithText("自動スケール").performScrollTo().performClick()
        composeRule.waitForIdle()

        val window = viewModel.uiState.value.window!!
        val waveform = viewModel.uiState.value.traces.first { it.waveform != null }.waveform as AnalogWaveform
        assertTrue("表示範囲に波形が収まっていません", window.minVolts <= waveform.minVolts)
        assertTrue("表示範囲に波形が収まっていません", window.maxVolts >= waveform.maxVolts)
    }
}
