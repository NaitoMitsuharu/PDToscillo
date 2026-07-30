package com.pdtoscillo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.ui.theme.PdtTheme
import com.pdtoscillo.simulator.ScopeSimulator
import com.pdtoscillo.simulator.SimulatedModel
import com.pdtoscillo.simulator.SimulatorConfig
import com.pdtoscillo.ui.PdtApp
import com.pdtoscillo.ui.TOP_BAR_MENU_TAG
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 画面遷移のテスト。
 *
 * **実装済みの画面すべてに到達できること**を確認する。
 * 下部ナビゲーションに載らない画面が開けないままにならないようにする。
 */
@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var simulator: ScopeSimulator
    private lateinit var session: InstrumentSession
    private var port: Int = 0

    @Before
    fun setUp() {
        runBlocking {
            simulator = ScopeSimulator(SimulatorConfig(port = 0, model = SimulatedModel.MDO4104C))
            port = simulator.start()

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            session = InstrumentSession(context)

            // 接続が必要な画面を開けるようにしておく。
            session.client.connect(
                ConnectionConfig(
                    host = "127.0.0.1",
                    port = port,
                    bindStrategy = SocketBindStrategy.SYSTEM_DEFAULT,
                ),
            )
            session.client.detectCapabilities()
        }
    }

    @After
    fun tearDown() {
        runBlocking { session.client.disconnect() }
        simulator.close()
    }

    /**
     * 画面名は文字列リソースから引く。
     * 端末のロケールによって表示が変わるため、日本語を直書きすると英語端末で落ちる。
     */
    private fun label(resId: Int): String = InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)

    private fun setApp() {
        composeRule.setContent {
            PdtTheme { PdtApp(session = session) }
        }
    }

    /** 上部メニューから目的の画面を開く。 */
    private fun openFromMenu(labelResId: Int) {
        composeRule.onNodeWithTag(TOP_BAR_MENU_TAG).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText(label(labelResId)).onLast().performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `下部ナビゲーションの画面へ移動できる`() {
        setApp()

        listOf(R.string.nav_overview, R.string.nav_waveform, R.string.nav_measurement, R.string.nav_console)
            .forEach { resId ->
                composeRule.onAllNodesWithText(label(resId)).onFirst().performClick()
                composeRule.waitForIdle()
            }
    }

    @Test
    fun `チャンネル画面を上部メニューから開ける`() {
        setApp()

        openFromMenu(R.string.nav_channels)

        composeRule.onNodeWithText("画面へ表示").assertIsDisplayed()
    }

    @Test
    fun `トリガ画面を上部メニューから開ける`() {
        setApp()

        openFromMenu(R.string.nav_trigger)

        composeRule.onNodeWithText("トリガ種別").assertIsDisplayed()
    }

    @Test
    fun `オプション画面を上部メニューから開ける`() {
        setApp()

        openFromMenu(R.string.nav_options)

        // MDO4104C はデジタル・RF・AFG・DVM を持つ。
        composeRule.onNodeWithText("デジタルチャンネル").assertIsDisplayed()
    }

    @Test
    fun `ファイル画面を上部メニューから開ける`() {
        setApp()

        openFromMenu(R.string.nav_files)

        composeRule.onNodeWithText("本体のストレージ").assertIsDisplayed()
    }

    @Test
    fun `自動測定画面を上部メニューから開ける`() {
        setApp()

        openFromMenu(R.string.nav_automation)

        composeRule.onNodeWithText("回数と時間").assertIsDisplayed()
    }

    @Test
    fun `設定画面を上部メニューから開ける`() {
        setApp()

        openFromMenu(R.string.nav_settings)

        composeRule.onNodeWithText("表示言語").assertIsDisplayed()
    }
}
