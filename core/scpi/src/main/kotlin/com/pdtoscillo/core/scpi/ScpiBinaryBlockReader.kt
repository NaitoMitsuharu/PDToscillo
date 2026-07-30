package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.ScopeError

/**
 * IEEE 488.2 のバイナリブロックを読み出す。
 *
 * definite-length 形式:
 * ```text
 * #<桁数><データ長><バイナリデータ>
 * ```
 * 例: `#510000` に続いて 10,000 バイト。
 *
 * 実装上の注意（いずれも実機で実際に起こる）:
 * - **TCP の 1 回の read で全データが届くと仮定しない。** 必要バイト数を読み切るまで繰り返す。
 * - 末尾の LF は「あれば消費する」。無い機種・タイミングでも次の読み出しを壊さない。
 * - 宣言長には上限を設ける。異常な長さでメモリを確保しない。
 * - `#0`（indefinite length）は 4000 系の `CURVe?` では使われないため、
 *   明示的に非対応として扱い、黙って誤ったデータを返さない。
 */
object ScpiBinaryBlockReader {
    private const val BLOCK_INTRODUCER = '#'.code
    private const val LINE_FEED = 0x0A
    private const val CARRIAGE_RETURN = 0x0D
    private const val SPACE = 0x20
    private const val TAB = 0x09
    private const val ASCII_ZERO = '0'.code
    private const val ASCII_NINE = '9'.code

    /**
     * ブロックを 1 つ読む。
     *
     * @param maxBytes 許容する最大データ長。超える宣言長は [ScopeError.MalformedBinaryBlock] とする。
     */
    suspend fun read(source: ScpiByteSource, maxBytes: Long): ByteArray {
        val introducer = skipLeadingWhitespace(source)
        if (introducer < 0) {
            throw ScpiException(ScopeError.MalformedBinaryBlock("応答が空です（接続が切れた可能性があります）"))
        }
        if (introducer != BLOCK_INTRODUCER) {
            throw ScpiException(
                ScopeError.MalformedBinaryBlock(
                    "'#' で始まっていません: 先頭バイト 0x${introducer.toString(HEX_RADIX)}",
                ),
            )
        }

        val digitCountByte = source.readByte()
        if (digitCountByte < 0) {
            throw ScpiException(ScopeError.MalformedBinaryBlock("桁数が読み取れませんでした"))
        }
        if (!isDigit(digitCountByte)) {
            throw ScpiException(
                ScopeError.MalformedBinaryBlock("桁数が数字ではありません: '${digitCountByte.toChar()}'"),
            )
        }
        val digitCount = digitCountByte - ASCII_ZERO
        if (digitCount == 0) {
            throw ScpiException(
                ScopeError.MalformedBinaryBlock("indefinite length ブロック (#0) には対応していません"),
            )
        }

        var declaredLength = 0L
        for (index in 0 until digitCount) {
            val digit = source.readByte()
            if (digit < 0) {
                throw ScpiException(
                    ScopeError.MalformedBinaryBlock("データ長が $digitCount 桁に足りません（$index 桁で終端）"),
                )
            }
            if (!isDigit(digit)) {
                throw ScpiException(
                    ScopeError.MalformedBinaryBlock("データ長に数字以外が含まれます: '${digit.toChar()}'"),
                )
            }
            declaredLength = declaredLength * DECIMAL_RADIX + (digit - ASCII_ZERO)
        }

        if (declaredLength < 0) {
            throw ScpiException(ScopeError.MalformedBinaryBlock("データ長が負の値です"))
        }
        if (declaredLength > maxBytes) {
            throw ScpiException(
                ScopeError.MalformedBinaryBlock("データ長 $declaredLength バイトが上限 $maxBytes バイトを超えています"),
            )
        }

        val payload = ByteArray(declaredLength.toInt())
        if (declaredLength > 0) {
            source.readFully(payload, 0, payload.size)
        }
        consumeTrailingTerminator(source)
        return payload
    }

    /** 前の応答の残り改行などを読み飛ばして最初の意味のあるバイトを返す。 */
    private suspend fun skipLeadingWhitespace(source: ScpiByteSource): Int {
        while (true) {
            val value = source.readByte()
            if (value < 0) return -1
            if (value != LINE_FEED && value != CARRIAGE_RETURN && value != SPACE && value != TAB) return value
        }
    }

    /**
     * 末尾の終端を消費する。
     *
     * 終端以外のバイトだった場合は次の応答の先頭なので押し戻す。
     * ここで押し戻さないと、次の読み出しが 1 バイトずれて壊れる。
     */
    private suspend fun consumeTrailingTerminator(source: ScpiByteSource) {
        val value = try {
            source.readByte()
        } catch (_: ScpiException) {
            // 終端が来ないまま読み取りが失敗しても、本体は取得できているので成功として扱う。
            return
        }
        when {
            value < 0 -> Unit
            value == LINE_FEED -> Unit
            value == CARRIAGE_RETURN -> {
                // CRLF の可能性がある。LF まで消費する。
                val next = source.readByte()
                if (next >= 0 && next != LINE_FEED) source.unreadByte(next)
            }

            else -> source.unreadByte(value)
        }
    }

    private fun isDigit(value: Int): Boolean = value in ASCII_ZERO..ASCII_NINE

    private const val DECIMAL_RADIX = 10
    private const val HEX_RADIX = 16
}
