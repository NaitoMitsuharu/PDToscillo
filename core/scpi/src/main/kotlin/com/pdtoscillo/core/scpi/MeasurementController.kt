package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.MeasurementStatistics
import com.pdtoscillo.core.model.MeasurementType
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.WaveformSource

/** 測定スロット 1 つの状態。 */
data class MeasurementSlot(
    val slot: Int,
    val enabled: Boolean,
    val type: MeasurementType?,
    val typeRaw: String?,
    val source: WaveformSource?,
    val secondSource: WaveformSource?,
    val unit: String?,
    val statistics: MeasurementStatistics,
) {
    /**
     * 値が測定できない状態か。
     *
     * マニュアル記載のとおり、測定できない場合は 9.91e37 が返る。
     * これを普通の数値として表示すると、極端に大きい値が出て混乱する。
     */
    val isNotMeasurable: Boolean
        get() = statistics.current?.let { MeasurementStatistics.isNotANumber(it) } ?: false

    companion object {
        fun empty(slot: Int) = MeasurementSlot(
            slot = slot,
            enabled = false,
            type = null,
            typeRaw = null,
            source = null,
            secondSource = null,
            unit = null,
            statistics = MeasurementStatistics.EMPTY,
        )
    }
}

/**
 * 測定の設定と読み出し。
 *
 * スロット数は機種依存（`CONFIGuration:NUMMEAS?`）。存在しないスロットを問い合わせると
 * 未定義ヘッダーでタイムアウトを待つことになるため、Capability の値を必ず使う。
 */
class MeasurementController(private val client: ScpiClient) {

    /** 統計の有効・無効。`MEASUrement:STATIstics:MODe`。 */
    suspend fun setStatisticsEnabled(enabled: Boolean): ScpiClient.ApplyResult<Boolean> = client.applyAndVerify(
        setCommand = TektronixCommands.Measurement.STATISTICS_MODE,
        value = if (enabled) "ALL" else "OFF",
        readBackQuery = "${TektronixCommands.Measurement.STATISTICS_MODE}?",
        parser = { response ->
            val value = ScpiResponseParser.unquote(ScpiResponseParser.stripHeader(response)).uppercase()
            value != "OFF"
        },
    )

    /** スロットを読み出す。統計は有効な場合のみ意味を持つ。 */
    suspend fun readSlot(slot: Int, withStatistics: Boolean = true): MeasurementSlot {
        val enabled = safeBoolean(TektronixCommands.Measurement.stateQuery(slot)) ?: false
        val typeRaw = safeValue(TektronixCommands.Measurement.typeQuery(slot))
        val sourceRaw = safeValue(TektronixCommands.Measurement.sourceQuery(slot, 1))
        val secondRaw = safeValue(TektronixCommands.Measurement.sourceQuery(slot, 2))

        val statistics = if (enabled) {
            MeasurementStatistics(
                current = safeDouble(TektronixCommands.Measurement.valueQuery(slot)),
                mean = if (withStatistics) safeDouble(TektronixCommands.Measurement.meanQuery(slot)) else null,
                minimum = if (withStatistics) safeDouble(TektronixCommands.Measurement.minimumQuery(slot)) else null,
                maximum = if (withStatistics) safeDouble(TektronixCommands.Measurement.maximumQuery(slot)) else null,
                standardDeviation = if (withStatistics) {
                    safeDouble(TektronixCommands.Measurement.standardDeviationQuery(slot))
                } else {
                    null
                },
                sampleCount = if (withStatistics) {
                    safeValue(TektronixCommands.Measurement.countQuery(slot))?.let {
                        ScpiResponseParser.parseLong(it)
                    }
                } else {
                    null
                },
            )
        } else {
            MeasurementStatistics.EMPTY
        }

        return MeasurementSlot(
            slot = slot,
            enabled = enabled,
            type = typeRaw?.let { MeasurementType.fromScpi(it) },
            typeRaw = typeRaw,
            source = sourceRaw?.let { WaveformSource.fromScpi(it) },
            secondSource = secondRaw?.let { WaveformSource.fromScpi(it) },
            unit = safeValue(TektronixCommands.Measurement.unitsQuery(slot)),
            statistics = statistics,
        )
    }

    suspend fun readAll(slotCount: Int, withStatistics: Boolean = true): List<MeasurementSlot> =
        (1..slotCount).map { readSlot(it, withStatistics) }

    /**
     * 測定を割り当てる。
     *
     * 種別 → ソース → 有効化の順に送る。有効化を先にすると、
     * 前の設定のまま一度測定が走ることがある。
     */
    suspend fun configureSlot(
        slot: Int,
        type: MeasurementType,
        source: WaveformSource,
        secondSource: WaveformSource? = null,
    ): ScpiClient.ApplyResult<MeasurementType> = try {
        client.write("${TektronixCommands.Measurement.type(slot)} ${type.scpiValue}")
        client.write("${TektronixCommands.Measurement.source(slot, 1)} ${source.scpiValue}")
        if (type.requiresSecondSource && secondSource != null) {
            client.write("${TektronixCommands.Measurement.source(slot, 2)} ${secondSource.scpiValue}")
        }
        client.write("${TektronixCommands.Measurement.state(slot)} ON")

        val error = client.errorQueue.classifyLatest(TektronixCommands.Measurement.type(slot))
        if (error != null) {
            ScpiClient.ApplyResult.Rejected(null, error)
        } else {
            val readBack = safeValue(TektronixCommands.Measurement.typeQuery(slot))
            ScpiClient.ApplyResult.Applied(null, readBack?.let { MeasurementType.fromScpi(it) })
        }
    } catch (exception: ScpiException) {
        ScpiClient.ApplyResult.Rejected(null, exception.error)
    }

    /** 測定を外す。 */
    suspend fun disableSlot(slot: Int): ScpiClient.ApplyResult<Boolean> = client.applyAndVerify(
        setCommand = TektronixCommands.Measurement.state(slot),
        value = "OFF",
        readBackQuery = TektronixCommands.Measurement.stateQuery(slot),
        parser = { ScpiResponseParser.parseBoolean(it) },
    )

    /**
     * 即時測定。画面へ表示せず 1 回だけ測る。
     *
     * スロットを消費しないため、一時的な確認に向く。
     */
    suspend fun measureImmediate(type: MeasurementType, source: WaveformSource): Pair<Double?, String?> {
        client.write("${TektronixCommands.Measurement.IMMEDIATE_TYPE} ${type.scpiValue}")
        client.write("${TektronixCommands.Measurement.immediateSource(1)} ${source.scpiValue}")
        val value = safeDouble(TektronixCommands.Measurement.IMMEDIATE_VALUE_QUERY)
        val unit = safeValue(TektronixCommands.Measurement.IMMEDIATE_UNITS_QUERY)
        return value to unit
    }

    private suspend fun safeValue(command: String): String? = try {
        client.queryValue(command).takeIf { it.isNotBlank() }
    } catch (exception: ScpiException) {
        if (exception.error is ScopeError.Disconnected) throw exception
        null
    }

    private suspend fun safeDouble(command: String): Double? = safeValue(command)?.toDoubleOrNull()

    private suspend fun safeBoolean(command: String): Boolean? = safeValue(command)?.let { ScpiResponseParser.parseBoolean(it) }
}
