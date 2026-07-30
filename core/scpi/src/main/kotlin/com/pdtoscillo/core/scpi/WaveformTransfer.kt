package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.WaveformDomain
import com.pdtoscillo.core.model.WaveformSource
import com.pdtoscillo.core.waveform.Waveform
import com.pdtoscillo.core.waveform.WaveformDecodeException
import com.pdtoscillo.core.waveform.WaveformEncoding
import com.pdtoscillo.core.waveform.WaveformPreamble

/** 波形転送の設定。 */
data class WaveformTransferConfig(
    /** 取得開始点（1 始まり）。 */
    val startPoint: Int = 1,
    /** 取得終了点。null なら本体のレコード長をそのまま使う。 */
    val stopPoint: Int? = null,
    /** 1 点あたりのバイト数。1 または 2。 */
    val bytesPerPoint: Int = 1,
    /** バイナリを優先する。ASCII は 100 万点までという制限がある。 */
    val useBinary: Boolean = true,
) {
    init {
        require(startPoint >= 1) { "開始点は 1 以上である必要があります" }
        require(bytesPerPoint in 1..2) { "1 点あたりのバイト数は 1 または 2 です" }
    }
}

/** 取得結果。プリアンブルも一緒に返し、保存時のメタデータに使う。 */
data class WaveformCapture(
    val waveform: Waveform,
    val preamble: WaveformPreamble,
    /** 転送されたバイト数。スループット表示に使う。 */
    val transferredBytes: Int,
    val elapsedMillis: Long,
)

/**
 * 波形転送。
 *
 * マニュアル記載の手順に従う。
 * ```text
 * DATa:SOUrce <source>
 * DATa:STARt <start>
 * DATa:STOP <stop>
 * DATa:ENCdg <encoding>
 * DATa:WIDth <bytes>
 * WFMOutpre?
 * CURVe?
 * ```
 *
 * 重要な点:
 * - **設定した値ではなくプリアンブルの値でデコードする。** 本体が要求を丸めることがあるため、
 *   `WFMOutpre?` を必ず読み、その内容に従って解釈する。
 * - 転送設定（`DATa:*`）は本体の状態を変えるため、読み取り専用モードでは送れない。
 *   その場合は現在の設定のまま `WFMOutpre?` と `CURVe?` だけで取得する。
 */
class WaveformTransfer(private val client: ScpiClient) {

    /**
     * 波形を 1 つ取得する。
     *
     * @param configureTransfer false なら `DATa:*` を送らず、本体の現在の設定のまま取得する。
     *   読み取り専用モードではこちらを使う。
     */
    suspend fun capture(
        source: WaveformSource,
        config: WaveformTransferConfig = WaveformTransferConfig(),
        configureTransfer: Boolean = true,
    ): WaveformCapture {
        val started = System.currentTimeMillis()

        if (configureTransfer) {
            applyTransferSettings(source, config)
        }

        val preambleResponse = client.queryText(TektronixCommands.Waveform.PREAMBLE_QUERY)
        val preamble = WfmOutpreParser.parse(preambleResponse)

        // 波形が非表示だと転送パラメータだけが返り、スケーリング項目が欠ける。
        // ここで気付けるようにしてから CURVe? を投げる。
        if (!preamble.hasVerticalScaling || !preamble.hasHorizontalScaling) {
            val missing = preamble.missingFields()
            val event = client.errorQueue.classifyLatest(TektronixCommands.Waveform.PREAMBLE_QUERY)
            throw ScpiException(
                event ?: ScopeError.WaveformNotAvailable(
                    "${source.displayName}: プリアンブルに ${missing.joinToString()} がありません。" +
                        "対象のチャンネルが表示状態か確認してください。",
                ),
            )
        }

        val useAscii = preamble.encoding == WaveformEncoding.ASCII
        val waveform: Waveform
        val transferredBytes: Int

        if (useAscii) {
            val response = client.queryText(TektronixCommands.Waveform.CURVE_QUERY)
            transferredBytes = response.length
            waveform = decode(source, preamble) {
                com.pdtoscillo.core.waveform.WaveformDecoder.decodeAscii(source, preamble, response)
            }
        } else {
            val payload = client.queryBinary(TektronixCommands.Waveform.CURVE_QUERY)
            transferredBytes = payload.size
            waveform = decode(source, preamble) {
                com.pdtoscillo.core.waveform.WaveformDecoder.decodeBinary(source, preamble, payload)
            }
        }

        // 宣言点数と実際の点数が食い違う場合、転送が途中で切れている可能性がある。
        // 黙って短い波形を返すと「なぜか波形が短い」という分かりにくい不具合になる。
        if (preamble.pointCountMismatch(waveform.pointCount)) {
            throw ScpiException(
                ScopeError.MalformedBinaryBlock(
                    "${source.displayName}: プリアンブルは ${preamble.pointCount} 点と宣言していますが、" +
                        "実際に取得できたのは ${waveform.pointCount} 点です。転送が途中で切れた可能性があります。",
                ),
            )
        }

        val elapsed = System.currentTimeMillis() - started
        PdtLog.d(
            TAG,
            "${source.displayName}: ${waveform.pointCount} 点 / $transferredBytes バイト / $elapsed ms",
        )
        return WaveformCapture(waveform, preamble, transferredBytes, elapsed)
    }

    private inline fun decode(source: WaveformSource, preamble: WaveformPreamble, block: () -> Waveform): Waveform = try {
        block()
    } catch (error: WaveformDecodeException) {
        throw ScpiException(
            ScopeError.MalformedBinaryBlock(
                "${source.displayName} のデコードに失敗しました: ${error.message}（プリアンブル: ${preamble.raw.take(RAW_PREVIEW)}）",
            ),
        )
    }

    private suspend fun applyTransferSettings(source: WaveformSource, config: WaveformTransferConfig) {
        client.write("${TektronixCommands.Waveform.DATA_SOURCE} ${source.scpiValue}")
        client.write("${TektronixCommands.Waveform.DATA_START} ${config.startPoint}")

        val stop = config.stopPoint ?: resolveRecordLength()
        if (stop != null) {
            client.write("${TektronixCommands.Waveform.DATA_STOP} $stop")
        }

        val encoding = resolveEncoding(source, config)
        client.write("${TektronixCommands.Waveform.DATA_ENCODING} $encoding")
        client.write("${TektronixCommands.Waveform.DATA_WIDTH} ${config.bytesPerPoint}")
    }

    /**
     * エンコーディングを決める。
     *
     * RF 周波数領域は浮動小数（FPbinary）で受け取る必要がある。整数として受け取ると
     * 桁の違う値になる。時間領域は符号付き整数（RIBinary）が既定。
     */
    private fun resolveEncoding(source: WaveformSource, config: WaveformTransferConfig): String = when {
        !config.useBinary -> TektronixCommands.Waveform.Encoding.ASCII
        source.domain == WaveformDomain.RF_FREQUENCY -> TektronixCommands.Waveform.Encoding.FLOAT_BIG_ENDIAN
        else -> TektronixCommands.Waveform.Encoding.SIGNED_BIG_ENDIAN
    }

    private suspend fun resolveRecordLength(): Int? = runCatching {
        client.queryLong(TektronixCommands.Horizontal.RECORD_LENGTH_QUERY)?.toInt()
    }.getOrNull()

    private companion object {
        const val TAG = "WaveformTransfer"
        const val RAW_PREVIEW = 120
    }
}
