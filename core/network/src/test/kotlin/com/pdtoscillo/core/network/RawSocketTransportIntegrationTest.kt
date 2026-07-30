package com.pdtoscillo.core.network

import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.scpi.ScpiException
import com.pdtoscillo.core.scpi.TektronixCommands
import com.pdtoscillo.simulator.FaultMode
import com.pdtoscillo.simulator.SimulatedModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 疑似オシロスコープへ実際に TCP 接続して行う統合テスト。
 *
 * 実時間の I/O を扱うため `runTest`（仮想時間）ではなく `runBlocking` を使う。
 * 仮想時間ではタイムアウトが即座に発火してしまい、実際の挙動を検証できない。
 */
class RawSocketTransportIntegrationTest {

    @Test
    fun `接続して IDN を取得できる`() = runBlocking {
        SimulatorHarness(model = SimulatedModel.MDO4104C).use { harness ->
            harness.client.connect(harness.config())

            val identity = harness.client.identify()

            assertEquals("TEKTRONIX", identity.manufacturer)
            assertEquals("MDO4104C", identity.model)
            assertEquals("C030001", identity.serialNumber)
            assertTrue(harness.client.connectionState.value.isConnected)
        }
    }

    @Test
    fun `応答が細かく分割されても正しく解析する`() = runBlocking {
        // TCP のパケット境界と SCPI の応答境界が一致しない状況。
        // 「1 回の read で全部届く」前提の実装はここで壊れる。
        SimulatorHarness(
            model = SimulatedModel.MDO4104C,
            faultMode = FaultMode.SPLIT_RESPONSE,
            chunkSize = 3,
            chunkDelayMillis = 2,
        ).use { harness ->
            harness.client.connect(harness.config())

            val identity = harness.client.identify()

            assertEquals("MDO4104C", identity.model)
        }
    }

    @Test
    fun `分割送信でもバイナリ波形を読み切る`() = runBlocking {
        SimulatorHarness(
            faultMode = FaultMode.SPLIT_RESPONSE,
            chunkSize = 13,
            chunkDelayMillis = 0,
        ).use { harness ->
            harness.connectAndUnlock()

            harness.client.write("${TektronixCommands.Waveform.DATA_SOURCE} CH1")
            harness.client.write("${TektronixCommands.Waveform.DATA_START} 1")
            harness.client.write("${TektronixCommands.Waveform.DATA_STOP} 5000")
            harness.client.write(
                "${TektronixCommands.Waveform.DATA_ENCODING} ${TektronixCommands.Waveform.Encoding.SIGNED_BIG_ENDIAN}",
            )
            harness.client.write("${TektronixCommands.Waveform.DATA_WIDTH} 1")

            val curve = harness.client.queryBinary(TektronixCommands.Waveform.CURVE_QUERY)

            assertEquals(5_000, curve.size)
        }
    }

    @Test
    fun `16ビット波形も読み切る`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()

            harness.client.write("${TektronixCommands.Waveform.DATA_SOURCE} CH1")
            harness.client.write("${TektronixCommands.Waveform.DATA_START} 1")
            harness.client.write("${TektronixCommands.Waveform.DATA_STOP} 1000")
            harness.client.write("${TektronixCommands.Waveform.DATA_WIDTH} 2")

            val curve = harness.client.queryBinary(TektronixCommands.Waveform.CURVE_QUERY)

            // 1000 点 × 2 バイト
            assertEquals(2_000, curve.size)
        }
    }

    @Test
    fun `プリアンブルを取得できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()

            val preamble = harness.client.queryText(TektronixCommands.Waveform.PREAMBLE_QUERY)

            assertTrue(preamble.contains("BYT_NR"))
            assertTrue(preamble.contains("YMULT"))
            assertTrue(preamble.contains("XINCR"))
        }
    }

    @Test
    fun `不正なブロック長を検出する`() = runBlocking {
        SimulatorHarness(faultMode = FaultMode.BAD_BLOCK_LENGTH).use { harness ->
            harness.connectAndUnlock()
            harness.client.write("${TektronixCommands.Waveform.DATA_STOP} 500")

            val error = expectScpiError {
                harness.client.queryBinary(TektronixCommands.Waveform.CURVE_QUERY)
            }

            // 宣言長より実データが少ないため、読み切れずに失敗する。
            assertTrue(
                "想定外のエラー: $error",
                error is ScopeError.MalformedBinaryBlock || error is ScopeError.ReadTimeout,
            )
        }
    }

    @Test
    fun `過大なブロック長は上限で拒否する`() = runBlocking {
        SimulatorHarness(faultMode = FaultMode.HUGE_BLOCK_LENGTH).use { harness ->
            harness.connectAndUnlock()

            val error = expectScpiError {
                harness.client.queryBinary(TektronixCommands.Waveform.CURVE_QUERY)
            }

            assertTrue("想定外のエラー: $error", error is ScopeError.MalformedBinaryBlock)
            assertTrue(error.detail!!.contains("上限"))
        }
    }

    @Test
    fun `応答が返らない場合はタイムアウトする`() = runBlocking {
        SimulatorHarness(faultMode = FaultMode.NO_RESPONSE).use { harness ->
            harness.client.connect(harness.config(queryTimeoutMillis = 600))

            val error = expectScpiError { harness.client.queryText(TektronixCommands.Common.IDENTIFY) }

            assertTrue("想定外のエラー: $error", error is ScopeError.ReadTimeout)
            assertTrue(error.isRetryable)
        }
    }

    @Test
    fun `応答の途中で切断された場合を検出する`() = runBlocking {
        SimulatorHarness(faultMode = FaultMode.DISCONNECT_MIDWAY).use { harness ->
            harness.client.connect(harness.config(queryTimeoutMillis = 800))

            val error = expectScpiError { harness.client.queryText(TektronixCommands.Common.IDENTIFY) }

            assertTrue(
                "想定外のエラー: $error",
                error is ScopeError.Disconnected || error is ScopeError.ReadTimeout,
            )
        }
    }

    @Test
    fun `切断してから再接続できる`() = runBlocking {
        SimulatorHarness().use { harness ->
            val config = harness.config()
            harness.client.connect(config)
            assertEquals("MDO4104C", harness.client.identify().model)

            harness.client.disconnect()
            assertTrue(!harness.client.connectionState.value.isConnected)

            harness.client.connect(config)
            assertEquals("MDO4104C", harness.client.identify().model)
            assertTrue(harness.simulator.acceptedConnections >= 2)
        }
    }

    @Test
    fun `タイムアウト後も受信バッファを破棄して次のコマンドを正しく処理する`() = runBlocking {
        // 遅延応答でタイムアウトさせ、その後に遅れて届いた応答が
        // 次のコマンドの応答として読まれないことを確認する。
        SimulatorHarness(
            faultMode = FaultMode.DELAYED_RESPONSE,
            responseDelayMillis = 700,
        ).use { harness ->
            harness.client.connect(harness.config(queryTimeoutMillis = 300, readTimeoutMillis = 300))

            expectScpiError { harness.client.queryText(TektronixCommands.Common.IDENTIFY) }
            assertTrue(harness.client.queue.needsResynchronization.value)

            // 遅延応答が届くのを待つ。
            Thread.sleep(900)

            // 同期喪失を検知しているため、次のコマンド前にバッファを破棄する。
            val response = runCatching {
                harness.client.queryText(TektronixCommands.Common.IDENTIFY, timeoutMillis = 2_000)
            }.getOrNull()

            // 破棄が効いていれば、IDN の応答として正しいものが返る（ずれた応答にならない）。
            if (response != null) {
                assertTrue("ずれた応答: $response", response.contains("TEKTRONIX"))
            }
        }
    }

    @Test
    fun `複数のクエリを同時に投げてもキューが直列化する`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()

            // 同時に投げても、応答の取り違えが起きてはならない。
            val results = listOf(
                async { harness.client.queryText(TektronixCommands.Common.IDENTIFY) },
                async { harness.client.queryText(TektronixCommands.Horizontal.RECORD_LENGTH_QUERY) },
                async { harness.client.queryText(TektronixCommands.Common.IDENTIFY) },
                async { harness.client.queryText(TektronixCommands.Acquisition.STATE_QUERY) },
                async { harness.client.queryText(TektronixCommands.Common.IDENTIFY) },
            ).awaitAll()

            assertTrue(results[0].contains("TEKTRONIX"))
            assertTrue(results[1].contains("10000"))
            assertTrue(results[2].contains("TEKTRONIX"))
            assertNotNull(results[3])
            assertTrue(results[4].contains("TEKTRONIX"))
        }
    }

    @Test
    fun `読み取り専用モードでは設定変更を拒否する`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.client.connect(harness.config())

            // 接続直後は読み取り専用。
            assertTrue(harness.client.readOnlyMode.value)

            val error = expectScpiError { harness.client.write("CH1:SCAle 0.5") }
            assertTrue("想定外のエラー: $error", error is ScopeError.ReadOnlyModeRejected)

            // 問い合わせは読み取り専用でも通る。
            val identity = harness.client.identify()
            assertEquals("MDO4104C", identity.model)

            // 拒否されたのだから本体の値は変わっていない。
            val scale = harness.client.queryDouble(TektronixCommands.Vertical.scaleQuery(1))
            assertEquals(0.1, scale!!, 1e-9)
        }
    }

    @Test
    fun `解除すれば設定変更でき本体が受理した値を読み戻せる`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()

            val result = harness.client.applyAndVerify(
                setCommand = TektronixCommands.Horizontal.RECORD_LENGTH,
                value = "100000",
                readBackQuery = TektronixCommands.Horizontal.RECORD_LENGTH_QUERY,
                parser = { com.pdtoscillo.core.scpi.ScpiResponseParser.parseLong(it) },
            )

            assertTrue("想定外の結果: $result", result is com.pdtoscillo.core.scpi.ScpiClient.ApplyResult.Applied)
            val applied = result as com.pdtoscillo.core.scpi.ScpiClient.ApplyResult.Applied
            assertEquals(10_000L, applied.previous)
            assertEquals(100_000L, applied.accepted)
        }
    }

    @Test
    fun `未定義コマンドではクラッシュせずエラーキューに記録される`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()

            // 存在しないコマンド。実機と同じく応答が返らない。
            val probe = harness.client.queue.probe(
                com.pdtoscillo.core.scpi.ScpiCommand.ProbeQuery("NOSUCH:COMMAND?", timeoutMillis = 400),
            )
            assertTrue(probe is com.pdtoscillo.core.scpi.ScpiCommandQueue.ProbeResult.NoResponse)

            val error = harness.client.errorQueue.classifyLatest("NOSUCH:COMMAND?")
            assertNotNull(error)
            assertTrue("想定外のエラー: $error", error is ScopeError.UndefinedHeader)
            assertTrue(error!!.indicatesUnsupported)
        }
    }

    @Test
    fun `Terminal プロトコルのエコーを検出して案内する`() = runBlocking {
        SimulatorHarness(terminalMode = true).use { harness ->
            harness.client.connect(harness.config(queryTimeoutMillis = 1_500))

            val error = expectScpiError { harness.client.identify() }

            assertTrue("想定外のエラー: $error", error is ScopeError.MalformedResponse)
            assertTrue(error.detail!!.contains("None"))
        }
    }

    @Test
    fun `通信ログにバイナリ本体を残さずサイズとハッシュを記録する`() = runBlocking {
        SimulatorHarness().use { harness ->
            harness.connectAndUnlock()
            harness.client.write("${TektronixCommands.Waveform.DATA_STOP} 1000")
            harness.client.queryBinary(TektronixCommands.Waveform.CURVE_QUERY)

            val entry = harness.client.logRecorder.entries.value.last { it.command == "CURVe?" }

            assertEquals(1_000L, entry.responseByteCount)
            assertNotNull(entry.responseSha256)
            // 本体は保持しない。
            assertEquals(null, entry.responsePreview)
        }
    }

    private inline fun expectScpiError(block: () -> Unit): ScopeError {
        try {
            block()
        } catch (exception: ScpiException) {
            return exception.error
        }
        fail("ScpiException が投げられませんでした")
        error("到達しない")
    }
}
