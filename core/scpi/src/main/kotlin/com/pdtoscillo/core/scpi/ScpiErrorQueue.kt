package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.ScopeError

/** SCPI イベント 1 件。 */
data class ScpiEvent(val code: Int, val message: String) {
    val category: Category
        get() = when (code) {
            0, 1 -> Category.NONE
            in COMMAND_ERROR_RANGE -> Category.COMMAND_ERROR
            in EXECUTION_ERROR_RANGE -> Category.EXECUTION_ERROR
            in DEVICE_ERROR_RANGE -> Category.DEVICE_ERROR
            in QUERY_ERROR_RANGE -> Category.QUERY_ERROR
            in TEK_EXECUTION_ERROR_RANGE -> Category.EXECUTION_ERROR
            else -> Category.OTHER
        }

    enum class Category { NONE, COMMAND_ERROR, EXECUTION_ERROR, DEVICE_ERROR, QUERY_ERROR, OTHER }

    val isEmptyQueueMarker: Boolean get() = code == 0 || code == 1

    companion object {
        /** Programmer Manual の Table 3-5 〜 3-8 で確認した区分。 */
        private val COMMAND_ERROR_RANGE = 100..199
        private val EXECUTION_ERROR_RANGE = 200..299
        private val DEVICE_ERROR_RANGE = 300..399
        private val QUERY_ERROR_RANGE = 400..499
        private val TEK_EXECUTION_ERROR_RANGE = 2200..2299
    }
}

/**
 * SCPI エラーキューの読み出しと分類。
 *
 * イベントコードは Programmer Manual（MDO4000/B/C, MSO/DPO4000B, MDO3000）の
 * Table 3-4 〜 Table 3-6 で確認した値のみを使う。
 *
 * **キューを無限に読み続けない。** 読み出し自体が新たなイベントを生む場合があるため、
 * 1 回の確認で読む件数に上限を設ける。
 */
class ScpiErrorQueue(private val queue: ScpiCommandQueue) {
    /**
     * キューに溜まったイベントを最大 [maxEvents] 件読む。
     *
     * `ALLEv?` は 1 回で全件返すが、機種によって応答が長大になるため、
     * 既定では `EVMsg?` を上限付きで繰り返す。
     */
    suspend fun readEvents(maxEvents: Int = DEFAULT_MAX_EVENTS): List<ScpiEvent> {
        val events = mutableListOf<ScpiEvent>()
        repeat(maxEvents) {
            val response = try {
                queue.query(ScpiCommand.Query(EVENT_MESSAGE_QUERY, timeoutMillis = EVENT_QUERY_TIMEOUT_MILLIS))
            } catch (_: ScpiException) {
                // エラーキューの確認自体が失敗した場合、そこで打ち切る。無限に試さない。
                return events
            }
            val event = parseEvent(response) ?: return events
            if (event.isEmptyQueueMarker) return events
            events += event
        }
        return events
    }

    /** キューを空にする。設定変更の直前に呼び、その後のエラーが当該操作由来だと分かるようにする。 */
    suspend fun clear() {
        runCatching { queue.write(ScpiCommand.Write(CLEAR_STATUS, timeoutMillis = EVENT_QUERY_TIMEOUT_MILLIS)) }
    }

    /** 標準イベントステータスレジスタ。ビットが立っていればキューに何か入っている。 */
    suspend fun readStandardEventStatus(): Int? {
        val response = runCatching {
            queue.query(ScpiCommand.Query(EVENT_STATUS_QUERY, timeoutMillis = EVENT_QUERY_TIMEOUT_MILLIS))
        }.getOrNull() ?: return null
        return ScpiResponseParser.parseInt(response)
    }

    /**
     * 直前のコマンドに対するエラーを分類する。
     *
     * 未対応機能の検出に使う。イベントが無ければ null（＝エラーではない）。
     */
    suspend fun classifyLatest(command: String, maxEvents: Int = DEFAULT_MAX_EVENTS): ScopeError? {
        val events = readEvents(maxEvents)
        val relevant = events.firstOrNull { !it.isEmptyQueueMarker } ?: return null
        return toScopeError(command, relevant)
    }

    companion object {
        /** `EVMsg?`: 先頭のイベントを 1 件取り出す。 */
        const val EVENT_MESSAGE_QUERY = "EVMsg?"

        /** `ALLEv?`: 溜まっている全イベントを取り出す。 */
        const val ALL_EVENTS_QUERY = "ALLEv?"

        /** `EVQty?`: キューの件数。 */
        const val EVENT_QUANTITY_QUERY = "EVQty?"

        /** `*ESR?`: 標準イベントステータスレジスタ。 */
        const val EVENT_STATUS_QUERY = "*ESR?"

        const val CLEAR_STATUS = "*CLS"

        const val DEFAULT_MAX_EVENTS: Int = 10
        private const val EVENT_QUERY_TIMEOUT_MILLIS = 2_000L

        /** Table 3-5: 未定義ヘッダー。機種がそのコマンドを持たない。 */
        const val CODE_UNDEFINED_HEADER = 113

        /** Table 3-6 で確認した主要な実行エラー。 */
        const val CODE_SETTINGS_CONFLICT = 221
        const val CODE_DATA_OUT_OF_RANGE = 222
        const val CODE_ILLEGAL_PARAMETER_VALUE = 224
        const val CODE_HARDWARE_MISSING = 241

        /** 波形が取得できない状態を示すコード。 */
        private val WAVEFORM_UNAVAILABLE_CODES = setOf(2225, 2226, 2233, 2241, 2244, 2245)
        private const val CODE_MEASUREMENT_WAIT = 2224

        /**
         * `EVMsg?` の応答を解析する。
         *
         * 形式: `<code>,"<message>"`。メッセージ内にカンマが含まれるため、
         * 最初のカンマだけで分割する。
         */
        fun parseEvent(response: String): ScpiEvent? {
            val body = ScpiResponseParser.stripHeader(response).trim()
            if (body.isEmpty()) return null
            val separatorIndex = body.indexOf(',')
            if (separatorIndex < 0) {
                val onlyCode = body.toIntOrNull() ?: return null
                return ScpiEvent(onlyCode, "")
            }
            val code = body.substring(0, separatorIndex).trim().toIntOrNull() ?: return null
            val message = ScpiResponseParser.unquote(body.substring(separatorIndex + 1))
            return ScpiEvent(code, message)
        }

        /** イベントをアプリのエラー分類へ写す。 */
        @Suppress("CyclomaticComplexMethod")
        fun toScopeError(command: String, event: ScpiEvent): ScopeError = when {
            event.isEmptyQueueMarker -> ScopeError.Unknown("イベントなし")

            event.code == CODE_UNDEFINED_HEADER ->
                ScopeError.UndefinedHeader(command, event.code, event.message)

            // その他のコマンドエラーも「この機種はこのコマンドを受け付けない」ことを意味する。
            event.category == ScpiEvent.Category.COMMAND_ERROR ->
                ScopeError.UndefinedHeader(command, event.code, event.message)

            event.code == CODE_HARDWARE_MISSING -> ScopeError.OptionNotInstalled(event.message)

            event.code == CODE_DATA_OUT_OF_RANGE || event.code == CODE_ILLEGAL_PARAMETER_VALUE ->
                ScopeError.ArgumentOutOfRange(command, event.message)

            event.code == CODE_SETTINGS_CONFLICT -> ScopeError.ExecutionNotAllowed(command, event.message)

            event.code in WAVEFORM_UNAVAILABLE_CODES -> ScopeError.WaveformNotAvailable(event.message)

            event.code == CODE_MEASUREMENT_WAIT -> ScopeError.InstrumentBusy(event.message)

            // Query error（410 Query INTERRUPTED / 420 Query UNTERMINATED など）は
            // 送受信の対応が崩れていることを示す。再接続が必要。
            event.category == ScpiEvent.Category.QUERY_ERROR ->
                ScopeError.StreamDesynchronized("${event.code} ${event.message}")

            event.category == ScpiEvent.Category.EXECUTION_ERROR ->
                ScopeError.ExecutionNotAllowed(command, "${event.code} ${event.message}")

            event.category == ScpiEvent.Category.DEVICE_ERROR ->
                ScopeError.Unknown("${event.code} ${event.message}")

            else -> ScopeError.Unknown("${event.code} ${event.message}")
        }
    }
}
