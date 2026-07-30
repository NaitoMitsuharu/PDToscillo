package com.pdtoscillo.core.model

/**
 * トリガ種別。
 *
 * Tektronix 4000 系の SCPI では、トリガは「型」と「クラス」の 2 段構成になっている
 * （Programmer Manual: MDO4000/B/C, MSO/DPO4000B, MDO3000 で確認）。
 *
 * ```text
 * TRIGger:A:TYPe {EDGe|LOGIc|PULSe|BUS|VIDeo}
 * TRIGger:A:LOGIc:CLAss {LOGIC|SETHold}
 * TRIGger:A:PULse:CLAss {RUNt|WIDth|TRANsition|TIMEOut}
 * ```
 *
 * 画面上でユーザーが選ぶのは「エッジ」「パルス幅」「ラント」などの具体的な種別なので、
 * ここでは種別ごとに (型, クラス) の対応を持たせる。マニュアルで確認していない組み合わせは追加しない。
 */
enum class TriggerType(val scpiType: String, val scpiClass: String?, val displayName: String) {
    EDGE("EDGe", null, "エッジ"),

    LOGIC("LOGIc", "LOGIC", "ロジック"),
    SETUP_HOLD("LOGIc", "SETHold", "セットアップ/ホールド"),

    PULSE_WIDTH("PULSe", "WIDth", "パルス幅"),
    RUNT("PULSe", "RUNt", "ラント"),

    /** 立ち上がり／立ち下がり時間（Transition）。マニュアルの `TRANsition` クラス。 */
    TRANSITION("PULSe", "TRANsition", "立ち上がり/立ち下がり"),

    TIMEOUT("PULSe", "TIMEOut", "タイムアウト"),

    BUS("BUS", null, "バス"),

    VIDEO("VIDeo", null, "ビデオ"),
    ;

    /** 種別を切り替えるために必要な SCPI 設定の並び。 */
    fun applyCommands(triggerPrefix: String = "TRIGger:A"): List<String> = buildList {
        add("$triggerPrefix:TYPe $scpiType")
        scpiClass?.let { klass ->
            when (scpiType) {
                "LOGIc" -> add("$triggerPrefix:LOGIc:CLAss $klass")
                "PULSe" -> add("$triggerPrefix:PULse:CLAss $klass")
                else -> Unit
            }
        }
    }

    companion object {
        /** 全機種で使える基本のトリガ種別。 */
        val ALWAYS_AVAILABLE: Set<TriggerType> = setOf(EDGE, PULSE_WIDTH, RUNT, TRANSITION, TIMEOUT, LOGIC, SETUP_HOLD)

        /**
         * `TRIGger:A:TYPe?` と対応するクラスの問い合わせ結果から種別を解決する。
         * 応答は短縮形・大文字小文字が機種やファームウェアで異なるため前方一致で判定する。
         */
        fun resolve(typeResponse: String, classResponse: String?): TriggerType? {
            val type = typeResponse.trim().trim('"').uppercase()
            val klass = classResponse?.trim()?.trim('"')?.uppercase()
            return entries.firstOrNull { candidate ->
                val typeMatches = matchesScpiKeyword(type, candidate.scpiType)
                val classMatches = when {
                    candidate.scpiClass == null -> true
                    klass == null -> false
                    else -> matchesScpiKeyword(klass, candidate.scpiClass)
                }
                typeMatches && classMatches
            }
        }

        /**
         * SCPI キーワードの短縮形を許容して比較する。
         * マニュアル表記の大文字部分が最短形であるため、応答がその前方一致であれば同一とみなす。
         */
        internal fun matchesScpiKeyword(response: String, keyword: String): Boolean {
            val full = keyword.uppercase()
            val shortForm = keyword.filter { it.isUpperCase() || it.isDigit() }
            if (response == full) return true
            if (shortForm.isNotEmpty() && response == shortForm) return true
            return shortForm.isNotEmpty() && response.length in shortForm.length..full.length && full.startsWith(response)
        }
    }
}

/** バストリガのバス種別。`CONFIGuration:BUSWAVEFORMS:*?` で搭載を確認できたものだけ有効化する。 */
enum class BusType(val scpiValue: String, val displayName: String, val configurationQuery: String) {
    I2C("I2C", "I2C", "CONFIGuration:BUSWAVEFORMS:I2C?"),
    SPI("SPI", "SPI", "CONFIGuration:BUSWAVEFORMS:SPI?"),
    RS232("RS232C", "UART / RS-232", "CONFIGuration:BUSWAVEFORMS:RS232C?"),
    CAN("CAN", "CAN", "CONFIGuration:BUSWAVEFORMS:CAN?"),
    LIN("LIN", "LIN", "CONFIGuration:BUSWAVEFORMS:LIN?"),
    USB("USB", "USB", "CONFIGuration:BUSWAVEFORMS:USB?"),
    ETHERNET("ETHERnet", "Ethernet", "CONFIGuration:BUSWAVEFORMS:ETHERNET?"),
    FLEXRAY("FLEXRAY", "FlexRay", "CONFIGuration:BUSWAVEFORMS:FLEXRAY?"),
    AUDIO("AUDIO", "Audio", "CONFIGuration:BUSWAVEFORMS:AUDIO?"),
    MIL1553B("MIL1553B", "MIL-STD-1553", "CONFIGuration:BUSWAVEFORMS:MIL1553B?"),
    PARALLEL("PARallel", "パラレル", "CONFIGuration:BUSWAVEFORMS:PARallel?"),
}
