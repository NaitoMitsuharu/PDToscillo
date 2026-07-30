package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.waveform.BinaryFormat
import com.pdtoscillo.core.waveform.ByteOrder
import com.pdtoscillo.core.waveform.WaveformEncoding
import com.pdtoscillo.core.waveform.WaveformPreamble

/**
 * `WFMOutpre?` の応答を [WaveformPreamble] へ変換する。
 *
 * 応答形式は `HEADer` の設定で変わるため、両方に対応する。
 *
 * ヘッダあり:
 * ```text
 * :WFMOUTPRE:BYT_NR 2;BIT_NR 16;ENCDG ASCII;BN_FMT RI;BYT_OR MSB;WFID "Ch1, ...";NR_PT 10000;...
 * ```
 *
 * ヘッダなし（値だけがマニュアル記載の順に並ぶ）:
 * ```text
 * 2;16;ASCII;RI;MSB;"Ch1, ...";10000;Y;"s";4.0000E-9;-20.0000E-6;0;"V";15.6250E-6;6.4000E+3;0.0000
 * ```
 *
 * ヘッダの有無を送信側で固定するには `HEADer` を送る必要があるが、それは設定変更にあたる。
 * 読み取り専用モードでも波形を取れるようにするため、**どちらの形式でも解析できるようにする。**
 */
object WfmOutpreParser {

    fun parse(response: String): WaveformPreamble {
        val fields = ScpiResponseParser.parseHeaderedFields(response)
        return if (fields.size >= MINIMUM_HEADERED_FIELDS) {
            fromFields(fields, response)
        } else {
            fromPositional(response)
        }
    }

    /** ヘッダ付き応答。フィールド名で引く。 */
    private fun fromFields(fields: Map<String, String>, raw: String): WaveformPreamble {
        fun field(name: String): String? = fields[name]?.trim()?.takeIf { it.isNotEmpty() }

        return WaveformPreamble(
            bytesPerPoint = field("BYT_NR")?.toIntOrNull(),
            bitsPerPoint = field("BIT_NR")?.toIntOrNull(),
            encoding = field("ENCDG")?.let(WaveformEncoding::fromScpi),
            binaryFormat = field("BN_FMT")?.let(BinaryFormat::fromScpi),
            byteOrder = field("BYT_OR")?.let(ByteOrder::fromScpi),
            pointCount = field("NR_PT")?.toIntOrNull(),
            pointFormat = field("PT_FMT"),
            waveformId = field("WFID")?.let(ScpiResponseParser::unquote),
            xUnit = field("XUNIT")?.let(ScpiResponseParser::unquote),
            xIncrement = field("XINCR")?.toDoubleOrNull(),
            xZero = field("XZERO")?.toDoubleOrNull(),
            pointOffset = field("PT_OFF")?.toIntOrNull(),
            yUnit = field("YUNIT")?.let(ScpiResponseParser::unquote),
            yMultiplier = field("YMULT")?.toDoubleOrNull(),
            yOffset = field("YOFF")?.toDoubleOrNull(),
            yZero = field("YZERO")?.toDoubleOrNull(),
            raw = raw,
        )
    }

    /**
     * ヘッダなし応答。位置で解釈する。
     *
     * 要素数が想定と違う場合でも例外にせず、取れた分だけを埋める。
     * 足りない項目は null のままにし、変換時に「何が足りないか」を示せるようにする。
     */
    private fun fromPositional(raw: String): WaveformPreamble {
        val values = ScpiResponseParser.splitSemicolons(ScpiResponseParser.stripHeader(raw))
        fun at(name: String): String? {
            val index = WaveformPreamble.POSITIONAL_FIELDS.indexOf(name)
            if (index < 0 || index >= values.size) return null
            return values[index].trim().takeIf { it.isNotEmpty() }
        }

        return WaveformPreamble(
            bytesPerPoint = at("BYT_NR")?.toIntOrNull(),
            bitsPerPoint = at("BIT_NR")?.toIntOrNull(),
            encoding = at("ENCDG")?.let(WaveformEncoding::fromScpi),
            binaryFormat = at("BN_FMT")?.let(BinaryFormat::fromScpi),
            byteOrder = at("BYT_OR")?.let(ByteOrder::fromScpi),
            pointCount = at("NR_PT")?.toIntOrNull(),
            pointFormat = at("PT_FMT"),
            waveformId = at("WFID")?.let(ScpiResponseParser::unquote),
            xUnit = at("XUNIT")?.let(ScpiResponseParser::unquote),
            xIncrement = at("XINCR")?.toDoubleOrNull(),
            xZero = at("XZERO")?.toDoubleOrNull(),
            pointOffset = at("PT_OFF")?.toIntOrNull(),
            yUnit = at("YUNIT")?.let(ScpiResponseParser::unquote),
            yMultiplier = at("YMULT")?.toDoubleOrNull(),
            yOffset = at("YOFF")?.toDoubleOrNull(),
            yZero = at("YZERO")?.toDoubleOrNull(),
            raw = raw,
        )
    }

    /** ヘッダ付きと判定するのに必要な最小フィールド数。 */
    private const val MINIMUM_HEADERED_FIELDS = 8
}
