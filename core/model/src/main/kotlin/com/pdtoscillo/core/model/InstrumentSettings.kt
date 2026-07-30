package com.pdtoscillo.core.model

/**
 * 計測器の設定値モデル。
 *
 * 列挙型の `scpiValue` は公式 Programmer Manual（MDO4000/B/C, MSO/DPO4000B, MDO3000）で
 * 実際の引数として確認した値のみを持つ。推測した値は追加しない。
 */

/** `ACQuire:MODe {SAMple|PEAKdetect|HIRes|AVErage|ENVelope}` */
enum class AcquisitionMode(val scpiValue: String, val displayName: String) {
    SAMPLE("SAMple", "Sample"),
    PEAK_DETECT("PEAKdetect", "Peak Detect"),
    HI_RES("HIRes", "Hi-Res"),
    AVERAGE("AVErage", "Average"),
    ENVELOPE("ENVelope", "Envelope"),
    ;

    /** 1 点が最大・最小の対になるモード。波形のデコードで扱いが変わる。 */
    val producesMinMaxPairs: Boolean get() = this == PEAK_DETECT || this == ENVELOPE

    val usesAverageCount: Boolean get() = this == AVERAGE || this == ENVELOPE

    companion object {
        fun fromScpi(value: String): AcquisitionMode? = entries.firstOrNull {
            TriggerType.matchesScpiKeyword(value.trim().trim('"').uppercase(), it.scpiValue)
        }
    }
}

/** `ACQuire:STOPAfter {RUNSTop|SEQuence}` */
enum class StopAfterMode(val scpiValue: String, val displayName: String) {
    RUN_STOP("RUNSTop", "連続"),
    SEQUENCE("SEQuence", "単発"),
    ;

    companion object {
        fun fromScpi(value: String): StopAfterMode? = entries.firstOrNull {
            TriggerType.matchesScpiKeyword(value.trim().trim('"').uppercase(), it.scpiValue)
        }
    }
}

data class AcquisitionSettings(
    val running: Boolean?,
    val mode: AcquisitionMode?,
    val stopAfter: StopAfterMode?,
    val averageCount: Int?,
    val acquisitionCount: Long?,
    val fastAcquisition: Boolean?,
) {
    companion object {
        val UNKNOWN = AcquisitionSettings(null, null, null, null, null, null)
    }
}

data class HorizontalSettings(
    /** 秒/div。 */
    val scaleSecondsPerDivision: Double?,
    /** % 表示の水平位置。 */
    val positionPercent: Double?,
    val recordLength: Long?,
    val sampleRate: Double?,
) {
    /** 画面全体の時間幅。4000 シリーズの水平目盛は 10 div。 */
    val totalTimeSpan: Double? get() = scaleSecondsPerDivision?.times(HORIZONTAL_DIVISIONS)

    companion object {
        val UNKNOWN = HorizontalSettings(null, null, null, null)

        /** 水平方向の目盛数。 */
        const val HORIZONTAL_DIVISIONS: Int = 10
    }
}

/** `CH<x>:COUPling {AC|DC|DCREJect}` */
enum class ChannelCoupling(val scpiValue: String, val displayName: String) {
    AC("AC", "AC"),
    DC("DC", "DC"),
    DC_REJECT("DCREJect", "DC Reject"),
    ;

    companion object {
        fun fromScpi(value: String): ChannelCoupling? {
            val normalized = value.trim().trim('"').uppercase()
            // "DC" は "DCREJECT" の前方一致でもあるため、長い方を先に判定する。
            return entries.sortedByDescending { it.scpiValue.length }.firstOrNull {
                TriggerType.matchesScpiKeyword(normalized, it.scpiValue)
            }
        }
    }
}

/**
 * `CH<x>:TERmination {FIFty|MEG|<NR3>}`
 *
 * 数値でも指定できるため、列挙型に収まらない値は [Custom] で保持する。
 */
sealed interface ChannelTermination {
    val ohms: Double?
    val scpiValue: String

    data object FiftyOhm : ChannelTermination {
        override val ohms: Double = 50.0
        override val scpiValue: String = "FIFty"
    }

    data object OneMegaOhm : ChannelTermination {
        override val ohms: Double = 1.0e6
        override val scpiValue: String = "MEG"
    }

    data class Custom(override val ohms: Double) : ChannelTermination {
        override val scpiValue: String get() = ohms.toString()
    }

    companion object {
        fun fromOhms(value: Double?): ChannelTermination? = when {
            value == null -> null
            value in FIFTY_RANGE -> FiftyOhm
            value >= MEG_THRESHOLD -> OneMegaOhm
            else -> Custom(value)
        }

        private val FIFTY_RANGE = 45.0..55.0
        private const val MEG_THRESHOLD = 500_000.0
    }
}

/**
 * `CH<x>:BANdwidth {FULl|<NR3>}`
 *
 * 数値制限にも対応するため、Full と数値を区別して持つ。
 */
sealed interface BandwidthLimit {
    val scpiValue: String

    data object Full : BandwidthLimit {
        override val scpiValue: String = "FULl"
    }

    data class Hertz(val value: Double) : BandwidthLimit {
        override val scpiValue: String get() = value.toString()
    }

    companion object {
        /** 応答は数値（帯域）か FULL 相当の大きな値で返る。 */
        fun fromResponse(raw: String, fullBandwidth: Double?): BandwidthLimit? {
            val text = raw.trim().trim('"')
            if (text.uppercase().startsWith("FUL")) return Full
            val value = text.toDoubleOrNull() ?: return null
            // 本体の最大帯域と同じ値なら制限なしとみなす。
            if (fullBandwidth != null && value >= fullBandwidth * FULL_TOLERANCE) return Full
            return Hertz(value)
        }

        private const val FULL_TOLERANCE = 0.99
    }
}

data class ChannelSettings(
    val channel: Int,
    val displayed: Boolean?,
    /** V/div。 */
    val verticalScale: Double?,
    /** div 単位の垂直位置。 */
    val verticalPosition: Double?,
    /** V 単位のオフセット。 */
    val offset: Double?,
    val coupling: ChannelCoupling?,
    val bandwidthLimit: BandwidthLimit?,
    val inverted: Boolean?,
    val label: String?,
    val termination: ChannelTermination?,
    /** 秒単位の Deskew。 */
    val deskew: Double?,
    /** プローブの減衰比（`CH<x>:PRObe:GAIN` の逆数として表示する）。 */
    val probeGain: Double?,
) {
    /** 画面全体の電圧幅。4000 シリーズの垂直目盛は 10 div。 */
    val totalVoltageSpan: Double? get() = verticalScale?.times(VERTICAL_DIVISIONS)

    /** プローブ減衰比。GAIN 0.1 は 10:1 プローブ。 */
    val probeAttenuation: Double? get() = probeGain?.takeIf { it != 0.0 }?.let { 1.0 / it }

    companion object {
        fun unknown(channel: Int) = ChannelSettings(
            channel = channel,
            displayed = null,
            verticalScale = null,
            verticalPosition = null,
            offset = null,
            coupling = null,
            bandwidthLimit = null,
            inverted = null,
            label = null,
            termination = null,
            deskew = null,
            probeGain = null,
        )

        /** 垂直方向の目盛数。 */
        const val VERTICAL_DIVISIONS: Int = 10
    }
}

/** `TRIGger:A:EDGE:SLOpe {RISe|FALL|EITHer}` */
enum class TriggerSlope(val scpiValue: String, val displayName: String) {
    RISE("RISe", "立ち上がり"),
    FALL("FALL", "立ち下がり"),
    EITHER("EITHer", "両方"),
    ;

    companion object {
        fun fromScpi(value: String): TriggerSlope? = entries.firstOrNull {
            TriggerType.matchesScpiKeyword(value.trim().trim('"').uppercase(), it.scpiValue)
        }
    }
}

/** `TRIGger:A:EDGE:COUPling {AC|DC|HFRej|LFRej|NOISErej}` */
enum class TriggerCoupling(val scpiValue: String, val displayName: String) {
    AC("AC", "AC"),
    DC("DC", "DC"),
    HF_REJECT("HFRej", "HF Reject"),
    LF_REJECT("LFRej", "LF Reject"),
    NOISE_REJECT("NOISErej", "Noise Reject"),
    ;

    companion object {
        fun fromScpi(value: String): TriggerCoupling? {
            val normalized = value.trim().trim('"').uppercase()
            return entries.sortedByDescending { it.scpiValue.length }.firstOrNull {
                TriggerType.matchesScpiKeyword(normalized, it.scpiValue)
            }
        }
    }
}

/** `TRIGger:A:MODe {AUTO|NORMal}` */
enum class TriggerSweepMode(val scpiValue: String, val displayName: String) {
    AUTO("AUTO", "Auto"),
    NORMAL("NORMal", "Normal"),
    ;

    companion object {
        fun fromScpi(value: String): TriggerSweepMode? = entries.firstOrNull {
            TriggerType.matchesScpiKeyword(value.trim().trim('"').uppercase(), it.scpiValue)
        }
    }
}

/**
 * `TRIGger:STATE?` の応答。
 *
 * マニュアルには「トリガシステムの現在の状態を返す」とあり、実機の応答文字列は世代で
 * 差がある可能性がある。既知の値に当てはまらない場合は [Unknown] として生応答を保持する。
 */
enum class TriggerRunState(val displayName: String) {
    ARMED("Armed"),
    AUTO("Auto"),
    READY("Ready"),
    SAVE("Save"),
    TRIGGERED("Trigger"),
    PARTIAL("Partial"),
    UNKNOWN("不明"),
    ;

    companion object {
        fun fromScpi(value: String): TriggerRunState {
            val normalized = value.trim().trim('"').uppercase()
            return when {
                normalized.startsWith("ARM") -> ARMED
                normalized.startsWith("AUTO") -> AUTO
                normalized.startsWith("READ") -> READY
                normalized.startsWith("SAV") -> SAVE
                normalized.startsWith("TRIG") -> TRIGGERED
                normalized.startsWith("PART") -> PARTIAL
                else -> UNKNOWN
            }
        }
    }
}

data class TriggerSettings(
    val type: TriggerType?,
    val sweepMode: TriggerSweepMode?,
    val runState: TriggerRunState?,
    val runStateRaw: String?,
    val edgeSource: WaveformSource?,
    val edgeSourceRaw: String?,
    val slope: TriggerSlope?,
    val coupling: TriggerCoupling?,
    val level: Double?,
    val holdoffTime: Double?,
) {
    companion object {
        val UNKNOWN = TriggerSettings(null, null, null, null, null, null, null, null, null, null)
    }
}

/**
 * 概要画面へ出す一括スナップショット。
 *
 * 個別の Query を何度も投げると本体の応答待ちが積み上がるため、1 回まとめて取得する。
 */
data class InstrumentSnapshot(
    val acquisition: AcquisitionSettings,
    val horizontal: HorizontalSettings,
    val trigger: TriggerSettings,
    val channels: List<ChannelSettings>,
    val capturedAtEpochMillis: Long,
    /** 取得にかかった時間。通信遅延の目安として表示する。 */
    val elapsedMillis: Long,
) {
    val displayedChannels: List<ChannelSettings> get() = channels.filter { it.displayed == true }

    companion object {
        fun empty() = InstrumentSnapshot(
            acquisition = AcquisitionSettings.UNKNOWN,
            horizontal = HorizontalSettings.UNKNOWN,
            trigger = TriggerSettings.UNKNOWN,
            channels = emptyList(),
            capturedAtEpochMillis = 0,
            elapsedMillis = 0,
        )
    }
}
