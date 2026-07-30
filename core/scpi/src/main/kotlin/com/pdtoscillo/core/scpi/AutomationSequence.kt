package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.common.PdtLog
import com.pdtoscillo.core.model.MeasurementType
import com.pdtoscillo.core.model.ScopeError
import com.pdtoscillo.core.model.WaveformSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.coroutineContext

/** 自動測定の設定。 */
data class AutomationConfig(
    /** 実行回数。0 以下は不可。無限ループを作らない。 */
    val iterations: Int = DEFAULT_ITERATIONS,
    /** 各回の間隔。 */
    val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    /** Single 実行後、取得完了を待つ上限。 */
    val acquisitionTimeoutMillis: Long = DEFAULT_ACQUISITION_TIMEOUT_MILLIS,
    /** 取得対象。 */
    val sources: List<WaveformSource> = listOf(WaveformSource.CH1),
    /** 取得する測定。 */
    val measurements: List<MeasurementType> = listOf(MeasurementType.FREQUENCY, MeasurementType.PEAK_TO_PEAK),
    val captureWaveform: Boolean = true,
    val captureMeasurements: Boolean = true,
    /** 本体側で画面イメージを保存する。 */
    val captureScreenImage: Boolean = false,
    /** ファイル名テンプレート。`{source}` `{timestamp}` `{index}` が使える。 */
    val fileNameTemplate: String = "{source}_{index}_{timestamp}",
    /** エラー時に停止するか。false なら記録して次へ進む。 */
    val stopOnError: Boolean = true,
    /** 全体の上限時間。超えたら打ち切る。 */
    val maxDurationMillis: Long = DEFAULT_MAX_DURATION_MILLIS,
) {
    init {
        require(iterations in 1..MAX_ITERATIONS) {
            "実行回数は 1〜$MAX_ITERATIONS の範囲で指定してください（無限ループを防ぐため）"
        }
        require(intervalMillis >= 0) { "間隔は 0 以上である必要があります" }
        require(maxDurationMillis > 0) { "上限時間は正の値である必要があります" }
    }

    companion object {
        const val DEFAULT_ITERATIONS = 10
        const val DEFAULT_INTERVAL_MILLIS = 1_000L
        const val DEFAULT_ACQUISITION_TIMEOUT_MILLIS = 30_000L
        const val DEFAULT_MAX_DURATION_MILLIS = 60L * 60 * 1000

        /** 実行回数の上限。誤入力による事実上の無限ループを防ぐ。 */
        const val MAX_ITERATIONS = 10_000
    }
}

/** 1 回分の結果。 */
data class AutomationIteration(
    val index: Int,
    val startedAtEpochMillis: Long,
    val elapsedMillis: Long,
    val measurements: Map<String, Double?>,
    val waveformPointCounts: Map<WaveformSource, Int>,
    val error: ScopeError?,
) {
    val succeeded: Boolean get() = error == null
}

/** 進捗の通知。 */
sealed interface AutomationProgress {
    data class Started(val config: AutomationConfig) : AutomationProgress

    data class Step(val index: Int, val total: Int, val description: String) : AutomationProgress

    data class IterationFinished(val iteration: AutomationIteration) : AutomationProgress

    data class Finished(val completed: Int, val failed: Int, val stoppedReason: String?) : AutomationProgress
}

/**
 * 自動測定シーケンス。
 *
 * 手順は次のとおり。
 * 1. 設定を適用（呼び出し側が事前に済ませる）
 * 2. Single acquisition
 * 3. 取得完了待ち
 * 4. 測定値取得
 * 5. 波形取得
 * 6. 保存（呼び出し側のコールバックで行う）
 * 7. 条件に応じて繰り返す
 *
 * **無限ループを作らない。** 実行回数、上限時間、取得完了待ちの上限をすべて設ける。
 * また、コルーチンがキャンセルされたら即座に止まる。
 */
class AutomationSequence(
    private val client: ScpiClient,
    private val driver: Tektronix4000Driver,
    private val measurementController: MeasurementController,
    private val waveformTransfer: WaveformTransfer,
) {

    /**
     * 実行する。
     *
     * @param onWaveform 取得した波形の保存など、副作用を伴う処理を呼び出し側へ委ねる。
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod")
    suspend fun run(
        config: AutomationConfig,
        onProgress: (AutomationProgress) -> Unit = {},
        onWaveform: suspend (index: Int, capture: WaveformCapture) -> Unit = { _, _ -> },
    ): List<AutomationIteration> {
        onProgress(AutomationProgress.Started(config))
        val results = mutableListOf<AutomationIteration>()
        val startedAt = System.currentTimeMillis()
        var stoppedReason: String? = null

        // 測定スロットを準備する。毎回設定し直すと本体の負荷が上がる。
        if (config.captureMeasurements) {
            config.measurements.forEachIndexed { slotIndex, type ->
                val slot = slotIndex + 1
                runCatching {
                    measurementController.configureSlot(
                        slot = slot,
                        type = type,
                        source = config.sources.firstOrNull() ?: WaveformSource.CH1,
                    )
                }
            }
        }

        for (index in 1..config.iterations) {
            // キャンセルされたら止める。
            if (!coroutineContext.isActive) {
                stoppedReason = "中断されました"
                break
            }
            // 上限時間を超えたら打ち切る。
            if (System.currentTimeMillis() - startedAt > config.maxDurationMillis) {
                stoppedReason = "上限時間 ${config.maxDurationMillis} ms を超えたため打ち切りました"
                break
            }

            val iterationStarted = System.currentTimeMillis()
            var error: ScopeError? = null
            val measurements = mutableMapOf<String, Double?>()
            val pointCounts = mutableMapOf<WaveformSource, Int>()

            try {
                onProgress(AutomationProgress.Step(index, config.iterations, "単発取得"))
                driver.single()

                onProgress(AutomationProgress.Step(index, config.iterations, "取得完了を待機"))
                val completed = waitForAcquisition(config.acquisitionTimeoutMillis)
                if (!completed) {
                    error = ScopeError.InstrumentBusy(
                        "取得完了を ${config.acquisitionTimeoutMillis} ms 待っても確認できませんでした",
                    )
                }

                if (error == null && config.captureMeasurements) {
                    onProgress(AutomationProgress.Step(index, config.iterations, "測定値を取得"))
                    config.measurements.forEachIndexed { slotIndex, type ->
                        val slot = measurementController.readSlot(slotIndex + 1, withStatistics = false)
                        measurements[type.displayName] = slot.statistics.current
                    }
                }

                if (error == null && config.captureWaveform) {
                    for (source in config.sources) {
                        onProgress(
                            AutomationProgress.Step(index, config.iterations, "${source.displayName} の波形を取得"),
                        )
                        val capture = waveformTransfer.capture(source)
                        pointCounts[source] = capture.waveform.pointCount
                        onWaveform(index, capture)
                    }
                }

                if (error == null && config.captureScreenImage) {
                    onProgress(AutomationProgress.Step(index, config.iterations, "画面イメージを保存"))
                    val fileName = config.fileNameTemplate
                        .replace("{source}", "SCREEN")
                        .replace("{index}", index.toString())
                        .replace("{timestamp}", System.currentTimeMillis().toString())
                    runCatching {
                        client.write("${TektronixCommands.Files.SAVE_IMAGE} \"$fileName.png\"")
                    }
                }
            } catch (exception: ScpiException) {
                error = exception.error
                PdtLog.w(TAG, "$index 回目で失敗しました: ${exception.error}")
            }

            val iteration = AutomationIteration(
                index = index,
                startedAtEpochMillis = iterationStarted,
                elapsedMillis = System.currentTimeMillis() - iterationStarted,
                measurements = measurements,
                waveformPointCounts = pointCounts,
                error = error,
            )
            results += iteration
            onProgress(AutomationProgress.IterationFinished(iteration))

            if (error != null && config.stopOnError) {
                stoppedReason = "$index 回目で失敗したため停止しました"
                break
            }

            // 最後の回のあとは待たない。
            if (index < config.iterations && config.intervalMillis > 0) {
                delay(config.intervalMillis)
            }
        }

        onProgress(
            AutomationProgress.Finished(
                completed = results.count { it.succeeded },
                failed = results.count { !it.succeeded },
                stoppedReason = stoppedReason,
            ),
        )
        return results
    }

    /**
     * 取得完了を待つ。
     *
     * `BUSY?` が 0 になるまで待つ。上限を超えたら false を返し、無限に待たない。
     */
    private suspend fun waitForAcquisition(timeoutMillis: Long): Boolean = withTimeoutOrNull(timeoutMillis) {
        while (coroutineContext.isActive) {
            val busy = runCatching { client.isBusy() }.getOrNull()
            if (busy == false) return@withTimeoutOrNull true
            delay(POLL_INTERVAL_MILLIS)
        }
        false
    } ?: false

    private companion object {
        const val TAG = "AutomationSequence"
        const val POLL_INTERVAL_MILLIS = 100L
    }
}
