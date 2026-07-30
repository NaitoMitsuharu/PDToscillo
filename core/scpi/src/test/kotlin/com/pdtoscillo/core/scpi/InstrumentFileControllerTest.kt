package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.common.CommunicationLogRecorder
import com.pdtoscillo.core.model.ConnectionConfig
import com.pdtoscillo.core.model.ConnectionState
import com.pdtoscillo.core.model.InstrumentTransport
import com.pdtoscillo.core.model.TransportRouteInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ファイル名の検証。
 *
 * 本体から返る名前や利用者の入力をそのままコマンドへ差し込むと、
 * 引用が壊れたり、意図しないディレクトリを操作できてしまう。
 */
class InstrumentFileControllerTest {

    /** 通信しない Transport。名前の検証だけを確かめる。 */
    private class NoopTransport : InstrumentTransport {
        override val state: StateFlow<ConnectionState> = MutableStateFlow(ConnectionState.Disconnected)
        override val routeInfo: StateFlow<TransportRouteInfo?> = MutableStateFlow(null)
        override suspend fun connect(config: ConnectionConfig) = Unit
        override suspend fun disconnect() = Unit
        override suspend fun write(command: ByteArray) = Unit
        override suspend fun readText(): String = ""
        override suspend fun readBinary(): ByteArray = ByteArray(0)
        override suspend fun queryText(command: String): String = ""
        override suspend fun queryBinary(command: String): ByteArray = ByteArray(0)
        override suspend fun discardPendingInput(): Int = 0
    }

    private val controller = InstrumentFileController(ScpiClient(NoopTransport(), CommunicationLogRecorder()))

    @Test
    fun `通常のファイル名は受け付ける`() {
        assertTrue(controller.validateName("setup1.set").isSuccess)
        assertTrue(controller.validateName("E:/waveform_001.csv").isSuccess)
        assertTrue(controller.validateName("測定_2026.png").isSuccess)
    }

    @Test
    fun `親ディレクトリ参照を拒否する`() {
        val result = controller.validateName("../../etc/passwd")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains(".."))
    }

    @Test
    fun `パスの途中の親ディレクトリ参照も拒否する`() {
        assertTrue(controller.validateName("E:/data/../../secret.set").isFailure)
    }

    @Test
    fun `引用符を拒否する`() {
        // 引用符を許すとコマンドの引用が閉じ、後続を別コマンドとして解釈させられる。
        val result = controller.validateName("""a".set""")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("引用符"))
    }

    @Test
    fun `セミコロンと改行を拒否する`() {
        assertTrue(controller.validateName("a.set;*RST").isFailure)
        assertTrue(controller.validateName("a.set\n*RST").isFailure)
        assertTrue(controller.validateName("a.set\r\nFACTORY").isFailure)
    }

    @Test
    fun `空の名前を拒否する`() {
        assertTrue(controller.validateName("").isFailure)
        assertTrue(controller.validateName("   ").isFailure)
    }

    @Test
    fun `長すぎる名前を拒否する`() {
        assertTrue(controller.validateName("a".repeat(300)).isFailure)
    }

    @Test
    fun `前後の空白は取り除いて受け付ける`() {
        val result = controller.validateName("  setup.set  ")

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow().startsWith(" "))
    }
}
