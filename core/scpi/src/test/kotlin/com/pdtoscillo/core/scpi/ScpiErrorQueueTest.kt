package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.ScopeError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScpiEventParsingTest {

    @Test
    fun `EVMsg の応答を解析する`() {
        val event = ScpiErrorQueue.parseEvent("""113,"Undefined header; CONFIGURATION:AFG"""")

        assertEquals(113, event!!.code)
        assertEquals("Undefined header; CONFIGURATION:AFG", event.message)
    }

    @Test
    fun `メッセージ内のカンマを保持する`() {
        val event = ScpiErrorQueue.parseEvent("""2200,"Measurement error, Measurement system error"""")

        assertEquals(2200, event!!.code)
        assertEquals("Measurement error, Measurement system error", event.message)
    }

    @Test
    fun `キューが空の応答を判別する`() {
        val event = ScpiErrorQueue.parseEvent("""0,"No events to report - queue empty"""")

        assertEquals(0, event!!.code)
        assertTrue(event.isEmptyQueueMarker)
    }

    @Test
    fun `新規イベント保留のマーカーも空扱いにする`() {
        val event = ScpiErrorQueue.parseEvent("""1,"No events to report; new events pending *ESR?"""")

        assertTrue(event!!.isEmptyQueueMarker)
    }

    @Test
    fun `ヘッダ付きの応答も解析する`() {
        val event = ScpiErrorQueue.parseEvent(""":EVMSG 113,"Undefined header"""")

        assertEquals(113, event!!.code)
    }

    @Test
    fun `解析できない応答は null を返す`() {
        assertNull(ScpiErrorQueue.parseEvent(""))
        assertNull(ScpiErrorQueue.parseEvent("garbage"))
    }

    @Test
    fun `コード区分を判定する`() {
        assertEquals(ScpiEvent.Category.COMMAND_ERROR, ScpiEvent(113, "").category)
        assertEquals(ScpiEvent.Category.COMMAND_ERROR, ScpiEvent(102, "").category)
        assertEquals(ScpiEvent.Category.EXECUTION_ERROR, ScpiEvent(222, "").category)
        assertEquals(ScpiEvent.Category.DEVICE_ERROR, ScpiEvent(350, "").category)
        assertEquals(ScpiEvent.Category.QUERY_ERROR, ScpiEvent(420, "").category)
        assertEquals(ScpiEvent.Category.EXECUTION_ERROR, ScpiEvent(2241, "").category)
        assertEquals(ScpiEvent.Category.NONE, ScpiEvent(0, "").category)
    }
}

class ScpiErrorClassificationTest {

    @Test
    fun `未定義ヘッダーは未対応として扱う`() {
        val error = ScpiErrorQueue.toScopeError(
            "CONFIGuration:AFG?",
            ScpiEvent(113, "Undefined header"),
        )

        assertTrue(error is ScopeError.UndefinedHeader)
        assertTrue(error.indicatesUnsupported)
    }

    @Test
    fun `その他のコマンドエラーも未対応として扱う`() {
        val error = ScpiErrorQueue.toScopeError("FOO:BAR?", ScpiEvent(102, "Syntax error"))

        assertTrue(error is ScopeError.UndefinedHeader)
    }

    @Test
    fun `Hardware missing はオプション未搭載として扱う`() {
        val error = ScpiErrorQueue.toScopeError("AFG:OUTPut:STATE ON", ScpiEvent(241, "Hardware missing"))

        assertTrue(error is ScopeError.OptionNotInstalled)
        assertTrue(error.indicatesUnsupported)
    }

    @Test
    fun `範囲外の値を判別する`() {
        val outOfRange = ScpiErrorQueue.toScopeError("CH1:SCAle 1e9", ScpiEvent(222, "Data out of range"))
        assertTrue(outOfRange is ScopeError.ArgumentOutOfRange)

        val illegal = ScpiErrorQueue.toScopeError("CH1:COUPling XX", ScpiEvent(224, "Illegal parameter value"))
        assertTrue(illegal is ScopeError.ArgumentOutOfRange)
    }

    @Test
    fun `設定の衝突は実行不可として扱う`() {
        val error = ScpiErrorQueue.toScopeError("ACQuire:MODe AVErage", ScpiEvent(221, "Settings conflict"))

        assertTrue(error is ScopeError.ExecutionNotAllowed)
    }

    @Test
    fun `波形が無効な状態を判別する`() {
        val error = ScpiErrorQueue.toScopeError("CURVe?", ScpiEvent(2241, "Waveform requested is invalid"))

        assertTrue(error is ScopeError.WaveformNotAvailable)
    }

    @Test
    fun `Query error は同期喪失として扱い再接続を促す`() {
        val error = ScpiErrorQueue.toScopeError("CURVe?", ScpiEvent(420, "Query UNTERMINATED"))

        assertTrue(error is ScopeError.StreamDesynchronized)
        assertTrue(error.isRetryable)
    }

    @Test
    fun `測定計算中は Busy として扱う`() {
        val error = ScpiErrorQueue.toScopeError(
            "MEASUrement:MEAS1:VALue?",
            ScpiEvent(2224, "Measurement error, WAIT calculating"),
        )

        assertTrue(error is ScopeError.InstrumentBusy)
    }
}

class ScpiDangerClassifierTest {

    @Test
    fun `問い合わせは安全と判定する`() {
        assertEquals(DangerLevel.SAFE, ScpiDangerClassifier.classify("*IDN?"))
        assertEquals(DangerLevel.SAFE, ScpiDangerClassifier.classify("CH1:SCAle?"))
        assertEquals(DangerLevel.SAFE, ScpiDangerClassifier.classify("CONFIGuration:AFG?"))
    }

    @Test
    fun `通常の設定変更は状態変更と判定する`() {
        assertEquals(DangerLevel.STATE_CHANGE, ScpiDangerClassifier.classify("CH1:SCAle 0.1"))
        assertEquals(DangerLevel.STATE_CHANGE, ScpiDangerClassifier.classify("ACQuire:STATE RUN"))
    }

    @Test
    fun `全設定を失うコマンドは危険と判定する`() {
        assertEquals(DangerLevel.DANGEROUS, ScpiDangerClassifier.classify("*RST"))
        assertEquals(DangerLevel.DANGEROUS, ScpiDangerClassifier.classify("FACTORY"))
        assertEquals(DangerLevel.DANGEROUS, ScpiDangerClassifier.classify("AUTOSet EXECute"))
    }

    @Test
    fun `ファイル削除は危険と判定する`() {
        assertEquals(DangerLevel.DANGEROUS, ScpiDangerClassifier.classify("FILESystem:DELEte \"E:/setup.set\""))
    }

    @Test
    fun `AFG 出力の有効化のみ危険と判定する`() {
        assertEquals(DangerLevel.DANGEROUS, ScpiDangerClassifier.classify("AFG:OUTPut:STATE ON"))
        assertEquals(DangerLevel.DANGEROUS, ScpiDangerClassifier.classify("AFG:OUTPut:STATE 1"))
        // 出力を切る操作は危険ではない。
        assertEquals(DangerLevel.STATE_CHANGE, ScpiDangerClassifier.classify("AFG:OUTPut:STATE OFF"))
    }

    @Test
    fun `Write コマンドは既定で危険度を自動判定する`() {
        assertEquals(DangerLevel.DANGEROUS, ScpiCommand.Write("*RST").dangerLevel)
        assertEquals(DangerLevel.STATE_CHANGE, ScpiCommand.Write("CH1:SCAle 0.1").dangerLevel)
    }
}
