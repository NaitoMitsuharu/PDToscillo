package com.pdtoscillo.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionLogWriterTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun writer(maxBytes: Long = SessionLogWriter.DEFAULT_MAX_BYTES): SessionLogWriter =
        SessionLogWriter(temporaryFolder.newFile("session.log"), maxBytes)

    @Test
    fun `ヘッダと行を書き出す`() {
        val target = writer()

        target.begin("PDToscillo セッションログ\n")
        target.append("接続しました")

        val text = target.target.readText()
        assertTrue(text.contains("PDToscillo セッションログ"))
        assertTrue(text.contains("接続しました"))
    }

    @Test
    fun `行には時刻が付く`() {
        val target = writer()
        target.begin("")

        target.append("テスト")

        // 2026-07-30 12:34:56.789 の形式
        assertTrue(target.target.readText().contains(Regex("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}""")))
    }

    @Test
    fun `begin で作り直す`() {
        val target = writer()
        target.begin("1 回目\n")
        target.append("古い行")

        target.begin("2 回目\n")

        val text = target.target.readText()
        assertTrue(text.contains("2 回目"))
        assertFalse("前回の内容が残っています", text.contains("古い行"))
    }

    @Test
    fun `上限に達したら記録を止める`() {
        // ヘッダだけでほぼ埋まる小さな上限にする。
        val target = writer(maxBytes = 200)
        target.begin("ヘッダ\n")

        repeat(100) { target.append("これは長めの行です。$it") }

        assertTrue("打ち切られていません", target.isTruncated)
        assertTrue(target.target.readText().contains("記録を停止しました"))
    }

    @Test
    fun `上限に達しても先頭の内容は残る`() {
        // 一番知りたいのは最初の接続でのやり取り。古い行を捨てて新しい行を残すと、
        // それが消えてしまう。
        val target = writer(maxBytes = 300)
        target.begin("最初の重要な行\n")

        repeat(100) { target.append("後から来た行 $it") }

        assertTrue(target.target.readText().contains("最初の重要な行"))
    }

    @Test
    fun `打ち切り後は追記しない`() {
        val target = writer(maxBytes = 120)
        target.begin("ヘッダ\n")
        repeat(50) { target.append("行 $it") }
        val sizeAfterTruncation = target.target.length()

        target.append("これは書かれないはず")

        assertEquals(sizeAfterTruncation, target.target.length())
    }

    @Test
    fun `見出しを書ける`() {
        val target = writer()
        target.begin("")

        target.appendSection("ネットワークの状態")

        assertTrue(target.target.readText().contains("===== ネットワークの状態 ====="))
    }

    @Test
    fun `複数行をそのまま書ける`() {
        val target = writer()
        target.begin("")

        target.appendBlock("1 行目\n2 行目\n3 行目")

        val lines = target.target.readText().trim().lines()
        assertEquals(listOf("1 行目", "2 行目", "3 行目"), lines)
    }

    @Test
    fun `ファイル名は開始時刻から作る`() {
        val name = SessionLogWriter.buildFileName(0L)

        assertTrue(name.startsWith("pdtoscillo_"))
        assertTrue(name.endsWith(".log"))
    }

    @Test
    fun `書き込んだバイト数を数える`() {
        val target = writer()
        target.begin("")
        val before = target.writtenBytes

        target.append("追記")

        assertTrue(target.writtenBytes > before)
    }
}
