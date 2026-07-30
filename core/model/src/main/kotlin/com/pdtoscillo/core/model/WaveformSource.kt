package com.pdtoscillo.core.model

/**
 * 波形ソース。
 *
 * `scpiValue` は Tektronix Programmer Manual（MDO4000/B/C, MSO/DPO4000B, MDO3000）で
 * `DATa:SOUrce` の有効値として確認したものだけを持つ。推測した値は追加しない。
 *
 * 時間領域のアナログ波形、デジタル波形、RF 波形はデータ形式が異なるため `domain` で区別し、
 * 誤ったスケーリングを型レベルで防ぐ。
 */
enum class WaveformSource(val scpiValue: String, val domain: WaveformDomain, val displayName: String) {
    CH1("CH1", WaveformDomain.ANALOG_TIME, "CH1"),
    CH2("CH2", WaveformDomain.ANALOG_TIME, "CH2"),
    CH3("CH3", WaveformDomain.ANALOG_TIME, "CH3"),
    CH4("CH4", WaveformDomain.ANALOG_TIME, "CH4"),

    MATH("MATH", WaveformDomain.ANALOG_TIME, "Math"),

    REF1("REF1", WaveformDomain.ANALOG_TIME, "Ref1"),
    REF2("REF2", WaveformDomain.ANALOG_TIME, "Ref2"),
    REF3("REF3", WaveformDomain.ANALOG_TIME, "Ref3"),
    REF4("REF4", WaveformDomain.ANALOG_TIME, "Ref4"),

    D0("D0", WaveformDomain.DIGITAL, "D0"),
    D1("D1", WaveformDomain.DIGITAL, "D1"),
    D2("D2", WaveformDomain.DIGITAL, "D2"),
    D3("D3", WaveformDomain.DIGITAL, "D3"),
    D4("D4", WaveformDomain.DIGITAL, "D4"),
    D5("D5", WaveformDomain.DIGITAL, "D5"),
    D6("D6", WaveformDomain.DIGITAL, "D6"),
    D7("D7", WaveformDomain.DIGITAL, "D7"),
    D8("D8", WaveformDomain.DIGITAL, "D8"),
    D9("D9", WaveformDomain.DIGITAL, "D9"),
    D10("D10", WaveformDomain.DIGITAL, "D10"),
    D11("D11", WaveformDomain.DIGITAL, "D11"),
    D12("D12", WaveformDomain.DIGITAL, "D12"),
    D13("D13", WaveformDomain.DIGITAL, "D13"),
    D14("D14", WaveformDomain.DIGITAL, "D14"),
    D15("D15", WaveformDomain.DIGITAL, "D15"),

    /** デジタルチャンネル一括（Digital Collection）。1 点あたり 4 または 8 バイト。 */
    DIGITAL_COLLECTION("DIGital", WaveformDomain.DIGITAL_COLLECTION, "Digital"),

    RF_AMPLITUDE("RF_AMPlitude", WaveformDomain.RF_TIME, "RF Amplitude"),
    RF_FREQUENCY("RF_FREQuency", WaveformDomain.RF_TIME, "RF Frequency"),
    RF_PHASE("RF_PHASe", WaveformDomain.RF_TIME, "RF Phase"),

    RF_NORMAL("RF_NORMal", WaveformDomain.RF_FREQUENCY, "RF Normal"),
    RF_AVERAGE("RF_AVErage", WaveformDomain.RF_FREQUENCY, "RF Average"),
    RF_MAXHOLD("RF_MAXHold", WaveformDomain.RF_FREQUENCY, "RF Max Hold"),
    RF_MINHOLD("RF_MINHold", WaveformDomain.RF_FREQUENCY, "RF Min Hold"),
    ;

    val isAnalogChannel: Boolean get() = this in ANALOG_CHANNELS
    val isDigitalBit: Boolean get() = this in DIGITAL_BITS
    val isReference: Boolean get() = this in REFERENCES
    val isRf: Boolean get() = domain == WaveformDomain.RF_TIME || domain == WaveformDomain.RF_FREQUENCY

    companion object {
        val ANALOG_CHANNELS: List<WaveformSource> = listOf(CH1, CH2, CH3, CH4)
        val DIGITAL_BITS: List<WaveformSource> =
            listOf(D0, D1, D2, D3, D4, D5, D6, D7, D8, D9, D10, D11, D12, D13, D14, D15)
        val REFERENCES: List<WaveformSource> = listOf(REF1, REF2, REF3, REF4)

        /** 1 始まりのチャンネル番号から解決する。範囲外は null。 */
        fun analogChannel(number: Int): WaveformSource? = ANALOG_CHANNELS.getOrNull(number - 1)

        /** D0–D15 のビット番号から解決する。 */
        fun digitalBit(index: Int): WaveformSource? = DIGITAL_BITS.getOrNull(index)

        /** SCPI 応答に含まれる文字列（短縮形・大文字小文字の差を含む）から解決する。 */
        fun fromScpi(value: String): WaveformSource? {
            val normalized = value.trim().trim('"').uppercase()
            return entries.firstOrNull { it.scpiValue.uppercase() == normalized }
        }
    }
}

/**
 * 波形データの領域。
 *
 * デジタル波形や RF 周波数領域データをアナログ電圧波形として変換しないよう、
 * デコード時の分岐に必ずこれを使う。
 */
enum class WaveformDomain {
    /** 電圧対時間。 */
    ANALOG_TIME,

    /** 論理値対時間（1 ビット）。 */
    DIGITAL,

    /** 全デジタルチャンネルをまとめた 1 点あたり 4 または 8 バイトのデータ。 */
    DIGITAL_COLLECTION,

    /** RF の振幅・周波数・位相の時間領域データ。 */
    RF_TIME,

    /** RF 周波数領域（スペクトラム）。4 バイト浮動小数。 */
    RF_FREQUENCY,
}
