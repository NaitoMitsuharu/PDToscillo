package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.AcquisitionMode
import com.pdtoscillo.core.model.AcquisitionSettings
import com.pdtoscillo.core.model.BandwidthLimit
import com.pdtoscillo.core.model.ChannelCoupling
import com.pdtoscillo.core.model.ChannelSettings
import com.pdtoscillo.core.model.ChannelTermination
import com.pdtoscillo.core.model.HorizontalSettings
import com.pdtoscillo.core.model.InstrumentCapabilities
import com.pdtoscillo.core.model.InstrumentSnapshot
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.StopAfterMode
import com.pdtoscillo.core.model.TriggerCoupling
import com.pdtoscillo.core.model.TriggerRunState
import com.pdtoscillo.core.model.TriggerSettings
import com.pdtoscillo.core.model.TriggerSlope
import com.pdtoscillo.core.model.TriggerSweepMode
import com.pdtoscillo.core.model.TriggerType
import com.pdtoscillo.core.model.WaveformSource

/**
 * Tektronix 4000 シリーズの操作をまとめた層。
 *
 * feature 層はこのクラスを通して計測器を操作する。SCPI 文字列の組み立ては
 * [TektronixCommands] に集約されており、ここでも画面側でも直書きしない。
 *
 * 設定変更は必ず「変更前を読む → 設定 → 読み戻す」の順で行う。計測器は要求値をそのまま
 * 受け付けるとは限らない（離散値へ丸める、範囲へ収める）ため、**送った値ではなく本体が
 * 受理した値**を返す。
 *
 * 個別の値が取れない場合は例外にせず null を返す。未対応機種で画面全体が使えなくなるのを避ける。
 */
class Tektronix4000Driver(private val client: ScpiClient) {

    // ---- Acquisition ----

    suspend fun readAcquisition(): AcquisitionSettings = AcquisitionSettings(
        running = safeBoolean(TektronixCommands.Acquisition.STATE_QUERY),
        mode = safeValue(TektronixCommands.Acquisition.MODE_QUERY)?.let(AcquisitionMode::fromScpi),
        stopAfter = safeValue(TektronixCommands.Acquisition.STOP_AFTER_QUERY)?.let(StopAfterMode::fromScpi),
        averageCount = safeInt(TektronixCommands.Acquisition.NUM_AVERAGE_QUERY),
        acquisitionCount = safeLong(TektronixCommands.Acquisition.NUM_ACQUISITIONS_QUERY),
        fastAcquisition = safeBoolean(TektronixCommands.Acquisition.FAST_ACQUISITION_STATE_QUERY),
    )

    /** 連続取得を開始する。 */
    suspend fun run() {
        client.writeAll(TektronixCommands.Acquisition.continuous())
    }

    suspend fun stop() {
        client.write(TektronixCommands.Acquisition.stop())
    }

    /**
     * 単発取得。
     *
     * `STOPAfter SEQuence` にしてから `STATE RUN` を送る。順序を逆にすると連続取得になる。
     */
    suspend fun single() {
        client.writeAll(TektronixCommands.Acquisition.singleSequence())
    }

    suspend fun forceTrigger() {
        client.write(TektronixCommands.Trigger.FORCE)
    }

    suspend fun applyAcquisitionMode(mode: AcquisitionMode): ScpiClient.ApplyResult<AcquisitionMode> = client.applyAndVerify(
        setCommand = TektronixCommands.Acquisition.MODE,
        value = mode.scpiValue,
        readBackQuery = TektronixCommands.Acquisition.MODE_QUERY,
        parser = { AcquisitionMode.fromScpi(ScpiResponseParser.stripHeader(it)) },
    )

    suspend fun applyAverageCount(count: Int): ScpiClient.ApplyResult<Int> = client.applyAndVerify(
        setCommand = TektronixCommands.Acquisition.NUM_AVERAGE,
        value = count.toString(),
        readBackQuery = TektronixCommands.Acquisition.NUM_AVERAGE_QUERY,
        parser = { ScpiResponseParser.parseInt(it) },
    )

    suspend fun applyFastAcquisition(enabled: Boolean): ScpiClient.ApplyResult<Boolean> = client.applyAndVerify(
        setCommand = TektronixCommands.Acquisition.FAST_ACQUISITION_STATE,
        value = if (enabled) "ON" else "OFF",
        readBackQuery = TektronixCommands.Acquisition.FAST_ACQUISITION_STATE_QUERY,
        parser = { ScpiResponseParser.parseBoolean(it) },
    )

    // ---- Horizontal ----

    suspend fun readHorizontal(): HorizontalSettings = HorizontalSettings(
        scaleSecondsPerDivision = safeDouble(TektronixCommands.Horizontal.SCALE_QUERY),
        positionPercent = safeDouble(TektronixCommands.Horizontal.POSITION_QUERY),
        recordLength = safeLong(TektronixCommands.Horizontal.RECORD_LENGTH_QUERY),
        sampleRate = safeDouble(TektronixCommands.Horizontal.SAMPLE_RATE_QUERY),
    )

    suspend fun applyHorizontalScale(secondsPerDivision: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Horizontal.SCALE,
        value = formatNumber(secondsPerDivision),
        readBackQuery = TektronixCommands.Horizontal.SCALE_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyHorizontalPosition(percent: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Horizontal.POSITION,
        value = formatNumber(percent),
        readBackQuery = TektronixCommands.Horizontal.POSITION_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyRecordLength(length: Long): ScpiClient.ApplyResult<Long> = client.applyAndVerify(
        setCommand = TektronixCommands.Horizontal.RECORD_LENGTH,
        value = length.toString(),
        readBackQuery = TektronixCommands.Horizontal.RECORD_LENGTH_QUERY,
        parser = { ScpiResponseParser.parseLong(it) },
    )

    // ---- Vertical (channel) ----

    suspend fun readChannel(channel: Int, fullBandwidth: Double? = null): ChannelSettings = ChannelSettings(
        channel = channel,
        displayed = safeBoolean(TektronixCommands.Vertical.displayQuery(channel)),
        verticalScale = safeDouble(TektronixCommands.Vertical.scaleQuery(channel)),
        verticalPosition = safeDouble(TektronixCommands.Vertical.positionQuery(channel)),
        offset = safeDouble(TektronixCommands.Vertical.offsetQuery(channel)),
        coupling = safeValue(TektronixCommands.Vertical.couplingQuery(channel))?.let(ChannelCoupling::fromScpi),
        bandwidthLimit = safeValue(TektronixCommands.Vertical.bandwidthQuery(channel))
            ?.let { BandwidthLimit.fromResponse(it, fullBandwidth) },
        inverted = safeBoolean(TektronixCommands.Vertical.invertQuery(channel)),
        label = safeValue(TektronixCommands.Vertical.labelQuery(channel)),
        termination = ChannelTermination.fromOhms(safeDouble(TektronixCommands.Vertical.terminationQuery(channel))),
        deskew = safeDouble(TektronixCommands.Vertical.deskewQuery(channel)),
        probeGain = safeDouble(TektronixCommands.Vertical.probeGainQuery(channel)),
    )

    suspend fun applyChannelDisplay(channel: Int, displayed: Boolean): ScpiClient.ApplyResult<Boolean> = client.applyAndVerify(
        setCommand = TektronixCommands.Vertical.display(channel),
        value = if (displayed) "ON" else "OFF",
        readBackQuery = TektronixCommands.Vertical.displayQuery(channel),
        parser = { ScpiResponseParser.parseBoolean(it) },
    )

    suspend fun applyVerticalScale(channel: Int, voltsPerDivision: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Vertical.scale(channel),
        value = formatNumber(voltsPerDivision),
        readBackQuery = TektronixCommands.Vertical.scaleQuery(channel),
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyVerticalPosition(channel: Int, divisions: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Vertical.position(channel),
        value = formatNumber(divisions),
        readBackQuery = TektronixCommands.Vertical.positionQuery(channel),
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyOffset(channel: Int, volts: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Vertical.offset(channel),
        value = formatNumber(volts),
        readBackQuery = TektronixCommands.Vertical.offsetQuery(channel),
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyCoupling(channel: Int, coupling: ChannelCoupling): ScpiClient.ApplyResult<ChannelCoupling> = client.applyAndVerify(
        setCommand = TektronixCommands.Vertical.coupling(channel),
        value = coupling.scpiValue,
        readBackQuery = TektronixCommands.Vertical.couplingQuery(channel),
        parser = { ChannelCoupling.fromScpi(ScpiResponseParser.stripHeader(it)) },
    )

    suspend fun applyBandwidthLimit(channel: Int, limit: BandwidthLimit, fullBandwidth: Double?): ScpiClient.ApplyResult<BandwidthLimit> =
        client.applyAndVerify(
            setCommand = TektronixCommands.Vertical.bandwidth(channel),
            value = limit.scpiValue,
            readBackQuery = TektronixCommands.Vertical.bandwidthQuery(channel),
            parser = { BandwidthLimit.fromResponse(ScpiResponseParser.stripHeader(it), fullBandwidth) },
        )

    suspend fun applyInvert(channel: Int, inverted: Boolean): ScpiClient.ApplyResult<Boolean> = client.applyAndVerify(
        setCommand = TektronixCommands.Vertical.invert(channel),
        value = if (inverted) "ON" else "OFF",
        readBackQuery = TektronixCommands.Vertical.invertQuery(channel),
        parser = { ScpiResponseParser.parseBoolean(it) },
    )

    /**
     * ラベルを設定する。
     *
     * 文字列は引用符で囲む。利用者の入力に引用符が含まれていた場合は取り除く
     * （そのまま送ると引用が閉じてコマンドが壊れる）。
     */
    suspend fun applyLabel(channel: Int, label: String): ScpiClient.ApplyResult<String> {
        val sanitized = label.replace("\"", "").take(MAX_LABEL_LENGTH)
        return client.applyAndVerify(
            setCommand = TektronixCommands.Vertical.label(channel),
            value = "\"$sanitized\"",
            readBackQuery = TektronixCommands.Vertical.labelQuery(channel),
            parser = { ScpiResponseParser.unquote(ScpiResponseParser.stripHeader(it)) },
        )
    }

    suspend fun applyTermination(channel: Int, termination: ChannelTermination): ScpiClient.ApplyResult<ChannelTermination> =
        client.applyAndVerify(
            setCommand = TektronixCommands.Vertical.termination(channel),
            value = termination.scpiValue,
            readBackQuery = TektronixCommands.Vertical.terminationQuery(channel),
            parser = { ChannelTermination.fromOhms(ScpiResponseParser.parseDouble(it)) },
        )

    suspend fun applyDeskew(channel: Int, seconds: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Vertical.deskew(channel),
        value = formatNumber(seconds),
        readBackQuery = TektronixCommands.Vertical.deskewQuery(channel),
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    /**
     * プローブ減衰比を設定する。
     *
     * SCPI 側は GAIN（利得）なので、10:1 プローブは 0.1 になる。
     * 画面では減衰比で扱い、ここで変換する。
     */
    suspend fun applyProbeAttenuation(channel: Int, attenuation: Double): ScpiClient.ApplyResult<Double> {
        require(attenuation > 0) { "減衰比は正の値である必要があります" }
        return client.applyAndVerify(
            setCommand = TektronixCommands.Vertical.probeGain(channel),
            value = formatNumber(1.0 / attenuation),
            readBackQuery = TektronixCommands.Vertical.probeGainQuery(channel),
            parser = { ScpiResponseParser.parseDouble(it) },
        )
    }

    // ---- Trigger ----

    suspend fun readTrigger(): TriggerSettings {
        val typeResponse = safeValue(TektronixCommands.Trigger.TYPE_QUERY)
        val pulseClass = safeValue(TektronixCommands.Trigger.PULSE_CLASS_QUERY)
        val logicClass = safeValue(TektronixCommands.Trigger.LOGIC_CLASS_QUERY)
        val runStateRaw = safeValue(TektronixCommands.Trigger.STATE_QUERY)
        val edgeSourceRaw = safeValue(TektronixCommands.Trigger.EDGE_SOURCE_QUERY)

        // 型に応じて参照するクラスが変わる。両方問い合わせてから解決する。
        val type = typeResponse?.let { response ->
            TriggerType.resolve(response, pulseClass)
                ?: TriggerType.resolve(response, logicClass)
                ?: TriggerType.resolve(response, null)
        }

        val level = edgeSourceRaw
            ?.let { WaveformSource.fromScpi(it) }
            ?.takeIf { it.isAnalogChannel }
            ?.let { source ->
                val channel = WaveformSource.ANALOG_CHANNELS.indexOf(source) + 1
                safeDouble(TektronixCommands.Trigger.levelForChannelQuery(channel))
            }

        return TriggerSettings(
            type = type,
            sweepMode = safeValue(TektronixCommands.Trigger.MODE_QUERY)?.let(TriggerSweepMode::fromScpi),
            runState = runStateRaw?.let(TriggerRunState::fromScpi),
            runStateRaw = runStateRaw,
            edgeSource = edgeSourceRaw?.let { WaveformSource.fromScpi(it) },
            edgeSourceRaw = edgeSourceRaw,
            slope = safeValue(TektronixCommands.Trigger.EDGE_SLOPE_QUERY)?.let(TriggerSlope::fromScpi),
            coupling = safeValue(TektronixCommands.Trigger.EDGE_COUPLING_QUERY)?.let(TriggerCoupling::fromScpi),
            level = level,
            holdoffTime = safeDouble(TektronixCommands.Trigger.HOLDOFF_TIME_QUERY),
        )
    }

    suspend fun applyTriggerType(type: TriggerType): ScpiClient.ApplyResult<TriggerType> {
        // 型とクラスは 2 段構成。まとめて送ってから読み戻す。
        return try {
            client.writeAll(type.applyCommands())
            val error = client.errorQueue.classifyLatest(TektronixCommands.Trigger.TYPE)
            if (error != null) {
                ScpiClient.ApplyResult.Rejected(null, error)
            } else {
                ScpiClient.ApplyResult.Applied(null, readTrigger().type)
            }
        } catch (exception: ScpiException) {
            ScpiClient.ApplyResult.Rejected(null, exception.error)
        }
    }

    suspend fun applyTriggerSource(source: WaveformSource): ScpiClient.ApplyResult<WaveformSource> = client.applyAndVerify(
        setCommand = TektronixCommands.Trigger.EDGE_SOURCE,
        value = source.scpiValue,
        readBackQuery = TektronixCommands.Trigger.EDGE_SOURCE_QUERY,
        parser = { WaveformSource.fromScpi(ScpiResponseParser.stripHeader(it)) },
    )

    suspend fun applyTriggerSlope(slope: TriggerSlope): ScpiClient.ApplyResult<TriggerSlope> = client.applyAndVerify(
        setCommand = TektronixCommands.Trigger.EDGE_SLOPE,
        value = slope.scpiValue,
        readBackQuery = TektronixCommands.Trigger.EDGE_SLOPE_QUERY,
        parser = { TriggerSlope.fromScpi(ScpiResponseParser.stripHeader(it)) },
    )

    suspend fun applyTriggerCoupling(coupling: TriggerCoupling): ScpiClient.ApplyResult<TriggerCoupling> = client.applyAndVerify(
        setCommand = TektronixCommands.Trigger.EDGE_COUPLING,
        value = coupling.scpiValue,
        readBackQuery = TektronixCommands.Trigger.EDGE_COUPLING_QUERY,
        parser = { TriggerCoupling.fromScpi(ScpiResponseParser.stripHeader(it)) },
    )

    suspend fun applyTriggerSweepMode(mode: TriggerSweepMode): ScpiClient.ApplyResult<TriggerSweepMode> = client.applyAndVerify(
        setCommand = TektronixCommands.Trigger.MODE,
        value = mode.scpiValue,
        readBackQuery = TektronixCommands.Trigger.MODE_QUERY,
        parser = { TriggerSweepMode.fromScpi(ScpiResponseParser.stripHeader(it)) },
    )

    /** トリガレベルはソースごとに指定する。 */
    suspend fun applyTriggerLevel(channel: Int, volts: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Trigger.levelForChannel(channel),
        value = formatNumber(volts),
        readBackQuery = TektronixCommands.Trigger.levelForChannelQuery(channel),
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    suspend fun applyTriggerHoldoff(seconds: Double): ScpiClient.ApplyResult<Double> = client.applyAndVerify(
        setCommand = TektronixCommands.Trigger.HOLDOFF_TIME,
        value = formatNumber(seconds),
        readBackQuery = TektronixCommands.Trigger.HOLDOFF_TIME_QUERY,
        parser = { ScpiResponseParser.parseDouble(it) },
    )

    /** トリガレベルを振幅の 50% に合わせる。設定変更なので確認を経て呼ぶ。 */
    suspend fun setTriggerLevelToFiftyPercent() {
        client.write(TektronixCommands.Trigger.SET_LEVEL_50_PERCENT)
    }

    // ---- スナップショット ----

    /**
     * 概要画面用に一括取得する。
     *
     * Capability に応じて存在するチャンネルだけを読む。存在しないチャンネルを問い合わせると
     * 未定義ヘッダーでタイムアウトを待つことになり、無駄に遅くなる。
     */
    suspend fun readSnapshot(capabilities: InstrumentCapabilities?): InstrumentSnapshot {
        val started = System.currentTimeMillis()
        val channelCount = capabilities?.analogChannelCount ?: InstrumentCapabilities.MINIMUM_ANALOG_CHANNELS
        val fullBandwidth = capabilities?.analogBandwidth

        val acquisition = readAcquisition()
        val horizontal = readHorizontal()
        val trigger = readTrigger()
        val channels = (1..channelCount).map { readChannel(it, fullBandwidth) }

        return InstrumentSnapshot(
            acquisition = acquisition,
            horizontal = horizontal,
            trigger = trigger,
            channels = channels,
            capturedAtEpochMillis = System.currentTimeMillis(),
            elapsedMillis = System.currentTimeMillis() - started,
        )
    }

    // ---- 補助 ----

    /**
     * 値が取れない場合に null を返す問い合わせ。
     *
     * 未対応コマンドや一時的な失敗で画面全体が使えなくなるのを避ける。
     * 接続そのものが切れている場合は上位で扱うため、そのまま投げる。
     */
    private suspend fun safeValue(command: String): String? = try {
        client.queryValue(command).takeIf { it.isNotBlank() }
    } catch (exception: ScpiException) {
        if (exception.error is ScopeError.Disconnected) throw exception
        null
    }

    private suspend fun safeDouble(command: String): Double? = safeValue(command)?.toDoubleOrNull()

    private suspend fun safeLong(command: String): Long? = safeValue(command)?.let { ScpiResponseParser.parseLong(it) }

    private suspend fun safeInt(command: String): Int? = safeValue(command)?.let { ScpiResponseParser.parseInt(it) }

    private suspend fun safeBoolean(command: String): Boolean? = safeValue(command)?.let { ScpiResponseParser.parseBoolean(it) }

    /**
     * 数値を SCPI へ渡す形式へ整える。
     *
     * ロケール依存の小数点（カンマ）が混ざるとコマンドが壊れるため、必ず US 表記にする。
     */
    private fun formatNumber(value: Double): String = String.format(java.util.Locale.US, "%.6E", value)

    private companion object {
        const val MAX_LABEL_LENGTH = 30
    }
}
