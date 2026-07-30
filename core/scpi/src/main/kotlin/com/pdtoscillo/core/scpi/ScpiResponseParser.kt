package com.pdtoscillo.core.scpi

/**
 * SCPI テキスト応答の解析。
 *
 * 機種とファームウェア、`HEADer` の設定によって応答形式が変わるため、次のすべてを受け付ける。
 *
 * ```text
 * 10000                                       ヘッダなし
 * :HORIZONTAL:RECORDLENGTH 10000               ヘッダあり
 * :WFMOUTPRE:BYT_NR 1;BIT_NR 8;ENCDG BINARY    複合応答（ヘッダあり）
 * 1;8;BINARY;RI;MSB;"Ch1, ...";10000           複合応答（ヘッダなし）
 * "Ch1, DC coupling, 1.000V/div"                引用符付き（内部のカンマ・セミコロンを含む）
 * ```
 *
 * 引用符の内側にある区切り文字で分割してはならない。波形の `WFID` には
 * カンマとセミコロンが実際に含まれる。
 */
object ScpiResponseParser {
    /**
     * セミコロン区切りの応答を分割する。引用符内のセミコロンは区切りとみなさない。
     */
    fun splitSemicolons(response: String): List<String> = splitRespectingQuotes(response, ';')

    /**
     * カンマ区切りの応答を分割する。`*IDN?` や `EVMsg?` のように引用符付き要素を含む場合に使う。
     */
    fun splitCommas(response: String): List<String> = splitRespectingQuotes(response, ',')

    private fun splitRespectingQuotes(response: String, delimiter: Char): List<String> {
        if (response.isEmpty()) return emptyList()
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        var inQuotes = false
        for (char in response) {
            when {
                char == '"' -> {
                    inQuotes = !inQuotes
                    builder.append(char)
                }

                char == delimiter && !inQuotes -> {
                    result += builder.toString().trim()
                    builder.clear()
                }

                else -> builder.append(char)
            }
        }
        result += builder.toString().trim()
        return result
    }

    /**
     * ヘッダ（`:HORIZONTAL:RECORDLENGTH ` のような先頭のコマンドパス）を取り除いて値だけを返す。
     *
     * ヘッダが付いていない応答はそのまま返す。値そのものが引用符で始まる場合は分解しない。
     */
    fun stripHeader(element: String): String {
        val trimmed = element.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.startsWith("\"")) return trimmed

        // ヘッダ付きは "PATH value" の形。パス部分に空白は入らない。
        val spaceIndex = trimmed.indexOf(' ')
        if (spaceIndex <= 0) return trimmed
        val head = trimmed.substring(0, spaceIndex)
        // 先頭が ':' か、英字とコロン・アンダースコアだけで構成されていればヘッダとみなす。
        val looksLikeHeader = head.startsWith(":") ||
            (head.contains(':') && head.all { it.isLetterOrDigit() || it == ':' || it == '_' })
        return if (looksLikeHeader) trimmed.substring(spaceIndex + 1).trim() else trimmed
    }

    /** 前後の引用符を外す。内部の引用符は保持する。 */
    fun unquote(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.length >= 2 && trimmed.startsWith('"') && trimmed.endsWith('"')) {
            trimmed.substring(1, trimmed.length - 1)
        } else {
            trimmed
        }
    }

    /**
     * ヘッダ付き複合応答を「フィールド名 → 値」へ変換する。
     *
     * `:WFMOUTPRE:BYT_NR 1;BIT_NR 8;...` の場合、先頭要素のフィールド名は
     * `BYT_NR`（`:WFMOUTPRE:` を除いた最後の要素）になる。
     *
     * ヘッダが付いていない応答では空の Map を返す。呼び出し側は位置による解釈へ切り替える。
     */
    fun parseHeaderedFields(response: String): Map<String, String> {
        val elements = splitSemicolons(response)
        val fields = LinkedHashMap<String, String>()
        for (element in elements) {
            val trimmed = element.trim()
            val spaceIndex = trimmed.indexOf(' ')
            if (spaceIndex <= 0) continue
            val head = trimmed.substring(0, spaceIndex).trimStart(':')
            if (head.isEmpty() || head.startsWith("\"")) continue
            if (!head.all { it.isLetterOrDigit() || it == ':' || it == '_' }) continue
            val name = head.substringAfterLast(':').uppercase()
            if (name.isEmpty()) continue
            fields[name] = trimmed.substring(spaceIndex + 1).trim()
        }
        return fields
    }

    /** 数値へ変換する。指数表記と余分な空白を許容する。変換できなければ null。 */
    fun parseDouble(value: String): Double? = stripHeader(value).let(::unquote).trim().toDoubleOrNull()

    fun parseLong(value: String): Long? {
        val text = stripHeader(value).let(::unquote).trim()
        text.toLongOrNull()?.let { return it }
        // "10000.0" のような応答も受け付ける。
        return text.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toLong()
    }

    fun parseInt(value: String): Int? = parseLong(value)?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else null }

    /**
     * `1` / `0` / `ON` / `OFF` / `TRUE` / `FALSE` を真偽値へ変換する。
     * `CONFIGuration:*?` は 0 か 1 を返すが、機種によって表記が異なる可能性に備える。
     */
    fun parseBoolean(value: String): Boolean? {
        val text = stripHeader(value).let(::unquote).trim().uppercase()
        return when (text) {
            "1", "ON", "TRUE", "YES" -> true
            "0", "OFF", "FALSE", "NO" -> false
            else -> text.toDoubleOrNull()?.let { it != 0.0 }
        }
    }

    /** カンマ区切りの数値列（`CONFIGuration:ANALOg:RECLENS?` など）を解析する。 */
    fun parseLongList(response: String): List<Long> = splitCommas(stripHeader(response)).mapNotNull { parseLong(it) }

    /**
     * 応答が Terminal プロトコルのエコーやプロンプトを含んでいないか判定する。
     *
     * `Protocol: Terminal` のまま接続すると、コマンドのエコーやプロンプト（`>`）が混入し、
     * 解析結果が壊れる。接続診断で早期に気付けるようにする。
     */
    fun looksLikeTerminalArtifact(response: String, sentCommand: String): Boolean {
        val trimmed = response.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith(">")) return true
        if (trimmed.equals(sentCommand.trim(), ignoreCase = true)) return true
        return trimmed.startsWith(sentCommand.trim(), ignoreCase = true) && trimmed.length > sentCommand.trim().length
    }
}
