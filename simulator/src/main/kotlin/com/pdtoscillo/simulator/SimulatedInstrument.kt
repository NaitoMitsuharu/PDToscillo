package com.pdtoscillo.simulator

import java.util.ArrayDeque
import java.util.Locale

/** 疑似オシロスコープの応答。 */
sealed interface SimulatedResponse {
    /** 応答を返さないコマンド（設定変更）。 */
    data object None : SimulatedResponse

    data class Text(val value: String) : SimulatedResponse

    /** IEEE 488.2 ブロックとして既に組み立て済みのバイト列。 */
    data class Binary(val value: ByteArray) : SimulatedResponse {
        override fun equals(other: Any?): Boolean = this === other

        override fun hashCode(): Int = System.identityHashCode(this)
    }
}

/** SCPI イベント（エラーキューの 1 件）。 */
data class SimulatedEvent(val code: Int, val message: String)

/**
 * 疑似オシロスコープの状態と SCPI 解釈。
 *
 * 実機の挙動のうち、受信側の実装を誤らせやすい点を意図的に再現する。
 *
 * - 未定義コマンドには**応答を返さず**、イベントキューへ 113 を積む（実機と同じ）。
 *   受信側はタイムアウト後にエラーキューを見て「未対応」と判断する必要がある。
 * - `WFMOutpre?` はヘッダ有無を切り替えられる。
 * - 波形が非表示のときは転送パラメータのみ返し、イベントを積む。
 */
class SimulatedInstrument(private val config: SimulatorConfig) {
    private val model = config.model

    private val events = ArrayDeque<SimulatedEvent>()

    /** 設定値。キーは正規化した大文字のコマンドパス。 */
    private val settings = mutableMapOf<String, String>()

    /** 表示中の波形ソース。非表示ソースへの `CURVe?` はエラーにする。 */
    private val displayedSources = mutableSetOf("CH1")

    private var standardEventStatus: Int = 0
    private var headerEnabled: Boolean = true
    private var acquisitionRunning: Boolean = true

    init {
        settings["HORIZONTAL:SCALE"] = formatEng(DEFAULT_HORIZONTAL_SCALE)
        settings["HORIZONTAL:POSITION"] = "50.0000"
        settings["HORIZONTAL:RECORDLENGTH"] = DEFAULT_RECORD_LENGTH.toString()
        settings["ACQUIRE:MODE"] = "SAMPLE"
        settings["ACQUIRE:STOPAFTER"] = "RUNSTOP"
        settings["ACQUIRE:NUMAVG"] = "16"
        settings["DATA:SOURCE"] = "CH1"
        settings["DATA:START"] = "1"
        settings["DATA:STOP"] = DEFAULT_RECORD_LENGTH.toString()
        settings["DATA:ENCDG"] = "RIBINARY"
        settings["DATA:WIDTH"] = "1"
        settings["ACQUIRE:FASTACQ:STATE"] = "0"
        settings["MEASUREMENT:STATISTICS:MODE"] = "OFF"
        settings["MEASUREMENT:IMMED:TYPE"] = "FREQUENCY"
        settings["MEASUREMENT:IMMED:SOURCE1"] = "CH1"
        for (slot in 1..MEASUREMENT_SLOTS) {
            settings["MEASUREMENT:MEAS$slot:TYPE"] = "FREQUENCY"
            settings["MEASUREMENT:MEAS$slot:SOURCE1"] = "CH1"
            settings["MEASUREMENT:MEAS$slot:SOURCE2"] = "CH2"
            settings["MEASUREMENT:MEAS$slot:STATE"] = "0"
        }
        settings["TRIGGER:A:TYPE"] = "EDGE"
        settings["TRIGGER:A:MODE"] = "AUTO"
        settings["TRIGGER:A:PULSE:CLASS"] = "WIDTH"
        settings["TRIGGER:A:LOGIC:CLASS"] = "LOGIC"
        settings["TRIGGER:A:EDGE:SOURCE"] = "CH1"
        settings["TRIGGER:A:EDGE:SLOPE"] = "RISE"
        settings["TRIGGER:A:EDGE:COUPLING"] = "DC"
        settings["TRIGGER:A:HOLDOFF:TIME"] = "20.0000E-9"
        for (channel in 1..model.analogChannels) {
            settings["TRIGGER:A:LEVEL:CH$channel"] = "0.0000"
            settings["CH$channel:SCALE"] = formatEng(DEFAULT_VERTICAL_SCALE)
            settings["CH$channel:POSITION"] = "0.0000"
            settings["CH$channel:OFFSET"] = "0.0000"
            settings["CH$channel:COUPLING"] = "DC"
            settings["CH$channel:BANDWIDTH"] = "FULL"
            settings["CH$channel:INVERT"] = "0"
            settings["CH$channel:LABEL"] = "\"\""
            settings["CH$channel:TERMINATION"] = "1.0000E+6"
            settings["CH$channel:DESKEW"] = "0.0000"
            settings["CH$channel:PROBE:GAIN"] = "1.0000"
            settings["SELECT:CH$channel"] = if (channel == 1) "1" else "0"
        }
    }

    /**
     * 1 行の入力を処理する。セミコロンで連結された複合メッセージにも対応する。
     *
     * 複合メッセージの応答はセミコロンで連結して返す（実機と同じ）。
     */
    fun handle(rawLine: String): SimulatedResponse {
        val line = rawLine.trim()
        if (line.isEmpty()) return SimulatedResponse.None

        if (config.faultMode == FaultMode.UNSUPPORTED_COMMAND) {
            pushEvent(EVENT_UNDEFINED_HEADER, "Undefined header")
            return SimulatedResponse.None
        }

        val parts = splitCompound(line)
        val textPieces = mutableListOf<String>()
        for (part in parts) {
            when (val response = handleSingle(part)) {
                is SimulatedResponse.Binary -> {
                    // バイナリ応答は連結しない。実機でも波形転送は単独で扱う。
                    return response
                }

                is SimulatedResponse.Text -> textPieces += response.value
                SimulatedResponse.None -> Unit
            }
        }
        return if (textPieces.isEmpty()) SimulatedResponse.None else SimulatedResponse.Text(textPieces.joinToString(";"))
    }

    /** 引用符の内側のセミコロンでは分割しない。 */
    private fun splitCompound(line: String): List<String> {
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    builder.append(char)
                }

                char == ';' && !inQuotes -> {
                    result += builder.toString()
                    builder.clear()
                }

                else -> builder.append(char)
            }
        }
        if (builder.isNotEmpty()) result += builder.toString()
        return result.map { it.trim() }.filter { it.isNotEmpty() }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun handleSingle(command: String): SimulatedResponse {
        val isQuery = command.endsWith("?")
        val head = command.substringBefore(' ').trim().removeSuffix("?").trimStart(':')
        val argument = command.substringAfter(' ', missingDelimiterValue = "").trim()
        val key = normalize(head)

        commonCommand(key, isQuery, argument)?.let { return it }
        configurationQuery(key, isQuery)?.let { return it }
        waveformCommand(key, isQuery, argument)?.let { return it }
        stateCommand(key, isQuery, argument)?.let { return it }
        measurementQuery(key, isQuery)?.let { return it }

        // 既知の設定コマンドは汎用処理で受ける。
        if (settings.containsKey(key)) {
            return if (isQuery) {
                textWithHeader(head, settings.getValue(key))
            } else {
                if (config.faultMode == FaultMode.BUSY) {
                    pushEvent(EVENT_EXECUTION_ERROR, "Busy")
                } else {
                    settings[key] = argument.uppercase(Locale.US)
                }
                SimulatedResponse.None
            }
        }

        // 未知のコマンド: 実機と同じく応答を返さず、イベントを積む。
        pushEvent(EVENT_UNDEFINED_HEADER, "Undefined header; $head")
        return SimulatedResponse.None
    }

    private fun commonCommand(key: String, isQuery: Boolean, argument: String): SimulatedResponse? = when (key) {
        "*IDN" -> if (isQuery) SimulatedResponse.Text(model.idnResponse) else null
        "ID" -> if (isQuery) SimulatedResponse.Text("TEK/${model.idnModel}") else null
        "*ESR" -> if (isQuery) {
            val value = standardEventStatus
            standardEventStatus = 0
            SimulatedResponse.Text(value.toString())
        } else {
            null
        }

        "EVMSG" -> if (isQuery) {
            val event = events.pollFirst()
            SimulatedResponse.Text(
                if (event == null) "0,\"No events to report - queue empty\"" else "${event.code},\"${event.message}\"",
            )
        } else {
            null
        }

        "ALLEV" -> if (isQuery) SimulatedResponse.Text(drainAllEvents()) else null
        "EVENT" -> if (isQuery) SimulatedResponse.Text((events.pollFirst()?.code ?: 0).toString()) else null
        "EVQTY" -> if (isQuery) SimulatedResponse.Text(events.size.toString()) else null
        "*OPC" -> if (isQuery) SimulatedResponse.Text("1") else SimulatedResponse.None
        "BUSY" -> if (isQuery) {
            SimulatedResponse.Text(if (config.faultMode == FaultMode.BUSY) "1" else "0")
        } else {
            null
        }

        "*CLS" -> {
            events.clear()
            standardEventStatus = 0
            SimulatedResponse.None
        }

        "*RST", "FACTORY" -> SimulatedResponse.None

        "HEADER" -> if (isQuery) {
            SimulatedResponse.Text(if (headerEnabled) "1" else "0")
        } else {
            headerEnabled = argument.uppercase(Locale.US) in setOf("ON", "1")
            SimulatedResponse.None
        }

        "VERBOSE" -> if (isQuery) SimulatedResponse.Text("1") else SimulatedResponse.None

        else -> null
    }

    /**
     * `CONFIGuration:*?` クエリ群。
     *
     * 無印世代を模したモデルでは意図的に未定義として扱い、受信側のフォールバック経路を検証できるようにする。
     */
    @Suppress("CyclomaticComplexMethod")
    private fun configurationQuery(key: String, isQuery: Boolean): SimulatedResponse? {
        if (!key.startsWith("CONFIGURATION:")) return null
        if (!isQuery) return SimulatedResponse.None
        if (!model.supportsConfigurationQueries) {
            pushEvent(EVENT_UNDEFINED_HEADER, "Undefined header; $key")
            return SimulatedResponse.None
        }
        val value = when (key.removePrefix("CONFIGURATION:")) {
            "ANALOG:NUMCHANNELS" -> model.analogChannels.toString()
            "ANALOG:BANDWIDTH", "ANALOG:MAXBANDWIDTH" -> "1.0000E+9"
            "ANALOG:MAXSAMPLERATE" -> "5.0000E+9"
            "ANALOG:RECLENS" -> "1000,10000,100000,1000000,10000000"
            "ANALOG:VERTINVERT" -> "1"
            "ANALOG:GNDCPLG" -> "0"
            "DIGITAL:NUMCHANNELS" -> model.digitalChannels.toString()
            "DIGITAL:MAXSAMPLERATE" -> if (model.digitalChannels > 0) "5.0000E+8" else "0"
            "DIGITAL:MAGNIVU" -> if (model.digitalChannels > 0) "1" else "0"
            "RF:NUMCHANNELS" -> if (model.hasRf) "1" else "0"
            "RF:BANDWIDTH", "RF:MAXBANDWIDTH" -> if (model.hasRf) "6.0000E+9" else "0"
            "RF:ADVTRIG" -> if (model.hasRf) "1" else "0"
            "AFG" -> boolValue(model.hasAfg)
            "ARB" -> boolValue(model.hasAfg)
            "DVM" -> boolValue(model.hasDvm)
            "ADVMATH" -> "1"
            "HISTOGRAM" -> "1"
            "EXTVIDEO" -> "0"
            "AUXIN" -> "1"
            "ROSC" -> boolValue(model.hasRf)
            "NETWORKDRIVES" -> "1"
            "NUMMEAS" -> "4"
            "REFS:NUMREFS" -> "4"
            "BUSWAVEFORMS:NUMBUS" -> "2"
            "BUSWAVEFORMS:I2C", "BUSWAVEFORMS:SPI", "BUSWAVEFORMS:RS232C" -> "1"
            "BUSWAVEFORMS:CAN", "BUSWAVEFORMS:LIN" -> "1"
            "BUSWAVEFORMS:PARALLEL" -> boolValue(model.digitalChannels > 0)
            "BUSWAVEFORMS:USB", "BUSWAVEFORMS:USB:HS" -> "0"
            "BUSWAVEFORMS:ETHERNET", "BUSWAVEFORMS:FLEXRAY" -> "0"
            "BUSWAVEFORMS:AUDIO", "BUSWAVEFORMS:MIL1553B" -> "0"
            "APPLICATIONS:POWER", "APPLICATIONS:LIMITMASK" -> "0"
            "APPLICATIONS:CUSTOMMASK", "APPLICATIONS:STANDARDMASK" -> "0"
            "APPLICATIONS:VIDPIC" -> "0"
            else -> {
                pushEvent(EVENT_UNDEFINED_HEADER, "Undefined header; $key")
                return SimulatedResponse.None
            }
        }
        return SimulatedResponse.Text(value)
    }

    private fun stateCommand(key: String, isQuery: Boolean, argument: String): SimulatedResponse? = when (key) {
        "ACQUIRE:STATE" -> if (isQuery) {
            SimulatedResponse.Text(if (acquisitionRunning) "1" else "0")
        } else {
            acquisitionRunning = argument.uppercase(Locale.US) in setOf("RUN", "ON", "1")
            SimulatedResponse.None
        }

        "TRIGGER:STATE" -> if (isQuery) {
            SimulatedResponse.Text(if (acquisitionRunning) "TRIGGER" else "SAVE")
        } else {
            null
        }

        "TRIGGER" -> {
            // `TRIGger FORCe`
            SimulatedResponse.None
        }

        "HORIZONTAL:SAMPLERATE" -> if (isQuery) SimulatedResponse.Text("2.5000E+9") else null

        "ACQUIRE:NUMACQ" -> if (isQuery) SimulatedResponse.Text("42") else null

        "SELECT" -> if (isQuery) SimulatedResponse.Text(displayedSources.joinToString(";")) else null

        else -> null
    }

    private fun measurementQuery(key: String, isQuery: Boolean): SimulatedResponse? {
        if (!isQuery) return null
        if (!key.startsWith("MEASUREMENT:")) return null
        val tail = key.substringAfterLast(':')
        val value = when (tail) {
            "VALUE", "MEAN" -> "1.0000E+3"
            "MINIMUM" -> "9.9000E+2"
            "MAXIMUM" -> "1.0100E+3"
            "STDDEV" -> "3.0000E+0"
            "COUNT" -> "128"
            "UNITS" -> "\"Hz\""
            else -> return null
        }
        return SimulatedResponse.Text(value)
    }

    @Suppress("ReturnCount")
    private fun waveformCommand(key: String, isQuery: Boolean, argument: String): SimulatedResponse? {
        when (key) {
            "WFMOUTPRE" -> if (isQuery) return SimulatedResponse.Text(buildPreamble())
            "CURVE" -> if (isQuery) return buildCurve()
            "SELECT:CH1", "SELECT:CH2", "SELECT:CH3", "SELECT:CH4" -> {
                val source = key.removePrefix("SELECT:")
                if (!isQuery) {
                    if (argument.uppercase(Locale.US) in setOf("ON", "1")) {
                        displayedSources += source
                    } else {
                        displayedSources -= source
                    }
                    settings[key] = if (source in displayedSources) "1" else "0"
                    return SimulatedResponse.None
                }
                return textWithHeader(key, if (source in displayedSources) "1" else "0")
            }
        }
        if (key.startsWith("WFMOUTPRE:") && isQuery) {
            val field = key.removePrefix("WFMOUTPRE:")
            preambleField(field)?.let { return SimulatedResponse.Text(it) }
        }
        return null
    }

    /**
     * `WFMOutpre?` の応答。
     *
     * フィールドの並びはマニュアル記載の例と同じ順序にする。
     * ヘッダ有効時は `:WFMOUTPRE:BYT_NR 1;BIT_NR 8;...` の形になる。
     */
    private fun buildPreamble(): String {
        // マニュアル記載の挙動: DATa:SOUrce の波形が表示されていない場合は
        // 転送パラメータ (BYT_Nr, BIT_Nr, ENCdg, BN_Fmt, BYT_Or) だけを返し、
        // 「source waveform is not turned on」のイベントを積む。
        // スケーリング項目が欠けるため、受信側はここで気付けなければならない。
        val fields = if (isSourceDisplayed()) {
            preambleFields()
        } else {
            pushEvent(EVENT_EXECUTION_ERROR, "Source waveform is not turned on")
            LinkedHashMap(preambleFields().entries.take(TRANSMISSION_FIELD_COUNT).associate { it.key to it.value })
        }
        return if (headerEnabled) {
            fields.entries.mapIndexed { index, entry ->
                if (index == 0) ":WFMOUTPRE:${entry.key} ${entry.value}" else "${entry.key} ${entry.value}"
            }.joinToString(";")
        } else {
            fields.values.joinToString(";")
        }
    }

    private fun preambleField(field: String): String? = preambleFields()[field]

    private fun preambleFields(): LinkedHashMap<String, String> {
        val bytesPerPoint = settings["DATA:WIDTH"]?.toIntOrNull() ?: 1
        val encoding = settings["DATA:ENCDG"] ?: "RIBINARY"
        val pointCount = effectivePointCount()
        val generated = WaveformFactory.generate(
            shape = config.waveformShape,
            pointCount = pointCount,
            bytesPerPoint = bytesPerPoint,
            signed = isSignedEncoding(encoding),
        )
        val fields = LinkedHashMap<String, String>()
        fields["BYT_NR"] = bytesPerPoint.toString()
        fields["BIT_NR"] = (bytesPerPoint * BITS_PER_BYTE).toString()
        fields["ENCDG"] = if (encoding.startsWith("ASC")) "ASCII" else "BINARY"
        fields["BN_FMT"] = binaryFormat(encoding)
        fields["BYT_OR"] = byteOrder(encoding)
        fields["WFID"] = "\"Ch1, DC coupling, 100.0mV/div, 4.000us/div, $pointCount points, Sample mode\""
        fields["NR_PT"] = pointCount.toString()
        fields["PT_FMT"] = "Y"
        fields["XUNIT"] = "\"s\""
        fields["XINCR"] = formatEng(generated.xIncrement)
        fields["XZERO"] = formatEng(generated.xZero)
        fields["PT_OFF"] = generated.pointOffset.toString()
        fields["YUNIT"] = "\"V\""
        fields["YMULT"] = formatEng(generated.yMultiplier)
        fields["YOFF"] = formatEng(generated.yOffset)
        fields["YZERO"] = formatEng(generated.yZero)
        return fields
    }

    /** DATa:SOUrce が指す波形が表示されているか。 */
    private fun isSourceDisplayed(): Boolean {
        val source = settings["DATA:SOURCE"] ?: "CH1"
        return source.uppercase(Locale.US) in displayedSources.map { it.uppercase(Locale.US) }
    }

    private fun buildCurve(): SimulatedResponse {
        val source = settings["DATA:SOURCE"] ?: "CH1"
        if (!isSourceDisplayed()) {
            pushEvent(EVENT_EXECUTION_ERROR, "Source waveform is not turned on; $source")
            return SimulatedResponse.None
        }

        val bytesPerPoint = settings["DATA:WIDTH"]?.toIntOrNull() ?: 1
        val encoding = settings["DATA:ENCDG"] ?: "RIBINARY"
        val pointCount = effectivePointCount()
        val generated = WaveformFactory.generate(
            shape = config.waveformShape,
            pointCount = pointCount,
            bytesPerPoint = bytesPerPoint,
            signed = isSignedEncoding(encoding),
        )

        if (encoding.startsWith("ASC")) {
            return SimulatedResponse.Text(generated.raw.joinToString(","))
        }

        val payload = Ieee4882Encoder.packIntegers(
            values = generated.raw,
            bytesPerPoint = bytesPerPoint,
            bigEndian = byteOrder(encoding) == "MSB",
        )
        val declared = when (config.faultMode) {
            FaultMode.BAD_BLOCK_LENGTH -> payload.size.toLong() + BAD_LENGTH_DELTA
            FaultMode.HUGE_BLOCK_LENGTH -> HUGE_DECLARED_LENGTH
            else -> null
        }
        return SimulatedResponse.Binary(Ieee4882Encoder.encode(payload, declared))
    }

    private fun effectivePointCount(): Int {
        val start = settings["DATA:START"]?.toIntOrNull() ?: 1
        val stop = settings["DATA:STOP"]?.toIntOrNull() ?: DEFAULT_RECORD_LENGTH
        return (stop - start + 1).coerceIn(1, MAX_POINT_COUNT)
    }

    private fun isSignedEncoding(encoding: String): Boolean = !encoding.startsWith("RP") && !encoding.startsWith("SRP")

    private fun binaryFormat(encoding: String): String = when {
        encoding.startsWith("RP") || encoding.startsWith("SRP") -> "RP"
        encoding.startsWith("FP") || encoding.startsWith("SFP") -> "FP"
        else -> "RI"
    }

    /** `SRIbinary` / `SRPbinary` / `SFPbinary` はバイト順が入れ替わる（LSB 先行）。 */
    private fun byteOrder(encoding: String): String = if (encoding.startsWith("S")) "LSB" else "MSB"

    private fun textWithHeader(head: String, value: String): SimulatedResponse = if (headerEnabled) {
        SimulatedResponse.Text(":${head.uppercase(Locale.US)} $value")
    } else {
        SimulatedResponse.Text(value)
    }

    private fun drainAllEvents(): String {
        if (events.isEmpty()) return "0,\"No events to report - queue empty\""
        val all = events.joinToString(",") { "${it.code},\"${it.message}\"" }
        events.clear()
        return all
    }

    private fun pushEvent(code: Int, message: String) {
        if (events.size >= MAX_EVENT_QUEUE) events.pollFirst()
        events.addLast(SimulatedEvent(code, message))
        standardEventStatus = standardEventStatus or when (code) {
            EVENT_UNDEFINED_HEADER -> ESR_COMMAND_ERROR
            EVENT_EXECUTION_ERROR -> ESR_EXECUTION_ERROR
            else -> ESR_DEVICE_ERROR
        }
    }

    private fun normalize(head: String): String = head.uppercase(Locale.US)

    private fun boolValue(value: Boolean): String = if (value) "1" else "0"

    private fun formatEng(value: Double): String = String.format(Locale.US, "%.4E", value)

    /** テストから状態を確認するための読み出し。 */
    fun setting(key: String): String? = settings[normalize(key)]

    fun pendingEventCount(): Int = events.size

    companion object {
        const val EVENT_UNDEFINED_HEADER = 113
        const val EVENT_EXECUTION_ERROR = 2200

        private const val ESR_COMMAND_ERROR = 0x20
        private const val ESR_EXECUTION_ERROR = 0x10
        private const val ESR_DEVICE_ERROR = 0x08

        private const val BITS_PER_BYTE = 8
        private const val DEFAULT_RECORD_LENGTH = 10_000
        private const val MAX_POINT_COUNT = 1_000_000
        private const val MAX_EVENT_QUEUE = 32

        /** 波形が非表示のときに返る転送パラメータの数 (BYT_NR, BIT_NR, ENCDG, BN_FMT, BYT_OR)。 */
        private const val TRANSMISSION_FIELD_COUNT = 5

        /** 同時測定数。CONFIGuration:NUMMEAS? の応答と一致させる。 */
        private const val MEASUREMENT_SLOTS = 4
        private const val DEFAULT_HORIZONTAL_SCALE = 4.0e-6
        private const val DEFAULT_VERTICAL_SCALE = 0.1
        private const val BAD_LENGTH_DELTA = 128L

        /** 桁数は 1 文字なので宣言長は最大 9 桁。上限チェックを試すため 9 桁の最大級を使う。 */
        private const val HUGE_DECLARED_LENGTH = 900_000_000L
    }
}
