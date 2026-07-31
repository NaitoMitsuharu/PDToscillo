package com.pdtoscillo.core.scpi

/**
 * Tektronix 4000 シリーズの SCPI コマンド定義。
 *
 * **このファイルがコマンド文字列の唯一の置き場所である。** feature 側でコマンドを直書きしない。
 *
 * ここに書かれているコマンドはすべて、公式 Programmer Manual
 * （MDO4000C/B, MDO4000, MSO4000B, DPO4000B, MDO3000 Series Programmer Manual 077-0510-07）
 * で存在と綴りを確認したものだけである。推測で追加してはならない。
 *
 * マニュアル表記では大文字部分が最短形（例 `HORizontal` → `HOR`）。ここでは可読性のため
 * マニュアルと同じ混在表記のまま送信する。計測器は大文字小文字を区別しない。
 */
object TektronixCommands {
    /** IEEE 488.2 共通コマンドと状態確認。 */
    object Common {
        const val IDENTIFY = "*IDN?"
        const val IDENTIFY_SHORT = "ID?"
        const val OPERATION_COMPLETE_QUERY = "*OPC?"
        const val OPERATION_COMPLETE_SET = "*OPC"
        const val EVENT_STATUS_REGISTER = "*ESR?"
        const val CLEAR_STATUS = "*CLS"
        const val RESET = "*RST"
        const val BUSY = "BUSY?"
        const val EVENT_MESSAGE = "EVMsg?"
        const val ALL_EVENTS = "ALLEv?"
        const val EVENT_CODE = "EVENT?"
        const val EVENT_QUANTITY = "EVQty?"

        /** 応答へコマンドパスを含めるかどうか。既定値は機種設定に依存する。 */
        const val HEADER = "HEADer"
        const val VERBOSE = "VERBose"
    }

    /**
     * Configuration グループ。
     *
     * **本体設定を変更せずに**機能の有無を問い合わせられるため、Capability 検出の主経路にする。
     * 無印世代（DPO4000 / MSO4000）には存在しない可能性があり、その場合は未定義ヘッダーが返る。
     */
    object Configuration {
        const val ANALOG_CHANNEL_COUNT = "CONFIGuration:ANALOg:NUMCHANnels?"
        const val ANALOG_BANDWIDTH = "CONFIGuration:ANALOg:BANDWidth?"
        const val ANALOG_MAX_BANDWIDTH = "CONFIGuration:ANALOg:MAXBANDWidth?"
        const val ANALOG_MAX_SAMPLE_RATE = "CONFIGuration:ANALOg:MAXSAMPLERate?"
        const val ANALOG_RECORD_LENGTHS = "CONFIGuration:ANALOg:RECLENS?"
        const val ANALOG_VERTICAL_INVERT = "CONFIGuration:ANALOg:VERTINVert?"
        const val DIGITAL_CHANNEL_COUNT = "CONFIGuration:DIGITAl:NUMCHANnels?"
        const val DIGITAL_MAX_SAMPLE_RATE = "CONFIGuration:DIGITAl:MAXSAMPLERate?"
        const val DIGITAL_MAGNIVU = "CONFIGuration:DIGITAl:MAGnivu?"
        const val RF_CHANNEL_COUNT = "CONFIGuration:RF:NUMCHANnels?"
        const val RF_BANDWIDTH = "CONFIGuration:RF:BANDWidth?"
        const val RF_MAX_BANDWIDTH = "CONFIGuration:RF:MAXBANDWidth?"
        const val RF_ADVANCED_TRIGGER = "CONFIGuration:RF:ADVTRIG?"
        const val AFG = "CONFIGuration:AFG?"
        const val ARBITRARY = "CONFIGuration:ARB?"
        const val DVM = "CONFIGuration:DVM?"
        const val ADVANCED_MATH = "CONFIGuration:ADVMATH?"
        const val HISTOGRAM = "CONFIGuration:HISTOGRAM?"
        const val EXTENDED_VIDEO = "CONFIGuration:EXTVIDEO?"
        const val AUX_INPUT = "CONFIGuration:AUXIN?"
        const val REFERENCE_OSCILLATOR = "CONFIGuration:ROSC?"
        const val MEASUREMENT_COUNT = "CONFIGuration:NUMMEAS?"
        const val REFERENCE_COUNT = "CONFIGuration:REFS:NUMREFS?"
        const val BUS_COUNT = "CONFIGuration:BUSWAVEFORMS:NUMBUS?"
    }

    /** Acquisition グループ。 */
    object Acquisition {
        const val STATE = "ACQuire:STATE"
        const val STATE_QUERY = "ACQuire:STATE?"
        const val STOP_AFTER = "ACQuire:STOPAfter"
        const val STOP_AFTER_QUERY = "ACQuire:STOPAfter?"
        const val MODE = "ACQuire:MODe"
        const val MODE_QUERY = "ACQuire:MODe?"
        const val NUM_AVERAGE = "ACQuire:NUMAVg"
        const val NUM_AVERAGE_QUERY = "ACQuire:NUMAVg?"
        const val NUM_ACQUISITIONS_QUERY = "ACQuire:NUMACq?"
        const val FAST_ACQUISITION_STATE = "ACQuire:FASTAcq:STATE"
        const val FAST_ACQUISITION_STATE_QUERY = "ACQuire:FASTAcq:STATE?"

        fun run(): String = "$STATE RUN"

        fun stop(): String = "$STATE STOP"

        /** 単発取得。`STOPAfter SEQuence` にしてから `STATE RUN`。 */
        fun singleSequence(): List<String> = listOf("$STOP_AFTER SEQuence", "$STATE RUN")

        fun continuous(): List<String> = listOf("$STOP_AFTER RUNSTop", "$STATE RUN")
    }

    /**
     * Autoset。マニュアル記載の構文は `AUTOSet {EXECute|UNDo}`。
     *
     * 本体の設定を大きく変えるため [ScpiDangerClassifier] で危険操作として扱う。
     */
    object Autoset {
        const val AUTOSET = "AUTOSet"

        fun execute(): String = "$AUTOSET EXECute"

        fun undo(): String = "$AUTOSET UNDo"
    }

    /** Horizontal グループ。 */
    object Horizontal {
        const val SCALE = "HORizontal:SCAle"
        const val SCALE_QUERY = "HORizontal:SCAle?"
        const val POSITION = "HORizontal:POSition"
        const val POSITION_QUERY = "HORizontal:POSition?"
        const val RECORD_LENGTH = "HORizontal:RECOrdlength"
        const val RECORD_LENGTH_QUERY = "HORizontal:RECOrdlength?"

        /** 問い合わせ専用。 */
        const val SAMPLE_RATE_QUERY = "HORizontal:SAMPLERate?"
        const val DELAY_MODE = "HORizontal:DELay:MODe"
        const val DELAY_TIME = "HORizontal:DELay:TIMe"
    }

    /** Vertical グループ（チャンネル設定）。`<x>` は 1〜4。 */
    object Vertical {
        fun display(channel: Int): String = "SELect:CH$channel"

        fun displayQuery(channel: Int): String = "SELect:CH$channel?"

        fun scale(channel: Int): String = "CH$channel:SCAle"

        fun scaleQuery(channel: Int): String = "CH$channel:SCAle?"

        fun position(channel: Int): String = "CH$channel:POSition"

        fun positionQuery(channel: Int): String = "CH$channel:POSition?"

        fun offset(channel: Int): String = "CH$channel:OFFSet"

        fun offsetQuery(channel: Int): String = "CH$channel:OFFSet?"

        fun coupling(channel: Int): String = "CH$channel:COUPling"

        fun couplingQuery(channel: Int): String = "CH$channel:COUPling?"

        fun bandwidth(channel: Int): String = "CH$channel:BANdwidth"

        fun bandwidthQuery(channel: Int): String = "CH$channel:BANdwidth?"

        fun invert(channel: Int): String = "CH$channel:INVert"

        fun invertQuery(channel: Int): String = "CH$channel:INVert?"

        fun label(channel: Int): String = "CH$channel:LABel"

        fun labelQuery(channel: Int): String = "CH$channel:LABel?"

        fun termination(channel: Int): String = "CH$channel:TERmination"

        fun terminationQuery(channel: Int): String = "CH$channel:TERmination?"

        fun deskew(channel: Int): String = "CH$channel:DESKew"

        fun deskewQuery(channel: Int): String = "CH$channel:DESKew?"

        fun probeGain(channel: Int): String = "CH$channel:PRObe:GAIN"

        fun probeGainQuery(channel: Int): String = "CH$channel:PRObe:GAIN?"
    }

    /** Digital グループ。`D<x>` は D0〜D15。綴りは `THReshold`（`THRESHold` ではない）。 */
    object Digital {
        fun threshold(bit: Int): String = "D$bit:THReshold"

        fun thresholdQuery(bit: Int): String = "D$bit:THReshold?"

        fun label(bit: Int): String = "D$bit:LABel"

        fun labelQuery(bit: Int): String = "D$bit:LABel?"

        fun position(bit: Int): String = "D$bit:POSition"

        fun positionQuery(bit: Int): String = "D$bit:POSition?"

        fun display(bit: Int): String = "SELect:D$bit"

        fun displayQuery(bit: Int): String = "SELect:D$bit?"
    }

    /** Trigger グループ。 */
    object Trigger {
        const val TYPE = "TRIGger:A:TYPe"
        const val TYPE_QUERY = "TRIGger:A:TYPe?"
        const val MODE = "TRIGger:A:MODe"
        const val MODE_QUERY = "TRIGger:A:MODe?"
        const val LOGIC_CLASS = "TRIGger:A:LOGIc:CLAss"
        const val LOGIC_CLASS_QUERY = "TRIGger:A:LOGIc:CLAss?"
        const val PULSE_CLASS = "TRIGger:A:PULse:CLAss"
        const val PULSE_CLASS_QUERY = "TRIGger:A:PULse:CLAss?"
        const val EDGE_SOURCE = "TRIGger:A:EDGE:SOUrce"
        const val EDGE_SOURCE_QUERY = "TRIGger:A:EDGE:SOUrce?"
        const val EDGE_SLOPE = "TRIGger:A:EDGE:SLOpe"
        const val EDGE_SLOPE_QUERY = "TRIGger:A:EDGE:SLOpe?"
        const val EDGE_COUPLING = "TRIGger:A:EDGE:COUPling"
        const val EDGE_COUPLING_QUERY = "TRIGger:A:EDGE:COUPling?"
        const val HOLDOFF_TIME = "TRIGger:A:HOLDoff:TIMe"
        const val HOLDOFF_TIME_QUERY = "TRIGger:A:HOLDoff:TIMe?"
        const val BUS_TYPE = "TRIGger:A:BUS"
        const val BUS_TYPE_QUERY = "TRIGger:A:BUS?"
        const val BUS_SOURCE = "TRIGger:A:BUS:SOUrce"

        /** 問い合わせ専用。`TRIGGER` / `SAVE` / `READY` / `ARMED` / `AUTO` を返す。 */
        const val STATE_QUERY = "TRIGger:STATE?"

        /** トリガを強制する。 */
        const val FORCE = "TRIGger FORCe"

        /** トリガレベルを振幅の 50% へ合わせる。 */
        const val SET_LEVEL_50_PERCENT = "TRIGger:A SETLevel"

        /** トリガレベルはソースごとに指定する。 */
        fun levelForChannel(channel: Int): String = "TRIGger:A:LEVel:CH$channel"

        fun levelForChannelQuery(channel: Int): String = "TRIGger:A:LEVel:CH$channel?"

        fun levelForDigital(bit: Int): String = "TRIGger:A:LEVel:D$bit"

        const val LEVEL_AUX = "TRIGger:A:LEVel:AUXin"

        /** パルス幅トリガ。 */
        const val PULSE_WIDTH_WHEN = "TRIGger:A:PULSEWidth:WHEn"
        const val PULSE_WIDTH_LOW_LIMIT = "TRIGger:A:PULSEWidth:LOWLimit"
        const val PULSE_WIDTH_HIGH_LIMIT = "TRIGger:A:PULSEWidth:HIGHLimit"
        const val PULSE_WIDTH_SOURCE = "TRIGger:A:PULSEWidth:SOUrce"
        const val PULSE_WIDTH_POLARITY = "TRIGger:A:PULSEWidth:POLarity"

        /** ラント。 */
        const val RUNT_SOURCE = "TRIGger:A:RUNT:SOUrce"
        const val RUNT_WHEN = "TRIGger:A:RUNT:WHEn"
        const val RUNT_WIDTH = "TRIGger:A:RUNT:WIDth"
        const val RUNT_POLARITY = "TRIGger:A:RUNT:POLarity"

        /** タイムアウト。 */
        const val TIMEOUT_SOURCE = "TRIGger:A:TIMEOut:SOUrce"
        const val TIMEOUT_TIME = "TRIGger:A:TIMEOut:TIMe"
        const val TIMEOUT_POLARITY = "TRIGger:A:TIMEOut:POLarity"

        /** ビデオ。 */
        const val VIDEO_SOURCE = "TRIGger:A:VIDeo:SOUrce"
        const val VIDEO_STANDARD = "TRIGger:A:VIDeo:STANdard"
        const val VIDEO_SYNC = "TRIGger:A:VIDeo:SYNC"
        const val VIDEO_POLARITY = "TRIGger:A:VIDeo:POLarity"

        /** B トリガ（シーケンストリガ）。 */
        const val B_STATE = "TRIGger:B:STATE"
        const val B_STATE_QUERY = "TRIGger:B:STATE?"
        const val B_TYPE = "TRIGger:B:TYPe"
    }

    /** Measurement グループ。`<x>` は 1 から `CONFIGuration:NUMMEAS?` の値まで。 */
    object Measurement {
        const val STATISTICS_MODE = "MEASUrement:STATIstics:MODe"
        const val STATISTICS_WEIGHTING = "MEASUrement:STATIstics:WEIghting"

        fun type(slot: Int): String = "MEASUrement:MEAS$slot:TYPe"

        fun typeQuery(slot: Int): String = "MEASUrement:MEAS$slot:TYPe?"

        fun state(slot: Int): String = "MEASUrement:MEAS$slot:STATE"

        fun stateQuery(slot: Int): String = "MEASUrement:MEAS$slot:STATE?"

        fun source(slot: Int, sourceIndex: Int = 1): String = "MEASUrement:MEAS$slot:SOUrce$sourceIndex"

        fun sourceQuery(slot: Int, sourceIndex: Int = 1): String = "MEASUrement:MEAS$slot:SOUrce$sourceIndex?"

        fun valueQuery(slot: Int): String = "MEASUrement:MEAS$slot:VALue?"

        fun meanQuery(slot: Int): String = "MEASUrement:MEAS$slot:MEAN?"

        fun minimumQuery(slot: Int): String = "MEASUrement:MEAS$slot:MINImum?"

        fun maximumQuery(slot: Int): String = "MEASUrement:MEAS$slot:MAXimum?"

        fun standardDeviationQuery(slot: Int): String = "MEASUrement:MEAS$slot:STDdev?"

        fun countQuery(slot: Int): String = "MEASUrement:MEAS$slot:COUNt?"

        fun unitsQuery(slot: Int): String = "MEASUrement:MEAS$slot:UNIts?"

        /** 即時測定（画面へ表示せず 1 回だけ測る）。 */
        const val IMMEDIATE_TYPE = "MEASUrement:IMMed:TYPe"
        const val IMMEDIATE_VALUE_QUERY = "MEASUrement:IMMed:VALue?"
        const val IMMEDIATE_UNITS_QUERY = "MEASUrement:IMMed:UNIts?"

        fun immediateSource(sourceIndex: Int = 1): String = "MEASUrement:IMMed:SOUrce$sourceIndex"
    }

    /** Cursor グループ。 */
    object Cursor {
        const val FUNCTION = "CURSor:FUNCtion"
        const val FUNCTION_QUERY = "CURSor:FUNCtion?"
        const val MODE = "CURSor:MODe"
        const val MODE_QUERY = "CURSor:MODe?"
        const val SOURCE = "CURSor:SOUrce"
        const val SOURCE_QUERY = "CURSor:SOUrce?"
        const val VBARS_DELTA_QUERY = "CURSor:VBArs:DELTa?"
        const val HBARS_DELTA_QUERY = "CURSor:HBArs:DELTa?"
        const val VBARS_UNITS = "CURSor:VBArs:UNIts"
        const val HBARS_UNITS = "CURSor:HBArs:UNIts"

        fun verticalBarPosition(index: Int): String = "CURSor:VBArs:POSITION$index"

        fun verticalBarPositionQuery(index: Int): String = "CURSor:VBArs:POSITION$index?"

        fun horizontalBarPosition(index: Int): String = "CURSor:HBArs:POSITION$index"

        fun horizontalBarPositionQuery(index: Int): String = "CURSor:HBArs:POSITION$index?"
    }

    /** Waveform Transfer グループ。 */
    object Waveform {
        const val DATA_SOURCE = "DATa:SOUrce"
        const val DATA_SOURCE_QUERY = "DATa:SOUrce?"
        const val DATA_START = "DATa:STARt"
        const val DATA_STOP = "DATa:STOP"
        const val DATA_ENCODING = "DATa:ENCdg"
        const val DATA_ENCODING_QUERY = "DATa:ENCdg?"
        const val DATA_WIDTH = "DATa:WIDth"
        const val DATA_WIDTH_QUERY = "DATa:WIDth?"

        /** プリアンブル一括取得。 */
        const val PREAMBLE_QUERY = "WFMOutpre?"
        const val CURVE_QUERY = "CURVe?"

        /** 個別フィールド。一括取得の解析に失敗した場合の予備。 */
        const val BYTES_PER_POINT_QUERY = "WFMOutpre:BYT_Nr?"
        const val BITS_PER_POINT_QUERY = "WFMOutpre:BIT_Nr?"
        const val ENCODING_QUERY = "WFMOutpre:ENCdg?"
        const val BINARY_FORMAT_QUERY = "WFMOutpre:BN_Fmt?"
        const val BYTE_ORDER_QUERY = "WFMOutpre:BYT_Or?"
        const val POINT_COUNT_QUERY = "WFMOutpre:NR_Pt?"
        const val X_INCREMENT_QUERY = "WFMOutpre:XINcr?"
        const val X_ZERO_QUERY = "WFMOutpre:XZEro?"
        const val POINT_OFFSET_QUERY = "WFMOutpre:PT_Off?"
        const val Y_MULTIPLIER_QUERY = "WFMOutpre:YMUlt?"
        const val Y_OFFSET_QUERY = "WFMOutpre:YOFf?"
        const val Y_ZERO_QUERY = "WFMOutpre:YZEro?"
        const val X_UNIT_QUERY = "WFMOutpre:XUNit?"
        const val Y_UNIT_QUERY = "WFMOutpre:YUNit?"

        /**
         * `DATa:ENCdg` の有効値。
         *
         * マニュアル記載どおり `RIBinary` が既定。`ASCIi` は最大 100 万点までで、
         * それを超える場合はバイナリが必須。
         */
        object Encoding {
            const val ASCII = "ASCIi"
            const val FASTEST = "FAStest"
            const val SIGNED_BIG_ENDIAN = "RIBinary"
            const val UNSIGNED_BIG_ENDIAN = "RPBinary"
            const val SIGNED_LITTLE_ENDIAN = "SRIbinary"
            const val UNSIGNED_LITTLE_ENDIAN = "SRPbinary"
            const val FLOAT_BIG_ENDIAN = "FPbinary"
            const val FLOAT_LITTLE_ENDIAN = "SFPbinary"
        }
    }

    /** Math グループ。マニュアル表記は `MATH[1]` で、`1` は省略できる。 */
    object Math {
        const val DEFINE = "MATH1:DEFine"
        const val DEFINE_QUERY = "MATH1:DEFine?"
        const val TYPE = "MATH1:TYPe"
        const val TYPE_QUERY = "MATH1:TYPe?"
        const val LABEL = "MATH1:LABel"
        const val AUTOSCALE = "MATH1:AUTOSCale"
        const val SPECTRAL_MAGNITUDE = "MATH1:SPECTral:MAG"
        const val SPECTRAL_WINDOW = "MATH1:SPECTral:WINdow"
        const val HORIZONTAL_SCALE = "MATH1:HORizontal:SCAle"
        const val HORIZONTAL_POSITION = "MATH1:HORizontal:POSition"
        const val HORIZONTAL_UNITS_QUERY = "MATH1:HORizontal:UNIts?"
        const val VERTICAL_SCALE = "MATH1:VERTical:SCAle"
        const val VERTICAL_POSITION = "MATH1:VERTical:POSition"
    }

    /** Reference 波形。 */
    object Reference {
        fun label(index: Int): String = "REF$index:LABel"

        fun labelQuery(index: Int): String = "REF$index:LABel?"

        fun display(index: Int): String = "SELect:REF$index"

        fun displayQuery(index: Int): String = "SELect:REF$index?"

        fun verticalScale(index: Int): String = "REF$index:VERTical:SCAle"

        fun verticalPosition(index: Int): String = "REF$index:VERTical:POSition"

        /** チャンネル波形を Reference へ保存する。 */
        fun saveFromChannel(channel: Int, referenceIndex: Int): String = "SAVe:WAVEform CH$channel,REF$referenceIndex"
    }

    /** Bus グループ。 */
    object Bus {
        fun type(bus: Int): String = "BUS:B$bus:TYPe"

        fun typeQuery(bus: Int): String = "BUS:B$bus:TYPe?"

        fun state(bus: Int): String = "BUS:B$bus:STATE"

        fun stateQuery(bus: Int): String = "BUS:B$bus:STATE?"

        fun displayType(bus: Int): String = "BUS:B$bus:DISplay:TYPe"

        fun label(bus: Int): String = "BUS:B$bus:LABel"

        fun i2cClockSource(bus: Int): String = "BUS:B$bus:I2C:CLOCk:SOUrce"

        fun i2cDataSource(bus: Int): String = "BUS:B$bus:I2C:DATa:SOUrce"

        fun spiClockSource(bus: Int): String = "BUS:B$bus:SPI:CLOCk:SOUrce"

        fun rs232TxSource(bus: Int): String = "BUS:B$bus:RS232C:TX:SOUrce"

        fun rs232BaudRate(bus: Int): String = "BUS:B$bus:RS232C:BAUDRate"

        fun canSource(bus: Int): String = "BUS:B$bus:CAN:SOUrce"

        fun canBitRate(bus: Int): String = "BUS:B$bus:CAN:BITRate"

        fun linSource(bus: Int): String = "BUS:B$bus:LIN:SOUrce"

        fun parallelClockSource(bus: Int): String = "BUS:B$bus:PARallel:CLOCk:SOUrce"
    }

    /** RF グループ（MDO 系のみ）。 */
    object Rf {
        /** 中心周波数。 */
        const val CENTER_FREQUENCY = "RF:FREQuency"
        const val CENTER_FREQUENCY_QUERY = "RF:FREQuency?"
        const val SPAN = "RF:SPAN"
        const val SPAN_QUERY = "RF:SPAN?"
        const val START_FREQUENCY = "RF:STARt"
        const val START_FREQUENCY_QUERY = "RF:STARt?"
        const val STOP_FREQUENCY = "RF:STOP"
        const val STOP_FREQUENCY_QUERY = "RF:STOP?"
        const val RESOLUTION_BANDWIDTH = "RF:RBW"
        const val RESOLUTION_BANDWIDTH_QUERY = "RF:RBW?"
        const val RESOLUTION_BANDWIDTH_MODE = "RF:RBW:MODe"
        const val REFERENCE_LEVEL = "RF:REFLevel"
        const val REFERENCE_LEVEL_QUERY = "RF:REFLevel?"
        const val WINDOW = "RF:WINdow"
        const val WINDOW_QUERY = "RF:WINdow?"
        const val SPECTROGRAM_STATE = "RF:SPECTRogram:STATE"
        const val SPECTRUM_UNITS = "RF:UNIts"
        const val DETECTION_METHOD = "RF:DETECTionmethod"
    }

    /** AFG（オプション搭載機のみ）。出力の有効化は確認を必須にする。 */
    object Afg {
        const val FUNCTION = "AFG:FUNCtion"
        const val FUNCTION_QUERY = "AFG:FUNCtion?"
        const val FREQUENCY = "AFG:FREQuency"
        const val FREQUENCY_QUERY = "AFG:FREQuency?"
        const val AMPLITUDE = "AFG:AMPLitude"
        const val AMPLITUDE_QUERY = "AFG:AMPLitude?"
        const val OFFSET = "AFG:OFFSet"
        const val OFFSET_QUERY = "AFG:OFFSet?"
        const val SQUARE_DUTY = "AFG:SQUare:DUty"
        const val SQUARE_DUTY_QUERY = "AFG:SQUare:DUty?"
        const val OUTPUT_STATE = "AFG:OUTPut:STATE"
        const val OUTPUT_STATE_QUERY = "AFG:OUTPut:STATE?"
        const val OUTPUT_LOAD_IMPEDANCE = "AFG:OUTPut:LOAd:IMPEDance"
        const val ARBITRARY_SOURCE = "AFG:ARBitrary:SOUrce"
    }

    /** DVM（オプション搭載機のみ）。 */
    object Dvm {
        const val MODE = "DVM:MODe"
        const val MODE_QUERY = "DVM:MODe?"
        const val SOURCE = "DVM:SOUrce"
        const val SOURCE_QUERY = "DVM:SOUrce?"
        const val VALUE_QUERY = "DVM:MEASUrement:VALue?"
        const val FREQUENCY_QUERY = "DVM:MEASUrement:FREQuency?"
        const val AUTO_RANGE = "DVM:AUTORange"
        const val DISPLAY_STYLE = "DVM:DISPLAYSTYle"
        const val HISTORY_MINIMUM_QUERY = "DVM:MEASUrement:INFMINimum?"
        const val HISTORY_MAXIMUM_QUERY = "DVM:MEASUrement:INFMAXimum?"
    }

    /** Save and Recall / File System / Hard Copy。 */
    object Files {
        const val SAVE_SETUP = "SAVe:SETUp"
        const val RECALL_SETUP = "RECAll:SETUp"
        const val SAVE_IMAGE = "SAVe:IMAGe"
        const val SAVE_IMAGE_FILE_FORMAT = "SAVe:IMAGe:FILEFormat"
        const val SAVE_WAVEFORM = "SAVe:WAVEform"
        const val SAVE_WAVEFORM_FILE_FORMAT = "SAVe:WAVEform:FILEFormat"
        const val RECALL_WAVEFORM = "RECAll:WAVEform"
        const val DIRECTORY_QUERY = "FILESystem:DIR?"
        const val DELETE = "FILESystem:DELEte"
        const val READ_FILE = "FILESystem:READFile"
        const val CURRENT_WORKING_DIRECTORY = "FILESystem:CWD"
        const val RENAME = "FILESystem:REName"
        const val MAKE_DIRECTORY = "FILESystem:MKDir"
        const val DELETE_WARNING = "FILESystem:DELWarn"
        const val FREE_SPACE_QUERY = "FILESystem:FREESpace?"

        /** 画面イメージの取得。`HARDCopy STARt` の後に読み出す。 */
        const val HARDCOPY_START = "HARDCopy STARt"
        const val HARDCOPY_INK_SAVER = "HARDCopy:INKSaver"
    }

    /** Display グループ。 */
    object Display {
        const val PERSISTENCE = "DISplay:PERSistence"
        const val GRATICULE = "DISplay:GRAticule"
        const val INTENSITY_WAVEFORM = "DISplay:INTENSITy:WAVEform"
    }
}
