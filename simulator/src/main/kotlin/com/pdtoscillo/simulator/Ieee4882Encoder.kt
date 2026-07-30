package com.pdtoscillo.simulator

import java.io.ByteArrayOutputStream

/**
 * IEEE 488.2 definite-length block の組み立て。
 *
 * ```text
 * #<桁数><データ長><バイナリデータ>
 * ```
 *
 * 受信側の解析実装とは意図的に共有しない。同じコードで作って同じコードで解析すると、
 * 双方が同じ誤解をしていてもテストが通ってしまう。
 */
object Ieee4882Encoder {
    /**
     * @param declaredLengthOverride ヘッダに書き込む長さを実データ長と食い違わせる場合に指定する
     *   （`FaultMode.BAD_BLOCK_LENGTH` / `HUGE_BLOCK_LENGTH` の再現用）。
     */
    fun encode(payload: ByteArray, declaredLengthOverride: Long? = null): ByteArray {
        val declared = (declaredLengthOverride ?: payload.size.toLong()).toString()
        val out = ByteArrayOutputStream(payload.size + declared.length + HEADER_OVERHEAD)
        out.write('#'.code)
        out.write(declared.length.toString()[0].code)
        out.write(declared.toByteArray(Charsets.US_ASCII))
        out.write(payload)
        out.write('\n'.code)
        return out.toByteArray()
    }

    /** 符号付き / 符号なし整数を指定バイト数・バイト順で並べる。 */
    fun packIntegers(values: IntArray, bytesPerPoint: Int, bigEndian: Boolean): ByteArray {
        val out = ByteArray(values.size * bytesPerPoint)
        var index = 0
        for (value in values) {
            for (byteIndex in 0 until bytesPerPoint) {
                val shift = if (bigEndian) (bytesPerPoint - 1 - byteIndex) * BITS_PER_BYTE else byteIndex * BITS_PER_BYTE
                out[index++] = ((value shr shift) and BYTE_MASK).toByte()
            }
        }
        return out
    }

    /** RF 周波数領域トレース用の 4 バイト浮動小数列。 */
    fun packFloats(values: FloatArray, bigEndian: Boolean): ByteArray {
        val out = ByteArray(values.size * Float.SIZE_BYTES)
        var index = 0
        for (value in values) {
            val bits = value.toRawBits()
            for (byteIndex in 0 until Float.SIZE_BYTES) {
                val shift = if (bigEndian) {
                    (Float.SIZE_BYTES - 1 - byteIndex) * BITS_PER_BYTE
                } else {
                    byteIndex * BITS_PER_BYTE
                }
                out[index++] = ((bits shr shift) and BYTE_MASK).toByte()
            }
        }
        return out
    }

    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF
    private const val HEADER_OVERHEAD = 4
}
