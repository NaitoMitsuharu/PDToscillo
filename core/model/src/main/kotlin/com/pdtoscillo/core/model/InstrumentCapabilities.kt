package com.pdtoscillo.core.model

/**
 * 接続した機器で「実際に使える機能」。
 *
 * 実機モデルが未確定のため、UI はこの値だけを見て機能の有効・無効を決める。
 * 未対応機能はクラッシュさせず、無効化して理由を表示する。
 *
 * 検出は非破壊で行う。機能を確かめるために本体設定を変更してはならない。
 */
data class InstrumentCapabilities(
    val manufacturer: String,
    val model: String,
    val serialNumber: String?,
    val firmwareVersion: String?,
    val family: ModelFamily,
    val analogChannelCount: Int,
    val digitalChannelCount: Int,
    val hasSpectrumAnalyzer: Boolean,
    val hasAfg: Boolean,
    val hasArbitraryWaveform: Boolean,
    val hasDvm: Boolean,
    val hasBusDecode: Boolean,
    val hasAdvancedMath: Boolean,
    val hasHistogram: Boolean,
    val supportsRawSocket: Boolean,
    val supportsVxi11: Boolean,
    val supportsEscope: Boolean,
    val supportsScreenshot: Boolean,
    val supportsConfigurationQueries: Boolean,
    val referenceWaveformCount: Int,
    val maxMeasurementCount: Int,
    val maxSampleRate: Double?,
    val analogBandwidth: Double?,
    val rfBandwidth: Double?,
    val supportedRecordLengths: List<Long>,
    val supportedTriggerTypes: Set<TriggerType>,
    val supportedBusTypes: Set<BusType>,
    val supportedMeasurements: Set<MeasurementType>,
    val supportedWaveformSources: Set<WaveformSource>,
    val detectionSource: CapabilityDetectionSource,
    val undeterminedFeatures: Set<String>,
) {
    val hasDigitalChannels: Boolean get() = digitalChannelCount > 0

    val analogChannels: List<WaveformSource>
        get() = WaveformSource.ANALOG_CHANNELS.take(analogChannelCount)

    val digitalChannels: List<WaveformSource>
        get() = WaveformSource.DIGITAL_BITS.take(digitalChannelCount)

    fun supports(source: WaveformSource): Boolean = source in supportedWaveformSources

    fun supports(trigger: TriggerType): Boolean = trigger in supportedTriggerTypes

    fun supports(measurement: MeasurementType): Boolean = measurement in supportedMeasurements

    /** 判定できなかった機能か。UI では「不明」として無効化する。 */
    fun isUndetermined(featureKey: String): Boolean = featureKey in undeterminedFeatures

    companion object {
        /**
         * 接続直後、まだ何も分かっていない状態。
         *
         * 過大評価を避けるため、この状態では最小構成（アナログ 2 ch）のみを有効とする。
         * 実際の値は Capability 検出後に置き換わる。
         */
        fun unknown(identity: InstrumentIdentity? = null): InstrumentCapabilities = InstrumentCapabilities(
            manufacturer = identity?.manufacturer ?: "",
            model = identity?.model ?: "",
            serialNumber = identity?.serialNumber,
            firmwareVersion = identity?.firmwareVersion,
            family = ModelFamily.UNKNOWN_4000,
            analogChannelCount = MINIMUM_ANALOG_CHANNELS,
            digitalChannelCount = 0,
            hasSpectrumAnalyzer = false,
            hasAfg = false,
            hasArbitraryWaveform = false,
            hasDvm = false,
            hasBusDecode = false,
            hasAdvancedMath = false,
            hasHistogram = false,
            supportsRawSocket = true,
            supportsVxi11 = false,
            supportsEscope = false,
            supportsScreenshot = false,
            supportsConfigurationQueries = false,
            referenceWaveformCount = 0,
            maxMeasurementCount = 0,
            analogBandwidth = null,
            rfBandwidth = null,
            maxSampleRate = null,
            supportedRecordLengths = emptyList(),
            supportedTriggerTypes = emptySet(),
            supportedBusTypes = emptySet(),
            supportedMeasurements = emptySet(),
            supportedWaveformSources = WaveformSource.ANALOG_CHANNELS.take(MINIMUM_ANALOG_CHANNELS).toSet(),
            detectionSource = CapabilityDetectionSource.NOT_DETECTED,
            undeterminedFeatures = emptySet(),
        )

        /** 4000 シリーズの最小構成。 */
        const val MINIMUM_ANALOG_CHANNELS: Int = 2
    }
}

/** Capability をどの経路で判定したか。診断画面に表示し、信頼度の目安にする。 */
enum class CapabilityDetectionSource {
    /** 未検出。 */
    NOT_DETECTED,

    /** `CONFIGuration:*?` クエリ群から取得（最も信頼できる）。 */
    CONFIGURATION_QUERIES,

    /** `CONFIGuration:*?` が使えず、モデル名から推定した。 */
    MODEL_NAME_FALLBACK,

    /** 一部を `CONFIGuration:*?`、残りをモデル名から補った。 */
    MIXED,
}
