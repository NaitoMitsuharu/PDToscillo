package com.pdtoscillo.core.scpi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScpiResponseParserTest {

    @Test
    fun `セミコロン区切りを分割する`() {
        val parts = ScpiResponseParser.splitSemicolons("1;8;BINARY;RI;MSB")

        assertEquals(listOf("1", "8", "BINARY", "RI", "MSB"), parts)
    }

    @Test
    fun `引用符の内側のセミコロンでは分割しない`() {
        // WFID には実際にカンマとセミコロンが含まれ得る。
        val response = """1;8;"Ch1, DC coupling; 100.0mV/div";10000"""
        val parts = ScpiResponseParser.splitSemicolons(response)

        assertEquals(4, parts.size)
        assertEquals(""""Ch1, DC coupling; 100.0mV/div"""", parts[2])
    }

    @Test
    fun `引用符の内側のカンマでは分割しない`() {
        val parts = ScpiResponseParser.splitCommas("""2200,"Measurement error, Measurement system error"""")

        assertEquals(2, parts.size)
        assertEquals("2200", parts[0])
        assertEquals(""""Measurement error, Measurement system error"""", parts[1])
    }

    @Test
    fun `ヘッダを取り除く`() {
        assertEquals("10000", ScpiResponseParser.stripHeader(":HORIZONTAL:RECORDLENGTH 10000"))
        assertEquals("1", ScpiResponseParser.stripHeader(":WFMOUTPRE:BYT_NR 1"))
        assertEquals("RUNSTOP", ScpiResponseParser.stripHeader("ACQUIRE:STOPAFTER RUNSTOP"))
    }

    @Test
    fun `ヘッダが無い応答はそのまま返す`() {
        assertEquals("10000", ScpiResponseParser.stripHeader("10000"))
        assertEquals("4.0000E-9", ScpiResponseParser.stripHeader("4.0000E-9"))
    }

    @Test
    fun `値に空白を含む応答をヘッダと誤認しない`() {
        // 引用符で始まる応答はヘッダ解析しない。
        val response = """"Ch1, DC coupling, 100.0mV/div""""
        assertEquals(response, ScpiResponseParser.stripHeader(response))
    }

    @Test
    fun `ファームウェア文字列をヘッダと誤認しない`() {
        // "CF:91.1CT FV:v1.28" は先頭にコロンを含むがヘッダではない。
        // ヘッダ判定は保守的でよいが、少なくとも値が消えてはいけない。
        val stripped = ScpiResponseParser.stripHeader("CF:91.1CT FV:v1.28")
        assertTrue(stripped.contains("FV:v1.28"))
    }

    @Test
    fun `引用符を外す`() {
        assertEquals("s", ScpiResponseParser.unquote("\"s\""))
        assertEquals("Ch1, DC", ScpiResponseParser.unquote("\"Ch1, DC\""))
        assertEquals("plain", ScpiResponseParser.unquote("plain"))
        assertEquals("", ScpiResponseParser.unquote("\"\""))
    }

    @Test
    fun `ヘッダ付き複合応答をフィールド名で引ける`() {
        val response = ":WFMOUTPRE:BYT_NR 2;BIT_NR 16;ENCDG ASCII;BN_FMT RI;BYT_OR MSB;" +
            """WFID "Ch1, DC coupling, 100.0mV/div, 4.000us/div, 10000 points, Sample mode";""" +
            "NR_PT 10000;PT_FMT Y;XUNIT \"s\";XINCR 4.0000E-9;XZERO -20.0000E-6;PT_OFF 0;" +
            "YUNIT \"V\";YMULT 15.6250E-6;YOFF 6.4000E+3;YZERO 0.0000"

        val fields = ScpiResponseParser.parseHeaderedFields(response)

        assertEquals("2", fields["BYT_NR"])
        assertEquals("16", fields["BIT_NR"])
        assertEquals("ASCII", fields["ENCDG"])
        assertEquals("RI", fields["BN_FMT"])
        assertEquals("MSB", fields["BYT_OR"])
        assertEquals("10000", fields["NR_PT"])
        assertEquals("4.0000E-9", fields["XINCR"])
        assertEquals("-20.0000E-6", fields["XZERO"])
        assertEquals("0", fields["PT_OFF"])
        assertEquals("15.6250E-6", fields["YMULT"])
        assertEquals("6.4000E+3", fields["YOFF"])
        assertEquals("0.0000", fields["YZERO"])
        assertEquals("\"s\"", fields["XUNIT"])
    }

    @Test
    fun `ヘッダなし複合応答ではフィールド名を得られない`() {
        val response = """1;8;BINARY;RI;MSB;"Ch1";10000;Y;"s";4.0E-9"""

        val fields = ScpiResponseParser.parseHeaderedFields(response)

        // 位置による解釈へ切り替える必要があることが分かればよい。
        assertTrue(fields.isEmpty() || fields.size < 5)
    }

    @Test
    fun `数値を解析する`() {
        assertEquals(4.0e-9, ScpiResponseParser.parseDouble("4.0000E-9")!!, 1e-21)
        assertEquals(4.0e-9, ScpiResponseParser.parseDouble(":WFMOUTPRE:XINCR 4.0000E-9")!!, 1e-21)
        assertEquals(-20.0e-6, ScpiResponseParser.parseDouble("-20.0000E-6")!!, 1e-18)
        assertNull(ScpiResponseParser.parseDouble("ASCII"))
    }

    @Test
    fun `整数を解析する`() {
        assertEquals(10_000L, ScpiResponseParser.parseLong("10000"))
        assertEquals(10_000L, ScpiResponseParser.parseLong(":HORIZONTAL:RECORDLENGTH 10000"))
        assertEquals(10_000L, ScpiResponseParser.parseLong("10000.0"))
        assertNull(ScpiResponseParser.parseLong("10000.5"))
    }

    @Test
    fun `真偽値を解析する`() {
        assertEquals(true, ScpiResponseParser.parseBoolean("1"))
        assertEquals(false, ScpiResponseParser.parseBoolean("0"))
        assertEquals(true, ScpiResponseParser.parseBoolean("ON"))
        assertEquals(false, ScpiResponseParser.parseBoolean("OFF"))
        assertEquals(true, ScpiResponseParser.parseBoolean(":SELECT:CH1 1"))
        assertNull(ScpiResponseParser.parseBoolean("MAYBE"))
    }

    @Test
    fun `カンマ区切りの数値列を解析する`() {
        val lengths = ScpiResponseParser.parseLongList("1000,10000,100000,1000000,10000000")

        assertEquals(listOf(1_000L, 10_000L, 100_000L, 1_000_000L, 10_000_000L), lengths)
    }

    @Test
    fun `Terminal モードのエコーを検出する`() {
        assertTrue(ScpiResponseParser.looksLikeTerminalArtifact("*IDN?", "*IDN?"))
        assertTrue(ScpiResponseParser.looksLikeTerminalArtifact("> ", "*IDN?"))
        assertFalse(
            ScpiResponseParser.looksLikeTerminalArtifact("TEKTRONIX,MDO4104C,C1,FV", "*IDN?"),
        )
    }
}
