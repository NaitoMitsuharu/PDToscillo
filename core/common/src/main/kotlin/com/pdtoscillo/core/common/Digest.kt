package com.pdtoscillo.core.common

import java.security.MessageDigest

/** バイナリ応答の同一性確認用ハッシュ。通信ログには本体を残さずハッシュとサイズだけを記録する。 */
object Digest {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }

    /** ログ表示向けの短縮ハッシュ。 */
    fun shortSha256(bytes: ByteArray): String = sha256(bytes).take(SHORT_LENGTH)

    private const val SHORT_LENGTH = 12
}
