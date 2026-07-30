package com.pdtoscillo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.core.ui.theme.PdtTheme
import com.pdtoscillo.feature.connection.CONNECTION_LIST_TAG
import com.pdtoscillo.feature.connection.ConnectionScreen
import com.pdtoscillo.feature.connection.ConnectionViewModel
import com.pdtoscillo.simulator.ScopeSimulator
import com.pdtoscillo.simulator.SimulatedModel
import com.pdtoscillo.simulator.SimulatorConfig
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 接続画面の UI テスト。
 *
 * 疑似オシロスコープを端末内で起動し、実際に TCP 接続してから表示を検証する。
 * 実機のオシロスコープは不要だが、Compose UI テストのため端末またはエミュレータが必要。
 *
 * 画面は LazyColumn のため、画面外の要素はまだ合成されていない。
 * 参照する前に必ずリストをスクロールする。
 */
@RunWith(AndroidJUnit4::class)
class ConnectionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var simulator: ScopeSimulator
    private lateinit var session: InstrumentSession
    private lateinit var viewModel: ConnectionViewModel
    private var port: Int = 0

    @Before
    fun setUp() {
        simulator = ScopeSimulator(SimulatorConfig(port = 0, model = SimulatedModel.MDO4104C))
        port = simulator.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        session = InstrumentSession(context)
        viewModel = ConnectionViewModel(session)
    }

    @After
    fun tearDown() {
        simulator.close()
    }

    private fun setScreen() {
        composeRule.setContent {
            PdtTheme {
                ConnectionScreen(
                    viewModel = viewModel,
                    onOpenEscope = {},
                )
            }
        }
    }

    /** 目的の要素までリストをスクロールする。合成されていない要素はこれ無しでは見つからない。 */
    private fun scrollTo(text: String) {
        composeRule.onNodeWithTag(CONNECTION_LIST_TAG).performScrollToNode(hasText(text))
        composeRule.waitForIdle()
    }

    private fun enterTarget(host: String, portValue: String) {
        scrollTo("IP アドレス")
        composeRule.onNodeWithText("IP アドレス").performTextInput(host)
        scrollTo("ポート")
        composeRule.onNodeWithText("ポート").performTextReplacement(portValue)
        // エミュレータには Ethernet が無いため、バインドなしを選ぶ。
        scrollTo("システム既定（バインドなし）")
        composeRule.onNodeWithText("システム既定（バインドなし）").performClick()
    }

    @Test
    fun `接続画面の主要な項目が表示される`() {
        setScreen()

        composeRule.onNodeWithText("IP アドレス").assertIsDisplayed()
        composeRule.onNodeWithText("ポート").assertIsDisplayed()

        scrollTo("接続")
        composeRule.onNodeWithText("接続").assertIsDisplayed()

        scrollTo("接続診断")
        composeRule.onNodeWithText("接続診断").assertIsDisplayed()
    }

    @Test
    fun `接続直後は読み取り専用モードが有効になっている`() {
        setScreen()

        scrollTo("読み取り専用モード")
        composeRule.onNodeWithText("読み取り専用モード").assertIsDisplayed()
        composeRule.onNodeWithText("設定変更コマンドを拒否します。接続直後は必ず有効です。").assertIsDisplayed()
        assertTrue(session.client.readOnlyMode.value)
    }

    @Test
    fun `IP が空のときは接続ボタンが押せない`() {
        setScreen()

        scrollTo("接続")
        composeRule.onNodeWithText("接続").assertIsNotEnabled()
    }

    @Test
    fun `不正な IP アドレスはエラーとして示される`() {
        setScreen()

        composeRule.onNodeWithText("IP アドレス").performTextInput("999.999.1.1")

        scrollTo("IP アドレスまたはホスト名の形式が正しくありません。")
        composeRule.onNodeWithText("IP アドレスまたはホスト名の形式が正しくありません。").assertIsDisplayed()

        scrollTo("接続")
        composeRule.onNodeWithText("接続").assertIsNotEnabled()
    }

    @Test
    fun `IP を入力すると接続ボタンが押せるようになる`() {
        setScreen()

        composeRule.onNodeWithText("IP アドレス").performTextInput("127.0.0.1")

        scrollTo("接続")
        composeRule.onNodeWithText("接続").assertIsEnabled()
    }

    @Test
    fun `疑似オシロスコープへ接続するとモデルが表示される`() {
        setScreen()
        enterTarget("127.0.0.1", port.toString())

        scrollTo("接続")
        composeRule.onNodeWithText("接続").performClick()

        // 接続 → *IDN? → Capability 検出まで進むのを待つ。
        composeRule.waitUntil(timeoutMillis = 20_000) {
            session.client.identity.value?.model == "MDO4104C"
        }
        composeRule.waitForIdle()

        scrollTo("MDO4104C")
        composeRule.onNodeWithText("MDO4104C").assertIsDisplayed()
        assertTrue(session.client.connectionState.value.isConnected)
    }

    @Test
    fun `Capability に応じてチャンネル数が表示される`() {
        setScreen()
        enterTarget("127.0.0.1", port.toString())

        scrollTo("接続")
        composeRule.onNodeWithText("接続").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000) {
            session.client.capabilities.value != null
        }
        composeRule.waitForIdle()

        // 疑似 MDO4104C はアナログ 4 ch / デジタル 16 ch。
        val capabilities = session.client.capabilities.value!!
        assertTrue(capabilities.analogChannelCount == 4)
        assertTrue(capabilities.digitalChannelCount == 16)

        scrollTo("アナログ CH")
        composeRule.onNodeWithText("アナログ CH").assertIsDisplayed()
    }

    @Test
    fun `接続できない相手にはエラーと対処方法が表示される`() {
        setScreen()
        // 待ち受けていないポートを指定する。
        enterTarget("127.0.0.1", "1")

        scrollTo("接続")
        composeRule.onNodeWithText("接続").performClick()

        // エラーカードはリストの上部に挿入される。下へスクロールした状態では合成されていないため、
        // 表示の有無ではなく状態で待ち、そのうえでスクロールして確認する。
        composeRule.waitUntil(timeoutMillis = 20_000) { viewModel.uiState.value.error != null }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(CONNECTION_LIST_TAG)
            .performScrollToNode(hasText("対処:", substring = true))
        composeRule.onNodeWithText("対処:", substring = true).assertIsDisplayed()
    }

    @Test
    fun `読み取り専用モードは解除できる`() {
        setScreen()

        scrollTo("読み取り専用モード")
        assertTrue(session.client.readOnlyMode.value)

        session.client.setReadOnlyMode(false)
        composeRule.waitForIdle()

        assertFalse(session.client.readOnlyMode.value)
        composeRule.onNodeWithText("設定変更を許可しています。計測器の状態が変わります。").assertIsDisplayed()
    }

    @Test
    fun `初期設定の手順を開ける`() {
        setScreen()

        scrollTo("初期設定の手順")
        composeRule.onNodeWithText("初期設定の手順").performClick()

        composeRule.onNodeWithText("接続の手順").assertIsDisplayed()
        composeRule.onNodeWithText("Protocol を None に設定します（Terminal ではありません）。").assertIsDisplayed()
    }

    @Test
    fun `未実装の通信方式は選べない`() {
        setScreen()

        scrollTo("VXI-11（未実装）")
        composeRule.onNodeWithText("VXI-11（未実装）").assertIsNotEnabled()
    }
}
