package com.pdtoscillo.core.scpi

/**
 * 計測器へ送る 1 コマンド。
 *
 * 設定変更（[Write]）と問い合わせ（[Query] / [BinaryQuery]）を型で区別する。
 * 「応答を待つべきか」を実行時の文字列判定に頼らず、呼び出し側が明示する。
 */
sealed interface ScpiCommand {
    val text: String

    /** null ならこのコマンド種別の既定タイムアウトを使う。 */
    val timeoutMillis: Long?

    val dangerLevel: DangerLevel

    /** 応答を待たない設定変更。 */
    data class Write(
        override val text: String,
        override val timeoutMillis: Long? = null,
        override val dangerLevel: DangerLevel = ScpiDangerClassifier.classify(text),
    ) : ScpiCommand

    /** テキスト応答を待つ問い合わせ。 */
    data class Query(override val text: String, override val timeoutMillis: Long? = null) : ScpiCommand {
        override val dangerLevel: DangerLevel get() = DangerLevel.SAFE
    }

    /** IEEE 488.2 バイナリブロックを待つ問い合わせ。波形転送に使う。 */
    data class BinaryQuery(override val text: String, override val timeoutMillis: Long? = null) : ScpiCommand {
        override val dangerLevel: DangerLevel get() = DangerLevel.SAFE
    }

    /**
     * 未対応かどうかを調べるための問い合わせ。
     *
     * 実機は未定義ヘッダーに対して**応答を返さない**（イベントキューへ 113 を積むだけ）。
     * したがって未対応の機能を調べる問い合わせは必ずタイムアウトする。
     * 通常の Query と同じ長いタイムアウトを使うと検出に時間がかかりすぎるため、
     * 短いタイムアウトを既定にした専用の種別を設ける。
     */
    data class ProbeQuery(override val text: String, override val timeoutMillis: Long? = DEFAULT_PROBE_TIMEOUT_MILLIS) : ScpiCommand {
        override val dangerLevel: DangerLevel get() = DangerLevel.SAFE
    }

    val isQuery: Boolean get() = this !is Write

    companion object {
        /** 未対応検出用の短いタイムアウト。 */
        const val DEFAULT_PROBE_TIMEOUT_MILLIS: Long = 1_200
    }
}

/**
 * コマンドの危険度。
 *
 * 読み取り専用モードの判定と、確認ダイアログの要否に使う。
 */
enum class DangerLevel {
    /** 状態を変えない問い合わせ。 */
    SAFE,

    /** 計測器の設定を変える。読み取り専用モードでは拒否する。 */
    STATE_CHANGE,

    /** 設定の一括変更、出力の有効化、ファイル削除など、取り返しがつきにくい操作。確認を必須にする。 */
    DANGEROUS,
}

/**
 * コマンド文字列から危険度を推定する。
 *
 * 推定は保守的に行う。判断できないものは [DangerLevel.STATE_CHANGE] として扱い、
 * 読み取り専用モードでは拒否する。
 */
object ScpiDangerClassifier {
    /**
     * 全設定を失う、出力を有効にする、データを消すコマンド。
     * いずれもマニュアルで存在を確認したコマンドのみを列挙する。
     */
    private val DANGEROUS_PREFIXES = listOf(
        "*RST",
        "FACTORY",
        "AUTOSET",
        "FILESYSTEM:DELETE",
        "FILESYSTEM:DELWARN",
        "FILESYSTEM:FORMAT",
        "FILESYSTEM:REName",
        "RECALL:SETUP",
        "RECALL:WAVEFORM",
        "*CAL",
        "DIAG:STATE",
        "CLEARMENU",
    )

    /** 出力を有効化する操作。値が ON / 1 のときだけ危険とみなす。 */
    private val OUTPUT_ENABLE_PREFIXES = listOf(
        "AFG:OUTPUT:STATE",
        "AFG:OUTPUT",
    )

    fun classify(text: String): DangerLevel {
        val normalized = text.trim().trimStart(':').uppercase()
        if (normalized.endsWith("?")) return DangerLevel.SAFE

        val head = normalized.substringBefore(' ')
        val argument = normalized.substringAfter(' ', missingDelimiterValue = "").trim()

        if (DANGEROUS_PREFIXES.any { head.startsWith(it) }) return DangerLevel.DANGEROUS
        if (OUTPUT_ENABLE_PREFIXES.any { head.startsWith(it) } && argument in ENABLE_VALUES) {
            return DangerLevel.DANGEROUS
        }
        return DangerLevel.STATE_CHANGE
    }

    private val ENABLE_VALUES = setOf("ON", "1")
}
