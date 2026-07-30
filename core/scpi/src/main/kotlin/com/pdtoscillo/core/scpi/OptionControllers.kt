package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.ScopeError
import java.util.Locale

/** AFG の状態。 */
data class AfgState(
    val function: String?,
    val frequency: Double?,
    val amplitude: Double?,
    val offset: Double?,
    val dutyPercent: Double?,
    val outputEnabled: Boolean?,
) {
    companion object {
        val UNKNOWN = AfgState(null, null, null, null, null, null)
    }
}

/** DVM の状態。 */
data class DvmState(val mode: String?, val source: String?, val value: Double?, val frequency: Double?) {
    companion object {
        val UNKNOWN = DvmState(null, null, null, null)
    }
}

/** RF（スペクトラム）の状態。 */
data class RfState(
    val centerFrequency: Double?,
    val span: Double?,
    val startFrequency: Double?,
    val stopFrequency: Double?,
    val resolutionBandwidth: Double?,
    val referenceLevel: Double?,
    val window: String?,
) {
    companion object {
        val UNKNOWN = RfState(null, null, null, null, null, null, null)
    }
}

/** デジタルチャンネル 1 本の状態。 */
data class DigitalChannelState(val bit: Int, val displayed: Boolean?, val threshold: Double?, val label: String?)

/**
 * オプション機能の操作。
 *
 * いずれも Capability で搭載を確認してから使う。搭載していない機種へ送ると
 * 未定義ヘッダーになり、タイムアウトを待つことになる。
 */
class OptionControllers(private val client: ScpiClient) {

    // ---- AFG ----

    suspend fun readAfg(): AfgState = AfgState(
        function = safeValue(TektronixCommands.Afg.FUNCTION_QUERY),
        frequency = safeDouble(TektronixCommands.Afg.FREQUENCY_QUERY),
        amplitude = safeDouble(TektronixCommands.Afg.AMPLITUDE_QUERY),
        offset = safeDouble(TektronixCommands.Afg.OFFSET_QUERY),
        dutyPercent = safeDouble(TektronixCommands.Afg.SQUARE_DUTY_QUERY),
        outputEnabled = safeBoolean(TektronixCommands.Afg.OUTPUT_STATE_QUERY),
    )

    suspend fun applyAfgFunction(function: String): ScpiClient.ApplyResult<String> = client.applyAndVerify(
        setCommand = TektronixCommands.Afg.FUNCTION,
        value = function,
        readBackQuery = TektronixCommands.Afg.FUNCTION_QUERY,
        parser = { ScpiResponseParser.unquote(ScpiResponseParser.stripHeader(it)) },
    )

    suspend fun applyAfgFrequency(hertz: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Afg.FREQUENCY,
        value = formatNumber(hertz),
        readBackQuery = TektronixCommands.Afg.FREQUENCY_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyAfgAmplitude(volts: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Afg.AMPLITUDE,
        value = formatNumber(volts),
        readBackQuery = TektronixCommands.Afg.AMPLITUDE_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyAfgOffset(volts: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Afg.OFFSET,
        value = formatNumber(volts),
        readBackQuery = TektronixCommands.Afg.OFFSET_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyAfgDuty(percent: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Afg.SQUARE_DUTY,
        value = formatNumber(percent),
        readBackQuery = TektronixCommands.Afg.SQUARE_DUTY_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    /**
     * AFG 出力の切り替え。
     *
     * **出力を有効にする操作は必ず確認を経てから呼ぶこと。**
     * 被測定回路へ信号が出るため、意図しない出力は機器を壊し得る。
     * 危険度の判定は [ScpiDangerClassifier] が行い、読み取り専用モードでは拒否される。
     */
    suspend fun applyAfgOutput(enabled: Boolean): ScpiClient.ApplyResult<Boolean> = client.applyAndVerify(
        setCommand = TektronixCommands.Afg.OUTPUT_STATE,
        value = if (enabled) "ON" else "OFF",
        readBackQuery = TektronixCommands.Afg.OUTPUT_STATE_QUERY,
        parser = { ScpiResponseParser.parseBoolean(it) },
    )

    // ---- DVM ----

    suspend fun readDvm(): DvmState = DvmState(
        mode = safeValue(TektronixCommands.Dvm.MODE_QUERY),
        source = safeValue(TektronixCommands.Dvm.SOURCE_QUERY),
        value = safeDouble(TektronixCommands.Dvm.VALUE_QUERY),
        frequency = safeDouble(TektronixCommands.Dvm.FREQUENCY_QUERY),
    )

    suspend fun applyDvmMode(mode: String): ScpiClient.ApplyResult<String> = client.applyAndVerify(
        setCommand = TektronixCommands.Dvm.MODE,
        value = mode,
        readBackQuery = TektronixCommands.Dvm.MODE_QUERY,
        parser = { ScpiResponseParser.unquote(ScpiResponseParser.stripHeader(it)) },
    )

    suspend fun applyDvmSource(source: String): ScpiClient.ApplyResult<String> = client.applyAndVerify(
        setCommand = TektronixCommands.Dvm.SOURCE,
        value = source,
        readBackQuery = TektronixCommands.Dvm.SOURCE_QUERY,
        parser = { ScpiResponseParser.unquote(ScpiResponseParser.stripHeader(it)) },
    )

    // ---- RF / Spectrum ----

    suspend fun readRf(): RfState = RfState(
        centerFrequency = safeDouble(TektronixCommands.Rf.CENTER_FREQUENCY_QUERY),
        span = safeDouble(TektronixCommands.Rf.SPAN_QUERY),
        startFrequency = safeDouble(TektronixCommands.Rf.START_FREQUENCY_QUERY),
        stopFrequency = safeDouble(TektronixCommands.Rf.STOP_FREQUENCY_QUERY),
        resolutionBandwidth = safeDouble(TektronixCommands.Rf.RESOLUTION_BANDWIDTH_QUERY),
        referenceLevel = safeDouble(TektronixCommands.Rf.REFERENCE_LEVEL_QUERY),
        window = safeValue(TektronixCommands.Rf.WINDOW_QUERY),
    )

    suspend fun applyRfCenterFrequency(hertz: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Rf.CENTER_FREQUENCY,
        value = formatNumber(hertz),
        readBackQuery = TektronixCommands.Rf.CENTER_FREQUENCY_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyRfSpan(hertz: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Rf.SPAN,
        value = formatNumber(hertz),
        readBackQuery = TektronixCommands.Rf.SPAN_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyRfResolutionBandwidth(hertz: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Rf.RESOLUTION_BANDWIDTH,
        value = formatNumber(hertz),
        readBackQuery = TektronixCommands.Rf.RESOLUTION_BANDWIDTH_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyRfReferenceLevel(dbm: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Rf.REFERENCE_LEVEL,
        value = formatNumber(dbm),
        readBackQuery = TektronixCommands.Rf.REFERENCE_LEVEL_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    // ---- Digital ----

    suspend fun readDigitalChannel(bit: Int): DigitalChannelState = DigitalChannelState(
        bit = bit,
        displayed = safeBoolean(TektronixCommands.Digital.displayQuery(bit)),
        threshold = safeDouble(TektronixCommands.Digital.thresholdQuery(bit)),
        label = safeValue(TektronixCommands.Digital.labelQuery(bit)),
    )

    suspend fun readDigitalChannels(count: Int): List<DigitalChannelState> = (0 until count).map { readDigitalChannel(it) }

    suspend fun applyDigitalDisplay(bit: Int, displayed: Boolean): ScpiClient.ApplyResult<Boolean> = client.applyAndVerify(
        setCommand = TektronixCommands.Digital.display(bit),
        value = if (displayed) "ON" else "OFF",
        readBackQuery = TektronixCommands.Digital.displayQuery(bit),
        parser = { ScpiResponseParser.parseBoolean(it) },
    )

    suspend fun applyDigitalThreshold(bit: Int, volts: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Digital.threshold(bit),
        value = formatNumber(volts),
        readBackQuery = TektronixCommands.Digital.thresholdQuery(bit),
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    // ---- Bus ----

    suspend fun readBusType(bus: Int): String? = safeValue(TektronixCommands.Bus.typeQuery(bus))

    suspend fun applyBusType(bus: Int, type: String): ScpiClient.ApplyResult<String> = client.applyAndVerify(
        setCommand = TektronixCommands.Bus.type(bus),
        value = type,
        readBackQuery = TektronixCommands.Bus.typeQuery(bus),
        parser = { ScpiResponseParser.unquote(ScpiResponseParser.stripHeader(it)) },
    )

    suspend fun applyBusDisplay(bus: Int, enabled: Boolean): ScpiClient.ApplyResult<Boolean> = client.applyAndVerify(
        setCommand = TektronixCommands.Bus.state(bus),
        value = if (enabled) "ON" else "OFF",
        readBackQuery = TektronixCommands.Bus.stateQuery(bus),
        parser = { ScpiResponseParser.parseBoolean(it) },
    )

    private suspend fun safeValue(command: String): String? = try {
        client.queryValue(command).takeIf { it.isNotBlank() }
    } catch (exception: ScpiException) {
        if (exception.error is ScopeError.Disconnected) throw exception
        null
    }

    private suspend fun safeDouble(command: String): Double? = safeValue(command)?.toDoubleOrNull()

    private suspend fun safeBoolean(command: String): Boolean? = safeValue(command)?.let { ScpiResponseParser.parseBoolean(it) }

    private fun formatNumber(value: Double): String = String.format(Locale.US, "%.6E", value)

    companion object {
        /**
         * AFG の波形種別。
         *
         * Programmer Manual の AFG:AMPLitude 設定表に載っている関数のみ。
         * `DC` は振幅設定を使わず、レベルは `AFG:OFFSet` で決まる（マニュアル注記）。
         */
        val AFG_FUNCTIONS = listOf(
            "SINE", "SQUare", "PULSe", "RAMP", "NOISe", "DC",
            "HAVERSINe", "CARDIac", "ARBitrary", "SINC", "LORENtz", "GAUSsian", "ERISe", "EDECAy",
        )

        /** DVM のモード。`DVM:MODe {ACRMS|ACDCRMS|DC|FREQuency|OFF}` で確認した値のみ。 */
        val DVM_MODES = listOf("ACRMS", "ACDCRMS", "DC", "FREQuency", "OFF")
    }
}
