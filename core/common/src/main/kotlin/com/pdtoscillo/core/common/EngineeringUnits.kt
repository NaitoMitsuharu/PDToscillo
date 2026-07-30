package com.pdtoscillo.core.common

import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * 工学表記（SI 接頭辞）による数値の整形と解釈。
 *
 * オシロスコープの設定値は 1 ns から 100 MHz まで 15 桁以上に跨る。指数表記のままでは読めないため、
 * 3 桁刻みの接頭辞へ変換する。数値と単位は分離して保持し、UI 側で組み合わせる。
 */
object EngineeringUnits {
    /** 接頭辞と 10 の冪指数の対応。倍量側は T まで、分量側は f まで扱う。 */
    private val PREFIXES: List<Pair<Int, String>> = listOf(
        12 to "T",
        9 to "G",
        6 to "M",
        3 to "k",
        0 to "",
        -3 to "m",
        -6 to "µ",
        -9 to "n",
        -12 to "p",
        -15 to "f",
    )

    /** 入力解釈用。`u` は `µ` の代替として受け付ける。 */
    private val PARSE_PREFIXES: Map<Char, Int> = mapOf(
        'T' to 12,
        'G' to 9,
        'M' to 6,
        'k' to 3,
        'K' to 3,
        'm' to -3,
        'µ' to -6,
        'u' to -6,
        'U' to -6,
        'n' to -9,
        'p' to -12,
        'f' to -15,
    )

    private const val DEFAULT_SIGNIFICANT_DIGITS = 4
    private const val MAX_DECIMALS = 6

    /**
     * 数値と単位を分離した整形結果。
     *
     * 例: `format(1.5e-9, "s")` → `Formatted("1.5", "ns")`
     */
    data class Formatted(val value: String, val unit: String) {
        override fun toString(): String = if (unit.isEmpty()) value else "$value $unit"
    }

    /**
     * 工学表記へ整形する。
     *
     * @param unit 単位記号。空文字なら接頭辞のみ付ける。
     * @param significantDigits 有効桁数。
     */
    fun format(value: Double, unit: String = "", significantDigits: Int = DEFAULT_SIGNIFICANT_DIGITS): Formatted {
        if (value.isNaN()) return Formatted("---", unit)
        if (value.isInfinite()) return Formatted(if (value > 0) "∞" else "-∞", unit)
        if (value == 0.0) return Formatted("0", unit)

        val magnitude = floor(log10(abs(value))).toInt()
        // 3 の倍数へ丸め下げる。負の指数でも下方向へ揃える。
        val exponent = Math.floorDiv(magnitude, 3) * 3
        val clamped = exponent.coerceIn(PREFIXES.last().first, PREFIXES.first().first)
        val prefix = PREFIXES.firstOrNull { it.first == clamped }?.second ?: ""
        val scaled = value / 10.0.pow(clamped)

        return Formatted(trimNumber(scaled, significantDigits), prefix + unit)
    }

    /** `format` の結果を 1 つの文字列にする。 */
    fun formatToString(value: Double, unit: String = "", significantDigits: Int = DEFAULT_SIGNIFICANT_DIGITS): String =
        format(value, unit, significantDigits).toString()

    /**
     * 有効桁数に合わせて整形し、余分な 0 と小数点を落とす。
     */
    private fun trimNumber(value: Double, significantDigits: Int): String {
        val integerDigits = if (abs(value) >= 1.0) floor(log10(abs(value))).toInt() + 1 else 1
        val decimals = (significantDigits - integerDigits).coerceIn(0, MAX_DECIMALS)
        val text = String.format(Locale.US, "%.${decimals}f", value)
        return if (text.contains('.')) {
            text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
        } else {
            text
        }
    }

    /**
     * 工学表記の文字列を数値へ戻す。
     *
     * 受け付ける形式の例: `1.5n`, `1.5ns`, `2.5mV`, `1k`, `-3.3`, `1e-6`, `500 MHz`
     * 単位記号は無視する（接頭辞の判別のみに使う）。解釈できない場合は null。
     *
     * @param unit 単位が分かっている場合に渡すと、`m` が「ミリ」か「メートル」かの曖昧さを解消できる。
     */
    fun parse(input: String, unit: String = ""): Double? {
        val text = input.trim().replace(",", "")
        if (text.isEmpty()) return null

        // まず指数表記をそのまま解釈できるか試す（接頭辞なし）。
        text.toDoubleOrNull()?.let { return it }

        val numberEnd = findNumberEnd(text) ?: return null
        val numberPart = text.substring(0, numberEnd)
        val suffix = text.substring(numberEnd).trim()
        val mantissa = numberPart.toDoubleOrNull() ?: return null
        if (suffix.isEmpty()) return mantissa

        // 単位だけが続く場合（"3.3V"）は接頭辞なし。
        if (unit.isNotEmpty() && suffix.equals(unit, ignoreCase = true)) return mantissa

        val prefixChar = suffix.first()
        val exponent = PARSE_PREFIXES[prefixChar]
        if (exponent == null) {
            // 未知の接尾辞は単位とみなす。ただし単位指定と食い違うなら解釈失敗とする。
            return if (unit.isEmpty() || suffix.equals(unit, ignoreCase = true)) mantissa else null
        }

        val remainder = suffix.drop(1)
        if (remainder.isNotEmpty() && unit.isNotEmpty() && !remainder.equals(unit, ignoreCase = true)) {
            return null
        }
        return mantissa * 10.0.pow(exponent)
    }

    /** 数値部分の終端位置を返す。指数表記（`1e-6`）も数値として含める。 */
    private fun findNumberEnd(text: String): Int? {
        var index = 0
        if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
        val digitsStart = index
        while (index < text.length && (text[index].isDigit() || text[index] == '.')) index++
        if (index == digitsStart) return null

        // 指数部。`1e-6` の `e` は接頭辞ではない。
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            var probe = index + 1
            if (probe < text.length && (text[probe] == '+' || text[probe] == '-')) probe++
            val expDigitsStart = probe
            while (probe < text.length && text[probe].isDigit()) probe++
            if (probe > expDigitsStart) index = probe
        }
        return index
    }

    /**
     * 秒を時間軸表示向けに整形する。`1/Δt` から周波数へ換算する用途も想定する。
     */
    fun formatSeconds(seconds: Double, significantDigits: Int = DEFAULT_SIGNIFICANT_DIGITS): String =
        formatToString(seconds, "s", significantDigits)

    fun formatVolts(volts: Double, significantDigits: Int = DEFAULT_SIGNIFICANT_DIGITS): String =
        formatToString(volts, "V", significantDigits)

    fun formatHertz(hertz: Double, significantDigits: Int = DEFAULT_SIGNIFICANT_DIGITS): String =
        formatToString(hertz, "Hz", significantDigits)

    /** Δt から周波数へ換算する。0 除算は null。 */
    fun deltaTimeToFrequency(deltaSeconds: Double): Double? = if (deltaSeconds == 0.0 || deltaSeconds.isNaN()) null else 1.0 / deltaSeconds

    /** バイト数の表示。通信ログで転送量を示す。 */
    fun formatBytes(bytes: Long): String {
        if (bytes < BYTES_PER_KIB) return "$bytes B"
        val units = listOf("KiB", "MiB", "GiB")
        var value = bytes.toDouble() / BYTES_PER_KIB
        var unitIndex = 0
        while (value >= BYTES_PER_KIB && unitIndex < units.lastIndex) {
            value /= BYTES_PER_KIB
            unitIndex++
        }
        val rounded = (value * 10).roundToLong() / 10.0
        return "${trimNumber(rounded, 3)} ${units[unitIndex]}"
    }

    private const val BYTES_PER_KIB = 1024.0
}
