package com.pdtoscillo.core.model

/**
 * `*IDN?` の解析結果。
 *
 * 一般に `TEKTRONIX,<model>,<serial>,<firmware>` のカンマ区切りで返るが、要素数が異なる機種でも
 * 例外にせず、取得できた分だけを保持する。`raw` は診断表示と実機ログのために常に残す。
 */
data class InstrumentIdentity(
    val manufacturer: String,
    val model: String,
    val serialNumber: String?,
    val firmwareVersion: String?,
    val raw: String,
) {
    val isTektronix: Boolean
        get() = manufacturer.contains("TEKTRONIX", ignoreCase = true) ||
            manufacturer.contains("TEK", ignoreCase = true)

    companion object {
        /** 解析に失敗した場合でも生応答は残す。 */
        fun unparsed(raw: String): InstrumentIdentity = InstrumentIdentity(
            manufacturer = "",
            model = "",
            serialNumber = null,
            firmwareVersion = null,
            raw = raw,
        )
    }
}

/**
 * 4000 シリーズの世代分類。
 *
 * 実機モデルが未確定のため、特定モデル決め打ちを避けて世代単位で扱う。
 * 世代はコマンド対応の目安であり、最終的な判断は Capability 検出結果に従う。
 */
enum class ModelFamily {
    /** DPO4000 / MSO4000（無印）。 */
    GEN1_DPO_MSO_4000,

    /** DPO4000B / MSO4000B / MDO4000。 */
    GEN2_4000B_MDO4000,

    /** MDO4000B / MDO4000C。 */
    GEN3_MDO4000BC,

    /** 4000 系だが世代を特定できなかった。 */
    UNKNOWN_4000,

    /** 4000 系ではない、または Tektronix ではない。 */
    UNSUPPORTED,
    ;

    val isTektronix4000: Boolean
        get() = this != UNSUPPORTED
}

/** モデル名から読み取れる構成。Capability 検出のフォールバックに使う。 */
data class ModelNameHints(
    val family: ModelFamily,
    val seriesPrefix: String?,
    val analogChannelCount: Int?,
    val hasDigitalChannels: Boolean?,
    val hasRfChannel: Boolean?,
)
