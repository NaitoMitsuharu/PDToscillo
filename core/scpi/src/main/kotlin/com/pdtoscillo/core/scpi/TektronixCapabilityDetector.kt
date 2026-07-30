package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.BusType
import com.pdtoscillo.core.model.CapabilityDetectionSource
import com.pdtoscillo.core.model.InstrumentCapabilities
import com.pdtoscillo.core.model.InstrumentIdentity
import com.pdtoscillo.core.model.MeasurementType
import com.pdtoscillo.core.model.ModelFamily
import com.pdtoscillo.core.model.TriggerType
import com.pdtoscillo.core.model.WaveformSource

/**
 * 接続した機器で実際に使える機能を判定する。
 *
 * 判定の順序:
 * 1. `*IDN?` でメーカー・モデル・シリアル・ファームウェアを得る
 * 2. `CONFIGuration:*?` を試す（**非破壊**。マニュアルで確認済み）
 * 3. 未定義ヘッダーが返る機種（無印世代の可能性）はモデル名からの推定へフォールバック
 * 4. どちらでも判定できない項目は「不明」として記録し、UI 側で無効化する
 *
 * **機能検出のために本体設定を破壊的に変更しない。** ここで送るのは問い合わせのみである。
 */
class TektronixCapabilityDetector(private val queue: ScpiCommandQueue, private val errorQueue: ScpiErrorQueue) {
    /** 検出の途中経過。診断画面に出す。 */
    data class Progress(val step: String, val detail: String?)

    suspend fun detect(identity: InstrumentIdentity, onProgress: (Progress) -> Unit = {}): InstrumentCapabilities {
        val hints = ModelNameResolver.resolve(identity)
        onProgress(Progress("モデル判定", "${identity.model} → ${hints.family}"))

        if (hints.family == ModelFamily.UNSUPPORTED) {
            PdtLog.w(TAG, "4000 シリーズとして認識できないモデルです: ${identity.model}")
        }

        // まず Configuration グループが使えるかを 1 つの問い合わせで確かめる。
        val analogProbe = probeInt(TektronixCommands.Configuration.ANALOG_CHANNEL_COUNT)
        val configurationSupported = analogProbe is ProbeOutcome.Value
        onProgress(
            Progress(
                "CONFIGuration クエリ",
                if (configurationSupported) "対応" else "未対応（モデル名から推定します）",
            ),
        )

        return if (configurationSupported) {
            detectFromConfiguration(identity, hints, analogProbe.value, onProgress)
        } else {
            detectFromModelName(identity, hints, onProgress)
        }
    }

    @Suppress("LongMethod")
    private suspend fun detectFromConfiguration(
        identity: InstrumentIdentity,
        hints: com.pdtoscillo.core.model.ModelNameHints,
        analogChannelCount: Int,
        onProgress: (Progress) -> Unit,
    ): InstrumentCapabilities {
        val undetermined = mutableSetOf<String>()

        val digitalChannels = probeInt(TektronixCommands.Configuration.DIGITAL_CHANNEL_COUNT)
            .valueOr(0) { undetermined += FEATURE_DIGITAL }
        val rfChannels = probeInt(TektronixCommands.Configuration.RF_CHANNEL_COUNT)
            .valueOr(0) { undetermined += FEATURE_RF }
        val hasAfg = probeBoolean(TektronixCommands.Configuration.AFG)
            .valueOr(false) { undetermined += FEATURE_AFG }
        val hasArb = probeBoolean(TektronixCommands.Configuration.ARBITRARY)
            .valueOr(false) { undetermined += FEATURE_ARB }
        val hasDvm = probeBoolean(TektronixCommands.Configuration.DVM)
            .valueOr(false) { undetermined += FEATURE_DVM }
        val hasAdvancedMath = probeBoolean(TektronixCommands.Configuration.ADVANCED_MATH).valueOr(false) {}
        val hasHistogram = probeBoolean(TektronixCommands.Configuration.HISTOGRAM).valueOr(false) {}
        val referenceCount = probeInt(TektronixCommands.Configuration.REFERENCE_COUNT).valueOr(0) {}
        val measurementCount = probeInt(TektronixCommands.Configuration.MEASUREMENT_COUNT)
            .valueOr(DEFAULT_MEASUREMENT_SLOTS) {}
        val maxSampleRate = probeDouble(TektronixCommands.Configuration.ANALOG_MAX_SAMPLE_RATE).valueOrNull()
        val analogBandwidth = probeDouble(TektronixCommands.Configuration.ANALOG_MAX_BANDWIDTH).valueOrNull()
        val rfBandwidth = if (rfChannels > 0) {
            probeDouble(TektronixCommands.Configuration.RF_MAX_BANDWIDTH).valueOrNull()
        } else {
            null
        }
        val recordLengths = probeLongList(TektronixCommands.Configuration.ANALOG_RECORD_LENGTHS)

        onProgress(Progress("チャンネル構成", "アナログ $analogChannelCount / デジタル $digitalChannels"))

        val busTypes = mutableSetOf<BusType>()
        for (bus in BusType.entries) {
            when (val outcome = probeBoolean(bus.configurationQuery)) {
                is ProbeOutcome.Value -> if (outcome.value) busTypes += bus
                ProbeOutcome.Unsupported -> Unit
                is ProbeOutcome.Failed -> undetermined += "bus.${bus.name}"
            }
        }
        onProgress(Progress("バスデコード", busTypes.joinToString().ifEmpty { "なし" }))

        val hasRf = rfChannels > 0
        return build(
            identity = identity,
            family = hints.family,
            analogChannelCount = analogChannelCount,
            digitalChannelCount = digitalChannels,
            hasRf = hasRf,
            hasAfg = hasAfg,
            hasArb = hasArb,
            hasDvm = hasDvm,
            hasAdvancedMath = hasAdvancedMath,
            hasHistogram = hasHistogram,
            referenceCount = referenceCount,
            measurementCount = measurementCount,
            maxSampleRate = maxSampleRate,
            analogBandwidth = analogBandwidth,
            rfBandwidth = rfBandwidth,
            recordLengths = recordLengths,
            busTypes = busTypes,
            supportsConfigurationQueries = true,
            detectionSource = if (undetermined.isEmpty()) {
                CapabilityDetectionSource.CONFIGURATION_QUERIES
            } else {
                CapabilityDetectionSource.MIXED
            },
            undetermined = undetermined,
        )
    }

    /**
     * モデル名からの推定。
     *
     * 過大評価は誤操作につながるため、確信が持てない項目は有効化せず「不明」として記録する。
     */
    private fun detectFromModelName(
        identity: InstrumentIdentity,
        hints: com.pdtoscillo.core.model.ModelNameHints,
        onProgress: (Progress) -> Unit,
    ): InstrumentCapabilities {
        val undetermined = mutableSetOf<String>()

        val analogChannels = hints.analogChannelCount ?: run {
            undetermined += FEATURE_ANALOG
            InstrumentCapabilities.MINIMUM_ANALOG_CHANNELS
        }
        val digitalChannels = when (hints.hasDigitalChannels) {
            true -> ModelNameResolver.MSO_DIGITAL_CHANNELS
            false -> 0
            null -> {
                undetermined += FEATURE_DIGITAL
                0
            }
        }
        val hasRf = when (hints.hasRfChannel) {
            true -> true
            false -> false
            null -> {
                undetermined += FEATURE_RF
                false
            }
        }

        // AFG / DVM / ARB / バスはオプション搭載の有無をモデル名から判断できない。
        undetermined += FEATURE_AFG
        undetermined += FEATURE_ARB
        undetermined += FEATURE_DVM
        undetermined += FEATURE_BUS

        onProgress(
            Progress(
                "モデル名からの推定",
                "アナログ $analogChannels / デジタル $digitalChannels / RF ${if (hasRf) "あり" else "なし"}",
            ),
        )

        return build(
            identity = identity,
            family = hints.family,
            analogChannelCount = analogChannels,
            digitalChannelCount = digitalChannels,
            hasRf = hasRf,
            hasAfg = false,
            hasArb = false,
            hasDvm = false,
            hasAdvancedMath = false,
            hasHistogram = false,
            referenceCount = DEFAULT_REFERENCE_COUNT,
            measurementCount = DEFAULT_MEASUREMENT_SLOTS,
            maxSampleRate = null,
            analogBandwidth = null,
            rfBandwidth = null,
            recordLengths = emptyList(),
            busTypes = emptySet(),
            supportsConfigurationQueries = false,
            detectionSource = CapabilityDetectionSource.MODEL_NAME_FALLBACK,
            undetermined = undetermined,
        )
    }

    @Suppress("LongParameterList")
    private fun build(
        identity: InstrumentIdentity,
        family: ModelFamily,
        analogChannelCount: Int,
        digitalChannelCount: Int,
        hasRf: Boolean,
        hasAfg: Boolean,
        hasArb: Boolean,
        hasDvm: Boolean,
        hasAdvancedMath: Boolean,
        hasHistogram: Boolean,
        referenceCount: Int,
        measurementCount: Int,
        maxSampleRate: Double?,
        analogBandwidth: Double?,
        rfBandwidth: Double?,
        recordLengths: List<Long>,
        busTypes: Set<BusType>,
        supportsConfigurationQueries: Boolean,
        detectionSource: CapabilityDetectionSource,
        undetermined: Set<String>,
    ): InstrumentCapabilities {
        val sources = buildSet {
            addAll(WaveformSource.ANALOG_CHANNELS.take(analogChannelCount))
            addAll(WaveformSource.DIGITAL_BITS.take(digitalChannelCount))
            if (digitalChannelCount > 0) add(WaveformSource.DIGITAL_COLLECTION)
            add(WaveformSource.MATH)
            addAll(WaveformSource.REFERENCES.take(referenceCount))
            if (hasRf) {
                add(WaveformSource.RF_AMPLITUDE)
                add(WaveformSource.RF_FREQUENCY)
                add(WaveformSource.RF_PHASE)
                add(WaveformSource.RF_NORMAL)
                add(WaveformSource.RF_AVERAGE)
                add(WaveformSource.RF_MAXHOLD)
                add(WaveformSource.RF_MINHOLD)
            }
        }

        val triggers = buildSet {
            addAll(TriggerType.ALWAYS_AVAILABLE)
            if (busTypes.isNotEmpty()) add(TriggerType.BUS)
            // ビデオトリガは全機種にあるが、拡張ビデオはオプション。基本形のみ有効化する。
            add(TriggerType.VIDEO)
        }

        val measurements = MeasurementType.entries
            .filterNot { it.requiresOption }
            .toSet()

        return InstrumentCapabilities(
            manufacturer = identity.manufacturer,
            model = identity.model,
            serialNumber = identity.serialNumber,
            firmwareVersion = identity.firmwareVersion,
            family = family,
            analogChannelCount = analogChannelCount,
            digitalChannelCount = digitalChannelCount,
            hasSpectrumAnalyzer = hasRf,
            hasAfg = hasAfg,
            hasArbitraryWaveform = hasArb,
            hasDvm = hasDvm,
            hasBusDecode = busTypes.isNotEmpty(),
            hasAdvancedMath = hasAdvancedMath,
            hasHistogram = hasHistogram,
            supportsRawSocket = true,
            // VXI-11 は本アプリでは未実装。docs/vxi11-feasibility.md を参照。
            supportsVxi11 = false,
            // e*Scope は HTTP で開けるかどうかを別途確認する。ここでは世代から推定する。
            supportsEscope = family == ModelFamily.GEN2_4000B_MDO4000 || family == ModelFamily.GEN3_MDO4000BC,
            supportsScreenshot = true,
            supportsConfigurationQueries = supportsConfigurationQueries,
            referenceWaveformCount = referenceCount,
            maxMeasurementCount = measurementCount,
            maxSampleRate = maxSampleRate,
            analogBandwidth = analogBandwidth,
            rfBandwidth = rfBandwidth,
            supportedRecordLengths = recordLengths,
            supportedTriggerTypes = triggers,
            supportedBusTypes = busTypes,
            supportedMeasurements = measurements,
            supportedWaveformSources = sources,
            detectionSource = detectionSource,
            undeterminedFeatures = undetermined,
        )
    }

    /** 問い合わせ 1 件の結果。 */
    private sealed interface ProbeOutcome<out T> {
        data class Value<T>(val value: T) : ProbeOutcome<T>

        /** 未定義ヘッダー。この機種はこのクエリを持たない。 */
        data object Unsupported : ProbeOutcome<Nothing>

        data class Failed(val detail: String?) : ProbeOutcome<Nothing>
    }

    private fun <T> ProbeOutcome<T>.valueOr(fallback: T, onUndetermined: () -> Unit): T = when (this) {
        is ProbeOutcome.Value -> value
        ProbeOutcome.Unsupported -> fallback
        is ProbeOutcome.Failed -> {
            onUndetermined()
            fallback
        }
    }

    private fun <T> ProbeOutcome<T>.valueOrNull(): T? = (this as? ProbeOutcome.Value)?.value

    /**
     * 未対応の可能性がある問い合わせを 1 件実行する。
     *
     * 未定義ヘッダーには応答が返らないため、タイムアウト後にエラーキューを見て確定させる。
     * 「応答が無い」だけでは未対応と断定しない（回線不調と区別できない）。
     */
    private suspend fun probeRaw(command: String): ProbeOutcome<String> = when (val result = queue.probe(ScpiCommand.ProbeQuery(command))) {
        is ScpiCommandQueue.ProbeResult.Responded -> ProbeOutcome.Value(result.response)

        ScpiCommandQueue.ProbeResult.NoResponse -> {
            val error = errorQueue.classifyLatest(command)
            if (error != null && error.indicatesUnsupported) {
                PdtLog.i(TAG, "未対応と判定: $command (${error.detail})")
                ProbeOutcome.Unsupported
            } else {
                PdtLog.w(TAG, "応答なし。未対応か通信不調かを判別できません: $command")
                ProbeOutcome.Failed(error?.detail ?: "応答なし")
            }
        }

        is ScpiCommandQueue.ProbeResult.Failed ->
            if (result.error.indicatesUnsupported) {
                ProbeOutcome.Unsupported
            } else {
                ProbeOutcome.Failed(result.error.detail)
            }
    }

    private suspend fun probeInt(command: String): ProbeOutcome<Int> = mapProbe(command) {
        ScpiResponseParser.parseInt(it)
    }

    private suspend fun probeDouble(command: String): ProbeOutcome<Double> = mapProbe(command) {
        ScpiResponseParser.parseDouble(it)
    }

    private suspend fun probeBoolean(command: String): ProbeOutcome<Boolean> = mapProbe(command) {
        ScpiResponseParser.parseBoolean(it)
    }

    private suspend fun <T> mapProbe(command: String, transform: (String) -> T?): ProbeOutcome<T> = when (val raw = probeRaw(command)) {
        is ProbeOutcome.Value -> transform(raw.value)
            ?.let { ProbeOutcome.Value(it) }
            ?: ProbeOutcome.Failed("応答を解釈できません: ${raw.value.take(RESPONSE_PREVIEW)}")

        ProbeOutcome.Unsupported -> ProbeOutcome.Unsupported
        is ProbeOutcome.Failed -> raw
    }

    private suspend fun probeLongList(command: String): List<Long> = when (val raw = probeRaw(command)) {
        is ProbeOutcome.Value -> ScpiResponseParser.parseLongList(raw.value)
        else -> emptyList()
    }

    companion object {
        private const val TAG = "CapabilityDetector"
        private const val RESPONSE_PREVIEW = 40
        private const val DEFAULT_MEASUREMENT_SLOTS = 4
        private const val DEFAULT_REFERENCE_COUNT = 4

        const val FEATURE_ANALOG = "analogChannelCount"
        const val FEATURE_DIGITAL = "digitalChannels"
        const val FEATURE_RF = "rf"
        const val FEATURE_AFG = "afg"
        const val FEATURE_ARB = "arbitraryWaveform"
        const val FEATURE_DVM = "dvm"
        const val FEATURE_BUS = "busDecode"
    }
}
