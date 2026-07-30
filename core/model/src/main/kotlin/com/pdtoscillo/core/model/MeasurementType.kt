package com.pdtoscillo.core.model

/**
 * 測定種別。
 *
 * `scpiValue` は Programmer Manual（MDO4000/B/C, MSO/DPO4000B, MDO3000）の
 * `MEASUrement:IMMed:TYPe` / `MEASUrement:MEAS<x>:TYPe` の有効値として確認したものだけを持つ。
 *
 * 注意すべき点:
 * - 「アンダーシュート」に相当するのは `NOVershoot`（負のオーバーシュート）。
 *   独立した undershoot コマンドは存在しない。
 * - `TOVershoot`（合計オーバーシュート）はマニュアル記載のとおり一部モデルかつ
 *   SA3 / SA6 オプション搭載時のみ対応するため、Capability で制限する。
 * - デジタルチャンネルでは対応しない測定があり、その場合 9.91e37（NaN 相当）が返る。
 */
enum class MeasurementType(
    val scpiValue: String,
    val displayName: String,
    val quantity: MeasurementQuantity,
    val requiresOption: Boolean = false,
    val analogOnly: Boolean = false,
) {
    FREQUENCY("FREQuency", "周波数", MeasurementQuantity.FREQUENCY),
    PERIOD("PERIod", "周期", MeasurementQuantity.TIME),
    MEAN("MEAN", "平均", MeasurementQuantity.VOLTAGE),
    CYCLE_MEAN("CMEan", "サイクル平均", MeasurementQuantity.VOLTAGE),
    RMS("RMS", "実効値", MeasurementQuantity.VOLTAGE),
    CYCLE_RMS("CRMs", "サイクル実効値", MeasurementQuantity.VOLTAGE),
    PEAK_TO_PEAK("PK2Pk", "P-P 値", MeasurementQuantity.VOLTAGE),
    MAXIMUM("MAXimum", "最大値", MeasurementQuantity.VOLTAGE),
    MINIMUM("MINImum", "最小値", MeasurementQuantity.VOLTAGE),
    AMPLITUDE("AMPlitude", "振幅", MeasurementQuantity.VOLTAGE, analogOnly = true),
    HIGH("HIGH", "High 値", MeasurementQuantity.VOLTAGE),
    LOW("LOW", "Low 値", MeasurementQuantity.VOLTAGE),
    MEDIAN("MEDian", "中央値", MeasurementQuantity.VOLTAGE),
    RISE_TIME("RISe", "立ち上がり時間", MeasurementQuantity.TIME),
    FALL_TIME("FALL", "立ち下がり時間", MeasurementQuantity.TIME),
    POSITIVE_WIDTH("PWIdth", "正パルス幅", MeasurementQuantity.TIME),
    NEGATIVE_WIDTH("NWIdth", "負パルス幅", MeasurementQuantity.TIME),
    POSITIVE_DUTY("PDUty", "正デューティ比", MeasurementQuantity.PERCENT),
    NEGATIVE_DUTY("NDUty", "負デューティ比", MeasurementQuantity.PERCENT),
    POSITIVE_OVERSHOOT("POVershoot", "オーバーシュート", MeasurementQuantity.PERCENT, analogOnly = true),
    NEGATIVE_OVERSHOOT("NOVershoot", "アンダーシュート", MeasurementQuantity.PERCENT, analogOnly = true),
    TOTAL_OVERSHOOT("TOVershoot", "合計オーバーシュート", MeasurementQuantity.PERCENT, requiresOption = true),
    DELAY("DELay", "遅延", MeasurementQuantity.TIME),
    PHASE("PHAse", "位相", MeasurementQuantity.DEGREE),
    BURST_WIDTH("BURst", "バースト幅", MeasurementQuantity.TIME),
    AREA("AREa", "面積", MeasurementQuantity.VOLT_SECOND, analogOnly = true),
    CYCLE_AREA("CARea", "サイクル面積", MeasurementQuantity.VOLT_SECOND, analogOnly = true),
    POSITIVE_PULSE_COUNT("PPULSECount", "正パルス数", MeasurementQuantity.COUNT),
    NEGATIVE_PULSE_COUNT("NPULSECount", "負パルス数", MeasurementQuantity.COUNT),
    POSITIVE_EDGE_COUNT("PEDGECount", "立ち上がりエッジ数", MeasurementQuantity.COUNT),
    NEGATIVE_EDGE_COUNT("NEDGECount", "立ち下がりエッジ数", MeasurementQuantity.COUNT),
    STANDARD_DEVIATION("STDdev", "標準偏差", MeasurementQuantity.VOLTAGE),
    HITS("HITS", "ヒット数", MeasurementQuantity.COUNT, requiresOption = true),
    PEAK_HITS("PEAKHits", "ピークヒット数", MeasurementQuantity.COUNT, requiresOption = true),
    WAVEFORMS("WAVEFORMS", "波形数", MeasurementQuantity.COUNT),
    SIGMA1("SIGMA1", "σ1", MeasurementQuantity.PERCENT, requiresOption = true),
    SIGMA2("SIGMA2", "σ2", MeasurementQuantity.PERCENT, requiresOption = true),
    SIGMA3("SIGMA3", "σ3", MeasurementQuantity.PERCENT, requiresOption = true),
    ;

    /** 2 つのソースを必要とする測定か。 */
    val requiresSecondSource: Boolean
        get() = this == DELAY || this == PHASE

    companion object {
        /**
         * オプション不要で、アナログチャンネルに対して常用できる基本測定。
         * 接続直後の測定候補として提示する。
         */
        val BASIC: List<MeasurementType> = listOf(
            FREQUENCY, PERIOD, MEAN, RMS, PEAK_TO_PEAK, MAXIMUM, MINIMUM, AMPLITUDE,
            RISE_TIME, FALL_TIME, POSITIVE_WIDTH, NEGATIVE_WIDTH, POSITIVE_DUTY,
            POSITIVE_OVERSHOOT, NEGATIVE_OVERSHOOT, DELAY, PHASE,
        )

        fun fromScpi(value: String): MeasurementType? {
            val normalized = value.trim().trim('"').uppercase()
            return entries.firstOrNull { TriggerType.matchesScpiKeyword(normalized, it.scpiValue) }
        }
    }
}

/** 測定値の物理量。数値と単位を分離して扱うために使う。 */
enum class MeasurementQuantity(val defaultUnit: String) {
    VOLTAGE("V"),
    TIME("s"),
    FREQUENCY("Hz"),
    PERCENT("%"),
    DEGREE("°"),
    VOLT_SECOND("Vs"),
    COUNT(""),
}

/** 測定の統計値。`MEASUrement:STATIstics` 系の問い合わせ結果を保持する。 */
data class MeasurementStatistics(
    val current: Double?,
    val mean: Double?,
    val minimum: Double?,
    val maximum: Double?,
    val standardDeviation: Double?,
    val sampleCount: Long?,
) {
    companion object {
        val EMPTY = MeasurementStatistics(null, null, null, null, null, null)

        /**
         * 機種が「測定できない」ことを示す値。
         * マニュアル記載の 9.91e37 は NaN 相当として扱う。
         */
        const val NOT_A_NUMBER_SENTINEL: Double = 9.91e37

        fun isNotANumber(value: Double): Boolean = value.isNaN() || value >= NOT_A_NUMBER_SENTINEL * NAN_TOLERANCE_RATIO

        private const val NAN_TOLERANCE_RATIO = 0.999
    }
}
