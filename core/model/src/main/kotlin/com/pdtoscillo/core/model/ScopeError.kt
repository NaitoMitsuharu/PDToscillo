package com.pdtoscillo.core.model

/**
 * アプリ全体で扱うエラー分類。
 *
 * UI では「何が起きたか」と「どうすればよいか」を同時に表示する必要があるため、
 * 分類そのものに対処方法を持たせる。文言のローカライズは UI 層が担当する。
 */
sealed class ScopeError(open val detail: String? = null, open val cause: Throwable? = null) {
    /** Ethernet が見つからない。 */
    data class EthernetUnavailable(override val detail: String? = null) : ScopeError(detail)

    /** ソケットを Ethernet へバインドできなかった。 */
    data class BindFailed(override val detail: String? = null, override val cause: Throwable? = null) : ScopeError(detail, cause)

    /** 接続がタイムアウトした。 */
    data class ConnectTimeout(val host: String, val port: Int, val timeoutMillis: Long) :
        ScopeError("$host:$port ($timeoutMillis ms)")

    /** TCP 接続を拒否された。Socket Server が無効な場合が多い。 */
    data class ConnectionRefused(val host: String, val port: Int, override val cause: Throwable? = null) :
        ScopeError("$host:$port", cause)

    /** 経路が確保できない（サブネット不一致など）。 */
    data class Unreachable(val host: String, override val cause: Throwable? = null) : ScopeError(host, cause)

    /** 接続後に切断された。 */
    data class Disconnected(override val detail: String? = null) : ScopeError(detail)

    /** 応答が返らなかった。 */
    data class ReadTimeout(val command: String?, val timeoutMillis: Long) : ScopeError("${command ?: "(不明)"} ($timeoutMillis ms)")

    /** 応答を読み切る前に中断され、ストリームの同期が失われた。再接続が必要。 */
    data class StreamDesynchronized(override val detail: String? = null) : ScopeError(detail)

    /** 利用者または上位処理によるキャンセル。 */
    data object Cancelled : ScopeError()

    /** 未定義コマンド。機種が対応していない。 */
    data class UndefinedHeader(val command: String, val eventCode: Int?, val message: String?) :
        ScopeError("$command -> ${eventCode ?: "?"} ${message ?: ""}".trim())

    /** 引数が範囲外。 */
    data class ArgumentOutOfRange(val command: String, val message: String?) : ScopeError("$command $message")

    /** 現在の状態では実行できない。 */
    data class ExecutionNotAllowed(val command: String, val message: String?) : ScopeError("$command $message")

    /** 対象の波形が無効（非表示など）。 */
    data class WaveformNotAvailable(val source: String) : ScopeError(source)

    /** オプション未搭載。 */
    data class OptionNotInstalled(val feature: String) : ScopeError(feature)

    /** 本体が処理中。 */
    data class InstrumentBusy(override val detail: String? = null) : ScopeError(detail)

    /** バイナリブロックの解析に失敗した。 */
    data class MalformedBinaryBlock(override val detail: String? = null) : ScopeError(detail)

    /** 応答の形式が想定と異なる。 */
    data class MalformedResponse(val command: String?, val response: String?) :
        ScopeError("${command ?: ""} -> ${response?.take(RESPONSE_PREVIEW_LENGTH) ?: ""}")

    /** 読み取り専用モードのため設定変更を拒否した。 */
    data class ReadOnlyModeRejected(val command: String) : ScopeError(command)

    /** 上記に当てはまらないもの。 */
    data class Unknown(override val detail: String? = null, override val cause: Throwable? = null) : ScopeError(detail, cause)

    /** 再試行しても状況が変わらない種類か。UI の再試行ボタン表示を切り替える。 */
    val isRetryable: Boolean
        get() = when (this) {
            is ConnectTimeout, is ReadTimeout, is Disconnected, is Unreachable,
            is StreamDesynchronized, is InstrumentBusy, is ConnectionRefused,
            -> true

            else -> false
        }

    /** 機種が対応していないことを示す種類か。Capability を更新する契機になる。 */
    val indicatesUnsupported: Boolean
        get() = this is UndefinedHeader || this is OptionNotInstalled

    companion object {
        private const val RESPONSE_PREVIEW_LENGTH = 120
    }
}
