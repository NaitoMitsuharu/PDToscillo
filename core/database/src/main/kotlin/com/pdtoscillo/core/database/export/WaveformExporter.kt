package com.pdtoscillo.core.database.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.pdtoscillo.core.common.Digest
import com.pdtoscillo.core.waveform.AnalogWaveform
import com.pdtoscillo.core.waveform.EnvelopeWaveform
import com.pdtoscillo.core.waveform.SpectrumTrace
import com.pdtoscillo.core.waveform.Waveform
import com.pdtoscillo.core.waveform.WaveformPreamble
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 保存した波形ファイルの情報。 */
data class ExportedFile(val file: File, val format: ExportFormat, val sizeBytes: Long, val sha256: String, val pointCount: Int)

enum class ExportFormat(val extension: String, val displayName: String) {
    CSV("csv", "CSV"),
    JSON("json", "JSON"),
    PNG("png", "PNG"),
    RAW("bin", "生バイナリ"),

    /** プリアンブル付きの独自形式。バージョン番号を持つ。 */
    PDTWFM("pdtwfm", "PDToscillo 波形"),
}

/**
 * 波形の書き出し。
 *
 * **波形本体はデータベースへ入れない。** 10 M 点の波形は数十 MB になり、
 * BLOB へ入れると DB が肥大化して扱えなくなる。
 * ファイルはアプリ専用ストレージへ置き、DB にはパス・サイズ・ハッシュだけを記録する。
 */
class WaveformExporter(private val context: Context) {

    /** アプリ専用の保存先。外部から読まれないため、計測データの取り扱いとして安全側。 */
    private val baseDirectory: File
        get() = File(context.filesDir, WAVEFORM_DIRECTORY).apply { mkdirs() }

    /**
     * ファイル名を組み立てる。
     *
     * テンプレートで使える差し込み: `{source}` `{timestamp}` `{index}`
     * パス区切りや親ディレクトリ参照は除去する。テンプレートは利用者が編集できるため、
     * そのまま連結するとアプリ専用ディレクトリの外へ書き込めてしまう。
     */
    fun buildFileName(
        template: String,
        source: String,
        format: ExportFormat,
        index: Int = 0,
        timestamp: Long = System.currentTimeMillis(),
    ): String {
        val stamp = SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).format(Date(timestamp))
        val expanded = template
            .replace("{source}", source)
            .replace("{timestamp}", stamp)
            .replace("{index}", index.toString())
        return "${sanitizeFileName(expanded)}.${format.extension}"
    }

    /**
     * ファイル名から危険な文字を取り除く。
     *
     * `..` とパス区切りを許すと、保存先ディレクトリの外へ書き込める。
     */
    internal fun sanitizeFileName(name: String): String {
        val cleaned = name
            .replace('\\', '_')
            .replace('/', '_')
            .replace("..", "_")
            .filter { it.isLetterOrDigit() || it in ALLOWED_NAME_CHARS }
            .trim('.', ' ', '_')
        return cleaned.ifEmpty { DEFAULT_FILE_NAME }.take(MAX_FILE_NAME_LENGTH)
    }

    /**
     * 保存先を解決する。
     *
     * 正規化した絶対パスが保存先ディレクトリの内側にあることを必ず確認する。
     * 本体から受け取ったパスや利用者が入力した名前を、そのまま信用しない。
     */
    internal fun resolveInsideBase(fileName: String): File {
        val base = baseDirectory.canonicalFile
        val target = File(base, sanitizeFileName(fileName)).canonicalFile
        require(target.path.startsWith(base.path + File.separator) || target.parentFile == base) {
            "保存先がアプリ専用ディレクトリの外を指しています: ${target.path}"
        }
        return target
    }

    /** CSV。時間と値の 2 列。単位はヘッダ行へ書く。 */
    fun exportCsv(waveform: Waveform, fileName: String): ExportedFile {
        val target = resolveInsideBase(fileName)
        target.bufferedWriter().use { writer ->
            when (waveform) {
                is AnalogWaveform -> {
                    writer.write("time[${waveform.preamble.xUnit ?: "s"}],value[${waveform.preamble.yUnit ?: "V"}]\n")
                    for (index in waveform.volts.indices) {
                        writer.write("${waveform.times[index]},${waveform.volts[index]}\n")
                    }
                }

                is EnvelopeWaveform -> {
                    writer.write("time[s],min[V],max[V]\n")
                    for (index in waveform.times.indices) {
                        writer.write(
                            "${waveform.times[index]},${waveform.minVoltsPerPoint[index]}," +
                                "${waveform.maxVoltsPerPoint[index]}\n",
                        )
                    }
                }

                is SpectrumTrace -> {
                    writer.write("frequency[Hz],amplitude[${waveform.unit}]\n")
                    for (index in waveform.amplitudes.indices) {
                        writer.write("${waveform.frequencies[index]},${waveform.amplitudes[index]}\n")
                    }
                }

                else -> {
                    writer.write("index,value\n")
                    // デジタル系は論理値のまま出す。電圧へ変換しない。
                    writeGenericPoints(waveform, writer::write)
                }
            }
        }
        return describe(target, ExportFormat.CSV, waveform.pointCount)
    }

    /** JSON。プリアンブルを含め、後から解釈できる形にする。 */
    fun exportJson(waveform: Waveform, fileName: String): ExportedFile {
        val target = resolveInsideBase(fileName)
        target.bufferedWriter().use { writer ->
            writer.write("{\n")
            writer.write("  \"formatVersion\": $FORMAT_VERSION,\n")
            writer.write("  \"source\": \"${waveform.source.scpiValue}\",\n")
            writer.write("  \"capturedAtEpochMillis\": ${waveform.capturedAtEpochMillis},\n")
            writer.write("  \"pointCount\": ${waveform.pointCount},\n")
            writer.write("  \"preamble\": ${preambleJson(waveform.preamble)},\n")
            writer.write("  \"values\": [")
            when (waveform) {
                is AnalogWaveform -> writer.write(waveform.volts.joinToString(","))
                is SpectrumTrace -> writer.write(waveform.amplitudes.joinToString(","))
                is EnvelopeWaveform -> writer.write(
                    waveform.times.indices.joinToString(",") {
                        "[${waveform.minVoltsPerPoint[it]},${waveform.maxVoltsPerPoint[it]}]"
                    },
                )

                else -> writeGenericPoints(waveform) { writer.write(it) }
            }
            writer.write("]\n}\n")
        }
        return describe(target, ExportFormat.JSON, waveform.pointCount)
    }

    /**
     * PNG。画面と同じ見た目で書き出す。
     *
     * 描画は Compose とは独立に行う。画面の状態に依存させると、
     * 「表示していないと保存できない」制約が生まれる。
     */
    @Suppress("LongParameterList")
    fun exportPng(
        waveform: Waveform,
        fileName: String,
        width: Int = DEFAULT_PNG_WIDTH,
        height: Int = DEFAULT_PNG_HEIGHT,
        traceColor: Int = DEFAULT_TRACE_COLOR,
    ): ExportedFile {
        val target = resolveInsideBase(fileName)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND_COLOR)

        val gridPaint = Paint().apply {
            color = GRID_COLOR
            strokeWidth = 1f
        }
        for (index in 1 until GRID_DIVISIONS) {
            val x = width.toFloat() * index / GRID_DIVISIONS
            val y = height.toFloat() * index / GRID_DIVISIONS
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        val values = when (waveform) {
            is AnalogWaveform -> waveform.volts
            is SpectrumTrace -> waveform.amplitudes
            is EnvelopeWaveform -> waveform.maxVoltsPerPoint
            else -> DoubleArray(0)
        }

        if (values.isNotEmpty()) {
            val minimum = values.min()
            val maximum = values.max()
            val span = (maximum - minimum).takeIf { it > 0 } ?: 1.0
            val tracePaint = Paint().apply {
                color = traceColor
                strokeWidth = 2f
                isAntiAlias = true
                style = Paint.Style.STROKE
            }
            val path = android.graphics.Path()
            // 横ピクセル数を超える点は間引く。全点描画すると巨大な波形で時間がかかる。
            val step = (values.size / width).coerceAtLeast(1)
            var index = 0
            var first = true
            while (index < values.size) {
                val x = index.toFloat() / values.size * width
                val y = (height - ((values[index] - minimum) / span) * height).toFloat()
                if (first) {
                    path.moveTo(x, y)
                    first = false
                } else {
                    path.lineTo(x, y)
                }
                index += step
            }
            canvas.drawPath(path, tracePaint)
        }

        target.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
        }
        bitmap.recycle()
        return describe(target, ExportFormat.PNG, waveform.pointCount)
    }

    /** 生バイナリ。`CURVe?` の本体そのまま。 */
    fun exportRaw(payload: ByteArray, fileName: String, pointCount: Int): ExportedFile {
        val target = resolveInsideBase(fileName)
        target.writeBytes(payload)
        return describe(target, ExportFormat.RAW, pointCount)
    }

    /**
     * プリアンブル付き独自形式。
     *
     * 先頭にバージョン番号を持たせ、後から形式を変えても読み分けられるようにする。
     * 本体はテキストのプリアンブルと生バイナリの連結。
     */
    fun exportPdtWfm(preamble: WaveformPreamble, payload: ByteArray, source: String, fileName: String): ExportedFile {
        val target = resolveInsideBase(fileName)
        target.outputStream().buffered().use { stream ->
            val header = buildString {
                append("PDTWFM\n")
                append("version=$FORMAT_VERSION\n")
                append("source=$source\n")
                append("capturedAt=${System.currentTimeMillis()}\n")
                append("payloadBytes=${payload.size}\n")
                append("preamble=${preamble.raw.replace("\n", " ")}\n")
                append("---\n")
            }
            stream.write(header.toByteArray(Charsets.UTF_8))
            stream.write(payload)
        }
        return describe(target, ExportFormat.PDTWFM, preamble.pointCount ?: 0)
    }

    /** 保存済みファイルの一覧。新しい順。 */
    fun listExports(): List<File> = baseDirectory.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** ファイルを削除する。保存先の外は触らない。 */
    fun delete(fileName: String): Boolean = runCatching { resolveInsideBase(fileName).delete() }.getOrDefault(false)

    private fun describe(file: File, format: ExportFormat, pointCount: Int): ExportedFile = ExportedFile(
        file = file,
        format = format,
        sizeBytes = file.length(),
        sha256 = Digest.sha256(file.readBytes()),
        pointCount = pointCount,
    )

    private fun preambleJson(preamble: WaveformPreamble): String = buildString {
        append("{")
        append("\"bytesPerPoint\":${preamble.bytesPerPoint},")
        append("\"bitsPerPoint\":${preamble.bitsPerPoint},")
        append("\"encoding\":\"${preamble.encoding}\",")
        append("\"binaryFormat\":\"${preamble.binaryFormat}\",")
        append("\"byteOrder\":\"${preamble.byteOrder}\",")
        append("\"pointCount\":${preamble.pointCount},")
        append("\"xIncrement\":${preamble.xIncrement},")
        append("\"xZero\":${preamble.xZero},")
        append("\"pointOffset\":${preamble.pointOffset},")
        append("\"yMultiplier\":${preamble.yMultiplier},")
        append("\"yOffset\":${preamble.yOffset},")
        append("\"yZero\":${preamble.yZero},")
        append("\"xUnit\":\"${preamble.xUnit}\",")
        append("\"yUnit\":\"${preamble.yUnit}\"")
        append("}")
    }

    private inline fun writeGenericPoints(waveform: Waveform, write: (String) -> Unit) {
        when (waveform) {
            is com.pdtoscillo.core.waveform.DigitalWaveform ->
                waveform.levels.forEachIndexed { index, level -> write("$index,$level\n") }

            is com.pdtoscillo.core.waveform.DigitalCollectionWaveform ->
                waveform.bitPatterns.forEachIndexed { index, pattern -> write("$index,$pattern\n") }

            else -> Unit
        }
    }

    companion object {
        /** 独自形式のバージョン。形式を変えたら上げる。 */
        const val FORMAT_VERSION = 1

        const val DEFAULT_FILE_NAME_TEMPLATE = "{source}_{timestamp}"

        private const val WAVEFORM_DIRECTORY = "waveforms"
        private const val TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"
        private const val MAX_FILE_NAME_LENGTH = 120
        private const val DEFAULT_FILE_NAME = "waveform"
        private val ALLOWED_NAME_CHARS = setOf('-', '_', '.')

        private const val DEFAULT_PNG_WIDTH = 1280
        private const val DEFAULT_PNG_HEIGHT = 720
        private const val PNG_QUALITY = 100
        private const val GRID_DIVISIONS = 10
        private val BACKGROUND_COLOR = Color.rgb(11, 15, 13)
        private val GRID_COLOR = Color.rgb(55, 71, 79)
        private val DEFAULT_TRACE_COLOR = Color.rgb(255, 235, 59)
    }
}
