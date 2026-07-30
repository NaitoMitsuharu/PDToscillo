package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.ModelFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdnParserTest {

    @Test
    fun `標準的な4要素の応答を解析する`() {
        val identity = IdnParser.parse("TEKTRONIX,MDO4104C,C012345,CF:91.1CT FV:v1.28")

        assertEquals("TEKTRONIX", identity.manufacturer)
        assertEquals("MDO4104C", identity.model)
        assertEquals("C012345", identity.serialNumber)
        assertEquals("CF:91.1CT FV:v1.28", identity.firmwareVersion)
        assertTrue(identity.isTektronix)
    }

    @Test
    fun `要素が足りない応答でも例外にしない`() {
        val identity = IdnParser.parse("TEKTRONIX,DPO4054")

        assertEquals("TEKTRONIX", identity.manufacturer)
        assertEquals("DPO4054", identity.model)
        assertNull(identity.serialNumber)
        assertNull(identity.firmwareVersion)
    }

    @Test
    fun `前後の空白と改行を取り除く`() {
        val identity = IdnParser.parse("  TEKTRONIX, MSO4104B , C020001 , FV:v2.68  \n")

        assertEquals("TEKTRONIX", identity.manufacturer)
        assertEquals("MSO4104B", identity.model)
        assertEquals("C020001", identity.serialNumber)
    }

    @Test
    fun `空の応答でも生応答を保持する`() {
        val identity = IdnParser.parse("")

        assertEquals("", identity.manufacturer)
        assertEquals("", identity.raw)
    }

    @Test
    fun `ヘッダ付きの応答からも解析する`() {
        val identity = IdnParser.parse(":IDN TEKTRONIX,MDO4104C,C012345,FV:v1.28")

        assertEquals("TEKTRONIX", identity.manufacturer)
        assertEquals("MDO4104C", identity.model)
    }

    @Test
    fun `Tektronix 以外は判別できる`() {
        val identity = IdnParser.parse("KEYSIGHT,DSOX1102G,CN12345,02.10")

        assertFalse(identity.isTektronix)
        assertEquals("DSOX1102G", identity.model)
    }
}

class ModelNameResolverTest {

    @Test
    fun `DPO は4桁目からチャンネル数を推定しデジタルなしと判断する`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("TEKTRONIX,DPO4054,C1,FV"))

        assertEquals(4, hints.analogChannelCount)
        assertEquals(false, hints.hasDigitalChannels)
        assertEquals(false, hints.hasRfChannel)
        assertEquals(ModelFamily.GEN1_DPO_MSO_4000, hints.family)
    }

    @Test
    fun `2ch モデルを正しく推定する`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("TEKTRONIX,DPO4032,C1,FV"))

        assertEquals(2, hints.analogChannelCount)
        assertEquals(ModelFamily.GEN1_DPO_MSO_4000, hints.family)
    }

    @Test
    fun `MSO はデジタル16chを持つと判断する`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("TEKTRONIX,MSO4104,C1,FV"))

        assertEquals(4, hints.analogChannelCount)
        assertEquals(true, hints.hasDigitalChannels)
        assertEquals(false, hints.hasRfChannel)
    }

    @Test
    fun `B 世代を判別する`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("TEKTRONIX,MSO4104B,C1,FV"))

        assertEquals(ModelFamily.GEN2_4000B_MDO4000, hints.family)
    }

    @Test
    fun `MDO は RF を持ちデジタルは不明とする`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("TEKTRONIX,MDO4104C,C1,FV"))

        assertEquals(true, hints.hasRfChannel)
        // MDO のデジタルはオプション。モデル名からは判断できないため null（不明）。
        assertNull(hints.hasDigitalChannels)
        assertEquals(ModelFamily.GEN3_MDO4000BC, hints.family)
    }

    @Test
    fun `MDO 無印は Gen2 として扱う`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("TEKTRONIX,MDO4104,C1,FV"))

        assertEquals(ModelFamily.GEN2_4000B_MDO4000, hints.family)
    }

    @Test
    fun `4000 系以外は非対応と判断する`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("TEKTRONIX,MDO3054,C1,FV"))

        assertEquals(ModelFamily.UNSUPPORTED, hints.family)
    }

    @Test
    fun `Tektronix 以外は非対応と判断する`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("KEYSIGHT,DSOX1102G,CN1,02.10"))

        assertEquals(ModelFamily.UNSUPPORTED, hints.family)
    }

    @Test
    fun `解析できないモデル名は不明として扱いチャンネル数を推定しない`() {
        val hints = ModelNameResolver.resolve(IdnParser.parse("TEKTRONIX,,C1,FV"))

        assertEquals(ModelFamily.UNKNOWN_4000, hints.family)
        assertNull(hints.analogChannelCount)
    }
}
