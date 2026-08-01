package com.pdtoscillo.simulator

/**
 * 疑似オシロスコープの動作設定。
 *
 * 実機が無い状態で開発とテストを進めるための機能に加え、
 * 実機で起こり得る異常（応答の分割、遅延、途中切断、不正なブロック長）を意図的に再現する。
 */
data class SimulatorConfig(
    val port: Int = DEFAULT_PORT,
    /** `*IDN?` で返すモデル。世代ごとの挙動差を再現するために使う。 */
    val model: SimulatedModel = SimulatedModel.MDO4104C,
    val faultMode: FaultMode = FaultMode.NONE,
    /** 応答を分割送信するときの 1 回あたりのバイト数。 */
    val chunkSize: Int = DEFAULT_CHUNK_SIZE,
    /** 分割送信の間に挟む待ち時間。TCP のパケット境界と応答境界をずらす。 */
    val chunkDelayMillis: Long = DEFAULT_CHUNK_DELAY_MILLIS,
    /** `DELAYED_RESPONSE` での応答遅延。 */
    val responseDelayMillis: Long = DEFAULT_RESPONSE_DELAY_MILLIS,
    /** 生成する波形の種類。 */
    val waveformShape: WaveformShape = WaveformShape.SINE,
    /** `Protocol: Terminal` を模してエコーとプロンプトを返す。 */
    val terminalMode: Boolean = false,
    val verbose: Boolean = false,
) {
    companion object {
        const val DEFAULT_PORT: Int = 4000
        const val DEFAULT_CHUNK_SIZE: Int = 7
        const val DEFAULT_CHUNK_DELAY_MILLIS: Long = 5
        const val DEFAULT_RESPONSE_DELAY_MILLIS: Long = 2_000
    }
}

/**
 * 再現する障害。
 *
 * TCP の 1 回の read で応答が揃うと仮定した実装は `SPLIT_RESPONSE` で必ず壊れる。
 * この状態でテストを通すことを要件にしている。
 */
enum class FaultMode {
    /** 正常応答。 */
    NONE,

    /** 応答を細かく分割して送る。TCP パケット境界 ≠ SCPI 応答境界を再現する。 */
    SPLIT_RESPONSE,

    /** 応答を遅らせる。タイムアウト設定の検証に使う。 */
    DELAYED_RESPONSE,

    /** 応答を返さない。読み取りタイムアウトを発生させる。 */
    NO_RESPONSE,

    /** 応答の途中で接続を切る。 */
    DISCONNECT_MIDWAY,

    /** IEEE 488.2 ブロックのヘッダ長と実データ長を食い違わせる。 */
    BAD_BLOCK_LENGTH,

    /** ブロック長を極端に大きく宣言する。上限チェックの検証に使う。 */
    HUGE_BLOCK_LENGTH,

    /** すべてのコマンドを未定義として扱う。 */
    UNSUPPORTED_COMMAND,

    /** `BUSY?` が 1 を返し、設定変更を受け付けない。 */
    BUSY,
}

enum class WaveformShape { SINE, SQUARE, NOISE, PULSE }

/**
 * 疑似モデル。
 *
 * `supportsConfigurationQueries = false` の世代を用意することで、
 * `CONFIGuration:*?` が使えない機種でのフォールバック経路をテストできる。
 */
enum class SimulatedModel(
    val idnModel: String,
    val serialNumber: String,
    val firmware: String,
    val analogChannels: Int,
    val digitalChannels: Int,
    val hasRf: Boolean,
    val hasAfg: Boolean,
    val hasDvm: Boolean,
    val supportsConfigurationQueries: Boolean,
) {
    /** 無印世代。`CONFIGuration:*?` を持たない想定。 */
    DPO4054(
        idnModel = "DPO4054",
        serialNumber = "C010001",
        firmware = "CF:91.1CT FV:v2.16",
        analogChannels = 4,
        digitalChannels = 0,
        hasRf = false,
        hasAfg = false,
        hasDvm = false,
        supportsConfigurationQueries = false,
    ),

    /**
     * 実機（2026-07-31 に接続確認）と同じモデル。無印 Gen1・4ch・350 MHz。
     *
     * オシロ無しでネットワーク/バインド層を検証する際の代役に使う。Gen1 なので
     * `CONFIGuration:*?` を持たず、機能検出はモデル名フォールバックになる想定。
     * ファームウェア文字列は暫定（実機の `*IDN?` を取得したら docs/hardware-validation.md
     * の値へ合わせること）。
     */
    DPO4034(
        idnModel = "DPO4034",
        serialNumber = "C012345",
        firmware = "CF:91.1CT FV:v2.48",
        analogChannels = 4,
        digitalChannels = 0,
        hasRf = false,
        hasAfg = false,
        hasDvm = false,
        supportsConfigurationQueries = false,
    ),

    /** 無印世代の MSO。デジタル 16 ch。 */
    MSO4104(
        idnModel = "MSO4104",
        serialNumber = "C010002",
        firmware = "CF:91.1CT FV:v2.16",
        analogChannels = 4,
        digitalChannels = 16,
        hasRf = false,
        hasAfg = false,
        hasDvm = false,
        supportsConfigurationQueries = false,
    ),

    /** B 世代。`CONFIGuration:*?` に対応。 */
    MSO4104B(
        idnModel = "MSO4104B",
        serialNumber = "C020001",
        firmware = "CF:91.1CT FV:v2.68",
        analogChannels = 4,
        digitalChannels = 16,
        hasRf = false,
        hasAfg = true,
        hasDvm = true,
        supportsConfigurationQueries = true,
    ),

    /** MDO。RF 搭載。 */
    MDO4104C(
        idnModel = "MDO4104C",
        serialNumber = "C030001",
        firmware = "CF:91.1CT FV:v1.28",
        analogChannels = 4,
        digitalChannels = 16,
        hasRf = true,
        hasAfg = true,
        hasDvm = true,
        supportsConfigurationQueries = true,
    ),

    /** 2 ch 構成。チャンネル数推定の検証に使う。 */
    DPO4032(
        idnModel = "DPO4032",
        serialNumber = "C010003",
        firmware = "CF:91.1CT FV:v2.16",
        analogChannels = 2,
        digitalChannels = 0,
        hasRf = false,
        hasAfg = false,
        hasDvm = false,
        supportsConfigurationQueries = false,
    ),
    ;

    val idnResponse: String get() = "TEKTRONIX,$idnModel,$serialNumber,$firmware"

    companion object {
        fun fromName(name: String): SimulatedModel? =
            entries.firstOrNull { it.idnModel.equals(name, ignoreCase = true) || it.name.equals(name, ignoreCase = true) }
    }
}
