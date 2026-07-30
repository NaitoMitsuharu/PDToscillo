package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.InstrumentIdentity
import com.pdtoscillo.core.model.ModelFamily
import com.pdtoscillo.core.model.ModelNameHints

/**
 * `*IDN?` の解析。
 *
 * 一般には `TEKTRONIX,MDO4104C,C012345,CF:91.1CT FV:v1.28` の 4 要素だが、
 * 要素数が異なる応答でも例外にせず取得できた分だけを保持する。
 */
object IdnParser {
    fun parse(response: String): InstrumentIdentity {
        val raw = response.trim()
        if (raw.isEmpty()) return InstrumentIdentity.unparsed(raw)

        val parts = ScpiResponseParser.splitCommas(ScpiResponseParser.stripHeader(raw))
            .map { ScpiResponseParser.unquote(it) }
        if (parts.isEmpty()) return InstrumentIdentity.unparsed(raw)

        return InstrumentIdentity(
            manufacturer = parts.getOrNull(0)?.trim().orEmpty(),
            model = parts.getOrNull(1)?.trim().orEmpty(),
            serialNumber = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() },
            firmwareVersion = parts.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() },
            raw = raw,
        )
    }
}

/**
 * モデル名から構成を推定する。
 *
 * **これはフォールバックである。** `CONFIGuration:*?` が使える機種では常にそちらを優先する。
 *
 * Tektronix 4000 シリーズのモデル名は `<接頭辞>40<帯域コード><チャンネル数><世代>` の形を取る。
 *
 * | モデル | 推定 |
 * | --- | --- |
 * | DPO4032 | アナログ 2 ch、デジタルなし、RF なし、Gen1 |
 * | DPO4054 | アナログ 4 ch、デジタルなし、RF なし、Gen1 |
 * | MSO4104B | アナログ 4 ch、デジタル 16 ch、RF なし、Gen2 |
 * | MDO4104C | アナログ 4 ch、デジタルはオプション、RF あり、Gen3 |
 *
 * 推定できない場合は該当項目を null とし、呼び出し側が「不明」として無効化する。
 * 過大評価は誤操作につながるため、確信が持てない項目は null を返す。
 */
object ModelNameResolver {
    private val MODEL_PATTERN = Regex("""^(DPO|MSO|MDO)\s*(\d{4})\s*([A-Z]?)""", RegexOption.IGNORE_CASE)

    /** MSO の標準デジタルチャンネル数。 */
    const val MSO_DIGITAL_CHANNELS: Int = 16

    fun resolve(identity: InstrumentIdentity): ModelNameHints {
        if (!identity.isTektronix && identity.manufacturer.isNotEmpty()) {
            return ModelNameHints(ModelFamily.UNSUPPORTED, null, null, null, null)
        }

        val model = identity.model.trim().uppercase()
        val match = MODEL_PATTERN.find(model)
            ?: return ModelNameHints(ModelFamily.UNKNOWN_4000, null, null, null, null)

        val prefix = match.groupValues[1].uppercase()
        val digits = match.groupValues[2]
        val suffix = match.groupValues[3].uppercase()

        // 数字部は「シリーズ 1 桁 + 帯域コード 2 桁 + チャンネル数 1 桁」。
        // 例: DPO4054 → 4 系 / 500 MHz(05) / 4 ch、MSO4104 → 4 系 / 1 GHz(10) / 4 ch。
        // 帯域コードが 10 以上になる機種があるため、先頭 2 桁を "40" と決め打ってはならない。
        if (!digits.startsWith(SERIES_4000_DIGIT)) {
            return ModelNameHints(ModelFamily.UNSUPPORTED, prefix, null, null, null)
        }

        val analogChannels = digits.last().digitToIntOrNull()?.takeIf { it in VALID_CHANNEL_COUNTS }
        val family = resolveFamily(prefix, suffix)

        return ModelNameHints(
            family = family,
            seriesPrefix = prefix,
            analogChannelCount = analogChannels,
            // MSO はデジタル 16 ch が標準。MDO はオプションなので推定しない（null = 不明）。
            hasDigitalChannels = when (prefix) {
                "MSO" -> true
                "DPO" -> false
                else -> null
            },
            // MDO は RF 標準搭載。DPO / MSO は非搭載。
            hasRfChannel = when (prefix) {
                "MDO" -> true
                "DPO", "MSO" -> false
                else -> null
            },
        )
    }

    private fun resolveFamily(prefix: String, suffix: String): ModelFamily = when {
        prefix == "MDO" && suffix == "C" -> ModelFamily.GEN3_MDO4000BC
        prefix == "MDO" && suffix == "B" -> ModelFamily.GEN3_MDO4000BC
        prefix == "MDO" -> ModelFamily.GEN2_4000B_MDO4000
        suffix == "B" -> ModelFamily.GEN2_4000B_MDO4000
        suffix.isEmpty() -> ModelFamily.GEN1_DPO_MSO_4000
        else -> ModelFamily.UNKNOWN_4000
    }

    private val VALID_CHANNEL_COUNTS = setOf(2, 4)

    /** 数字部の先頭 1 桁がシリーズを表す。4000 シリーズは "4"。 */
    private const val SERIES_4000_DIGIT = "4"
}
