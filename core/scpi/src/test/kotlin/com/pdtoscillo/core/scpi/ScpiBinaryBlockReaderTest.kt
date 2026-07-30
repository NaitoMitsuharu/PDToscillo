package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.ScopeError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ScpiBinaryBlockReaderTest {

    private val maxBytes = 1L * 1024 * 1024

    private fun block(payload: ByteArray, trailing: String = "\n"): ByteArray {
        val length = payload.size.toString()
        val header = "#${length.length}$length"
        return header.toByteArray(Charsets.US_ASCII) + payload + trailing.toByteArray(Charsets.US_ASCII)
    }

    @Test
    fun `definite-length ブロックを読む`() = runTest {
        val payload = ByteArray(10) { it.toByte() }
        val source = ByteArrayScpiSource(block(payload))

        val result = ScpiBinaryBlockReader.read(source, maxBytes)

        assertArrayEquals(payload, result)
    }

    @Test
    fun `マニュアル記載の例と同じ形式を読める`() = runTest {
        // #510000 に続いて 10,000 バイト（桁数 5、長さ 10000）
        val payload = ByteArray(10_000) { (it % 256).toByte() }
        val encoded = block(payload)

        assertEquals("#510000", String(encoded.copyOfRange(0, 7), Charsets.US_ASCII))

        val result = ScpiBinaryBlockReader.read(ByteArrayScpiSource(encoded), maxBytes)

        assertEquals(10_000, result.size)
        assertArrayEquals(payload, result)
    }

    @Test
    fun `1回のreadで全部届かなくても読み切る`() = runTest {
        val payload = ByteArray(5_000) { (it % 256).toByte() }
        // 1 回あたり 7 バイトずつしか返さない。TCP のパケット境界と応答境界のずれを再現。
        val source = ByteArrayScpiSource(block(payload), maxChunk = 7)

        val result = ScpiBinaryBlockReader.read(source, maxBytes)

        assertArrayEquals(payload, result)
    }

    @Test
    fun `1バイトずつでも読み切る`() = runTest {
        val payload = ByteArray(300) { (it % 256).toByte() }
        val source = ByteArrayScpiSource(block(payload), maxChunk = 1)

        val result = ScpiBinaryBlockReader.read(source, maxBytes)

        assertArrayEquals(payload, result)
    }

    @Test
    fun `空のブロックを読める`() = runTest {
        val source = ByteArrayScpiSource("#10\n".toByteArray(Charsets.US_ASCII))

        val result = ScpiBinaryBlockReader.read(source, maxBytes)

        assertEquals(0, result.size)
    }

    @Test
    fun `先頭の余分な改行を読み飛ばす`() = runTest {
        val payload = ByteArray(4) { it.toByte() }
        val source = ByteArrayScpiSource("\n\r\n".toByteArray(Charsets.US_ASCII) + block(payload))

        val result = ScpiBinaryBlockReader.read(source, maxBytes)

        assertArrayEquals(payload, result)
    }

    @Test
    fun `CRLF 終端も正しく消費する`() = runTest {
        val payload = ByteArray(4) { it.toByte() }
        // ブロックの後に CRLF、続けて次の応答が来る状況。
        val data = block(payload, trailing = "\r\n") + "NEXT\n".toByteArray(Charsets.US_ASCII)
        val source = ByteArrayScpiSource(data)

        val result = ScpiBinaryBlockReader.read(source, maxBytes)
        assertArrayEquals(payload, result)

        // 次の応答の先頭が消えていないことを確認する。
        val next = StringBuilder()
        while (true) {
            val value = source.readByte()
            if (value < 0 || value == '\n'.code) break
            next.append(value.toChar())
        }
        assertEquals("NEXT", next.toString())
    }

    @Test
    fun `終端が無くても本体を返す`() = runTest {
        val payload = ByteArray(4) { it.toByte() }
        val source = ByteArrayScpiSource(block(payload, trailing = ""))

        val result = ScpiBinaryBlockReader.read(source, maxBytes)

        assertArrayEquals(payload, result)
    }

    @Test
    fun `シャープで始まらない応答は拒否する`() = runTest {
        val source = ByteArrayScpiSource("1,2,3,4\n".toByteArray(Charsets.US_ASCII))

        val error = assertScpiError(source)
        assertTrue(error is ScopeError.MalformedBinaryBlock)
        assertTrue(error.detail!!.contains("'#'"))
    }

    @Test
    fun `indefinite length は明示的に非対応とする`() = runTest {
        val source = ByteArrayScpiSource("#0abcdef\n".toByteArray(Charsets.US_ASCII))

        val error = assertScpiError(source)
        assertTrue(error is ScopeError.MalformedBinaryBlock)
        assertTrue(error.detail!!.contains("#0"))
    }

    @Test
    fun `桁数が数字でない場合は拒否する`() = runTest {
        val source = ByteArrayScpiSource("#X100\n".toByteArray(Charsets.US_ASCII))

        val error = assertScpiError(source)
        assertTrue(error is ScopeError.MalformedBinaryBlock)
    }

    @Test
    fun `上限を超える宣言長は拒否してメモリを確保しない`() = runTest {
        // 桁数は 1 文字なので最大 9 桁。900,000,000 バイト（約 858 MiB）を宣言する。
        // 実際に確保しようとすれば破綻する。
        val source = ByteArrayScpiSource("#9900000000".toByteArray(Charsets.US_ASCII))

        val error = assertScpiError(source, maxBytes = 32L * 1024 * 1024)
        assertTrue(error is ScopeError.MalformedBinaryBlock)
        assertTrue(error.detail!!.contains("上限"))
    }

    @Test
    fun `宣言長よりデータが少ない場合は失敗させる`() = runTest {
        // 100 バイトと宣言して 10 バイトしか送らない。
        val data = "#3100".toByteArray(Charsets.US_ASCII) + ByteArray(10)
        val source = ByteArrayScpiSource(data)

        val error = assertScpiError(source)
        assertTrue(error is ScopeError.MalformedBinaryBlock)
    }

    @Test
    fun `空の応答は接続断として扱える形で失敗する`() = runTest {
        val source = ByteArrayScpiSource(ByteArray(0))

        val error = assertScpiError(source)
        assertTrue(error is ScopeError.MalformedBinaryBlock)
    }

    private suspend fun assertScpiError(source: ScpiByteSource, maxBytes: Long = this.maxBytes): ScopeError {
        try {
            ScpiBinaryBlockReader.read(source, maxBytes)
        } catch (exception: ScpiException) {
            return exception.error
        }
        fail("ScpiException が投げられませんでした")
        error("到達しない")
    }
}
