package com.pdtoscillo.core.waveform

/** バイナリの数値表現。`WFMOutpre:BN_Fmt` に対応する。 */
enum class BinaryFormat {
    /** 符号付き整数。 */
    SIGNED_INTEGER,

    /** 符号なし整数。 */
    UNSIGNED_INTEGER,

    /** 浮動小数。RF 周波数領域トレースで使われる。 */
    FLOATING_POINT,
    ;

    companion object {
        fun fromScpi(value: String): BinaryFormat? = when (value.trim().trim('"').uppercase()) {
            "RI" -> SIGNED_INTEGER
            "RP" -> UNSIGNED_INTEGER
            "FP" -> FLOATING_POINT
            else -> null
        }
    }
}

/** バイト順。`WFMOutpre:BYT_Or` に対応する。 */
enum class ByteOrder {
    /** 最上位バイトが先。 */
    MSB_FIRST,

    /** 最下位バイトが先。 */
    LSB_FIRST,
    ;

    val isBigEndian: Boolean get() = this == MSB_FIRST

    companion object {
        fun fromScpi(value: String): ByteOrder? = when (value.trim().trim('"').uppercase()) {
            "MSB" -> MSB_FIRST
            "LSB" -> LSB_FIRST
            else -> null
        }
    }
}

/** データの符号化。`WFMOutpre:ENCdg` に対応する。 */
enum class WaveformEncoding {
    ASCII,
    BINARY,
    ;

    companion object {
        fun fromScpi(value: String): WaveformEncoding? {
            val normalized = value.trim().trim('"').uppercase()
            return when {
                normalized.startsWith("ASC") -> ASCII
                normalized.startsWith("BIN") -> BINARY
                else -> null
            }
        }
    }
}

/**
 * `WFMOutpre?` が返す波形プリアンブル。
 *
 * このデータが無ければ生データを電圧・時間へ戻せない。値を推測で埋めず、
 * 取れなかった項目は null のままにして、変換時に明示的に失敗させる。
 *
 * マニュアル記載のスケーリング式:
 * ```text
 * Xn = XZEro + XINcr (n - PT_Off)
 * Yn = YZEro + YMUlt (yn - YOFf)
 * ```
 */
data class WaveformPreamble(
    val bytesPerPoint: Int?,
    val bitsPerPoint: Int?,
    val encoding: WaveformEncoding?,
    val binaryFormat: BinaryFormat?,
    val byteOrder: ByteOrder?,
    val pointCount: Int?,
    val pointFormat: String?,
    val waveformId: String?,
    val xUnit: String?,
    val xIncrement: Double?,
    val xZero: Double?,
    val pointOffset: Int?,
    val yUnit: String?,
    val yMultiplier: Double?,
    val yOffset: Double?,
    val yZero: Double?,
    val raw: String,
) {
    /**
     * 1 点が最大・最小の対で送られてくる形式か。
     *
     * Peak Detect / Envelope では `PT_FMT` が `ENV` になり、点数の 2 倍のデータが届く。
     * これを普通の波形として扱うと、時間軸が 2 倍にずれた波形になる。
     */
    val isEnvelope: Boolean get() = pointFormat?.trim()?.trim('"')?.uppercase()?.startsWith("ENV") == true

    /** 電圧へ戻すために最低限必要な値が揃っているか。 */
    val hasVerticalScaling: Boolean get() = yMultiplier != null && yOffset != null && yZero != null

    /** 時間軸を作るために最低限必要な値が揃っているか。 */
    val hasHorizontalScaling: Boolean get() = xIncrement != null && xZero != null && pointOffset != null

    /**
     * デコードに欠かせない項目のうち、欠けているもの。
     *
     * `NR_PT` は含めない。実際の点数は受信したデータ長から決まるため、
     * これが無くてもデコードはできる。点数の食い違いは [pointCountMismatch] で別に扱う。
     */
    fun missingFields(): List<String> = buildList {
        if (bytesPerPoint == null) add("BYT_NR")
        if (encoding == null) add("ENCDG")
        if (encoding == WaveformEncoding.BINARY && binaryFormat == null) add("BN_FMT")
        if (encoding == WaveformEncoding.BINARY && byteOrder == null) add("BYT_OR")
        if (xIncrement == null) add("XINCR")
        if (xZero == null) add("XZERO")
        if (pointOffset == null) add("PT_OFF")
        if (yMultiplier == null) add("YMULT")
        if (yOffset == null) add("YOFF")
        if (yZero == null) add("YZERO")
    }

    /**
     * プリアンブルの宣言点数と実際に取得できた点数が食い違っているか。
     *
     * 食い違いは転送が途中で切れた可能性を示す。黙って短い波形を返すと、
     * 「なぜか波形が短い」という分かりにくい不具合になる。
     */
    fun pointCountMismatch(actualPointCount: Int): Boolean {
        val declared = pointCount ?: return false
        if (declared <= 0) return false
        // Envelope は 1 点につき 2 値が届くため、点数は宣言値と一致する想定。
        return declared != actualPointCount
    }

    companion object {
        /** ヘッダなし応答を位置で解釈するときの並び。マニュアルの例と同じ順序。 */
        val POSITIONAL_FIELDS = listOf(
            "BYT_NR", "BIT_NR", "ENCDG", "BN_FMT", "BYT_OR", "WFID", "NR_PT", "PT_FMT",
            "XUNIT", "XINCR", "XZERO", "PT_OFF", "YUNIT", "YMULT", "YOFF", "YZERO",
        )
    }
}
