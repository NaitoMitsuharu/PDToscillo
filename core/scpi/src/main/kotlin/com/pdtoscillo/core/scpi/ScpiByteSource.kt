package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.ScopeError

/** SCPI 応答の解析中に発生したエラー。分類は [ScopeError] が持つ。 */
class ScpiException(val error: ScopeError) : Exception(error.detail ?: error::class.simpleName, error.cause)

/**
 * バイト単位の読み出し口。
 *
 * TCP の 1 回の read で応答が揃うとは限らないため、必要なバイト数を読み切る責務をここに集約する。
 * 実装（ソケット、テスト用のメモリ）を差し替えられるようにインターフェースで切る。
 */
interface ScpiByteSource {
    /** 1 バイト読む。EOF なら -1。 */
    suspend fun readByte(): Int

    /**
     * [length] バイトを読み切る。
     * 途中で EOF に達した場合は [ScpiException] を投げる（部分データを返さない）。
     */
    suspend fun readFully(target: ByteArray, offset: Int, length: Int)

    /**
     * 直前に読んだ 1 バイトを戻す。
     *
     * ブロック末尾の終端バイトを「あれば消費する」ために必要。終端が来ていない状態で
     * 先読みしてしまった場合に、次の読み出しへ影響を残さないようにする。
     */
    suspend fun unreadByte(value: Int)
}

/** テストと、既に受信済みバイト列を解析する用途のための実装。 */
class ByteArrayScpiSource(
    private val data: ByteArray,
    /** 1 回の `readFully` で返す最大バイト数。TCP の分割受信を再現するために使う。 */
    private val maxChunk: Int = Int.MAX_VALUE,
) : ScpiByteSource {
    private var position = 0
    private val pushback = ArrayDeque<Int>()

    override suspend fun readByte(): Int {
        pushback.removeLastOrNull()?.let { return it }
        return if (position < data.size) data[position++].toInt() and BYTE_MASK else -1
    }

    override suspend fun readFully(target: ByteArray, offset: Int, length: Int) {
        var written = 0
        while (written < length) {
            val remaining = length - written
            val chunk = minOf(maxChunk, remaining)
            for (index in 0 until chunk) {
                val value = readByte()
                if (value < 0) {
                    throw ScpiException(
                        ScopeError.MalformedBinaryBlock(
                            "データが不足しています: 要求 $length バイト、取得 ${written + index} バイト",
                        ),
                    )
                }
                target[offset + written + index] = value.toByte()
            }
            written += chunk
        }
    }

    override suspend fun unreadByte(value: Int) {
        pushback.addLast(value)
    }

    private companion object {
        const val BYTE_MASK = 0xFF
    }
}
