package com.pdtoscillo

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.SocketBindStrategy
import com.pdtoscillo.core.network.InstrumentSession
import com.pdtoscillo.simulator.ScopeSimulator
import com.pdtoscillo.simulator.SimulatedModel
import com.pdtoscillo.simulator.SimulatorConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * セッションログのテスト。
 *
 * 実機で最初に接続するときの調査に使うため、**何が記録されるか**を実際に確かめる。
 * 特に `*IDN?` の生応答は機種の特定に直結するので、必ず残っていること。
 */
@RunWith(AndroidJUnit4::class)
class SessionLogTest {

    private lateinit var simulator: ScopeSimulator
    private lateinit var session: InstrumentSession
    private var port: Int = 0

    @Before
    fun setUp() {
        simulator = ScopeSimulator(SimulatorConfig(port = 0, model = SimulatedModel.MDO4104C))
        port = simulator.start()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        session = InstrumentSession(context)
    }

    @After
    fun tearDown() {
        session.sessionLogger.stop()
        runBlocking { session.client.disconnect() }
        session.sessionLogger.listLogs().forEach { session.sessionLogger.delete(it.name) }
        simulator.close()
    }

    private fun config() = ConnectionConfig(
        host = "127.0.0.1",
        port = port,
        bindStrategy = SocketBindStrategy.SYSTEM_DEFAULT,
    )

    @Test
    fun `記録を開始するとファイルが作られる`() {
        session.sessionLogger.start(config())

        val state = session.sessionLogger.state.value
        assertTrue(state.recording)
        assertTrue("ファイルがありません", session.sessionLogger.currentFile()!!.exists())
    }

    @Test
    fun `ヘッダに端末と接続設定が入る`() {
        session.sessionLogger.start(config())

        val text = session.sessionLogger.currentFile()!!.readText()
        assertTrue(text.contains("PDToscillo セッションログ"))
        assertTrue(text.contains("端末"))
        assertTrue(text.contains("Android"))
        assertTrue("接続先が入っていません", text.contains("127.0.0.1:$port"))
        assertTrue("バインド方式が入っていません", text.contains("SYSTEM_DEFAULT"))
    }

    @Test
    fun `ネットワークの状態が記録される`() {
        session.sessionLogger.start(config())

        val text = session.sessionLogger.currentFile()!!.readText()
        assertTrue(text.contains("ネットワークの状態"))
        assertTrue(text.contains("Ethernet 検出"))
        assertTrue("インターフェース一覧がありません", text.contains("OS が見せているインターフェース一覧"))
    }

    @Test
    fun `送受信した SCPI がすべて記録される`() = runBlocking {
        session.sessionLogger.start(config())
        session.client.connect(config())

        val identity = session.client.identify()
        // ログの書き出しは Flow 経由なので、反映を待つ。
        waitUntilLogContains("*IDN?")

        val text = session.sessionLogger.currentFile()!!.readText()
        assertTrue("送ったコマンドがありません", text.contains("*IDN?"))
        // 機種の特定に直結するため、生応答は必ず残す。
        assertTrue("応答が残っていません", text.contains(identity.raw))
        assertTrue("所要時間が残っていません", text.contains("ms)"))
    }

    @Test
    fun `機能検出のやり取りも記録される`() = runBlocking {
        session.sessionLogger.start(config())
        session.client.connect(config())
        session.client.detectCapabilities()

        waitUntilLogContains("CONFIGuration:ANALOg:NUMCHANnels?")

        val text = session.sessionLogger.currentFile()!!.readText()
        assertTrue(text.contains("CONFIGuration:ANALOg:NUMCHANnels?"))
    }

    @Test
    fun `失敗したコマンドは理由付きで記録される`() = runBlocking {
        session.sessionLogger.start(config())
        session.client.connect(config())

        // 読み取り専用モードのまま設定変更を試みる。
        runCatching { session.client.write("CH1:SCAle 0.5") }
        waitUntilLogContains("CH1:SCAle")

        val text = session.sessionLogger.currentFile()!!.readText()
        assertTrue("拒否が記録されていません", text.contains("REJECT"))
        assertTrue(text.contains("ReadOnlyModeRejected"))
    }

    @Test
    fun `バイナリ応答は本体を書かずサイズとハッシュを残す`() = runBlocking {
        session.sessionLogger.start(config())
        session.client.connect(config())
        session.client.setReadOnlyMode(false)
        session.client.write("DATa:STOP 500")
        session.client.queryBinary("CURVe?")

        waitUntilLogContains("CURVe?")

        val text = session.sessionLogger.currentFile()!!.readText()
        assertTrue("サイズが残っていません", text.contains("500 バイト"))
        assertTrue("ハッシュが残っていません", text.contains("SHA-256"))
    }

    @Test
    fun `任意のメモを書ける`() {
        session.sessionLogger.start(config())

        session.sessionLogger.section("接続診断の結果")
        session.sessionLogger.note("Ethernet 検出: なし")

        val text = session.sessionLogger.currentFile()!!.readText()
        assertTrue(text.contains("===== 接続診断の結果 ====="))
        assertTrue(text.contains("Ethernet 検出: なし"))
    }

    @Test
    fun `停止後は追記されない`() = runBlocking {
        session.sessionLogger.start(config())
        session.client.connect(config())
        session.sessionLogger.stop()

        val sizeAfterStop = session.sessionLogger.currentFile()?.length() ?: 0
        session.client.identify()
        Thread.sleep(SETTLE_MILLIS)

        assertFalse(session.sessionLogger.state.value.recording)
        val file = session.sessionLogger.listLogs().firstOrNull()
        assertTrue("停止後に追記されています", (file?.length() ?: 0) <= sizeAfterStop)
    }

    @Test
    fun `記録中のファイルは削除できない`() {
        session.sessionLogger.start(config())
        val name = session.sessionLogger.state.value.fileName!!

        val deleted = session.sessionLogger.delete(name)

        assertFalse("記録中のファイルが削除されました", deleted)
        assertTrue(session.sessionLogger.currentFile()!!.exists())
    }

    @Test
    fun `保存先の外は削除できない`() {
        session.sessionLogger.start(config())
        session.sessionLogger.stop()

        // 親ディレクトリ参照でアプリ専用ディレクトリの外を指しても消えない。
        assertFalse(session.sessionLogger.delete("../../databases/pdtoscillo.db"))
    }

    /** ログはコルーチン経由で書かれるため、内容が現れるまで待つ。 */
    private fun waitUntilLogContains(text: String) {
        val deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            if (session.sessionLogger.currentFile()?.readText()?.contains(text) == true) return
            Thread.sleep(POLL_MILLIS)
        }
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 10_000L
        const val POLL_MILLIS = 100L
        const val SETTLE_MILLIS = 500L
    }
}
