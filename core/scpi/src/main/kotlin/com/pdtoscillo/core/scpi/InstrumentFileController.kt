package com.pdtoscillo.core.scpi

import com.pdtoscillo.core.model.ScopeError

/** 本体側のファイル 1 件。 */
data class InstrumentFile(
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long?,
    /** 本体が返した生の項目。解析できない形式でも表示できるよう残す。 */
    val raw: String,
)

/**
 * 本体のファイル操作。
 *
 * **本体から返るパスをそのまま信用しない。**
 * `FILESystem:DIR?` の応答は機種依存で、想定外の文字列が混ざり得る。
 * 受け取った名前をそのままコマンドへ差し込むと、意図しないディレクトリを操作できてしまう。
 * ここで正規化し、危険な要素を含むものは拒否する。
 */
class InstrumentFileController(private val client: ScpiClient) {

    /** 現在の作業ディレクトリ。 */
    suspend fun currentDirectory(): String? = runCatching {
        client.queryValue("${TektronixCommands.Files.CURRENT_WORKING_DIRECTORY}?")
    }.getOrNull()

    /**
     * ディレクトリ一覧。
     *
     * 応答はカンマ区切りの引用符付き文字列。機種によって書式差があるため、
     * 解析できない項目も生のまま保持して表示できるようにする。
     */
    suspend fun listFiles(): List<InstrumentFile> {
        val response = runCatching { client.queryText(TektronixCommands.Files.DIRECTORY_QUERY) }
            .getOrNull() ?: return emptyList()

        return ScpiResponseParser.splitCommas(ScpiResponseParser.stripHeader(response))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { raw ->
                val name = ScpiResponseParser.unquote(raw)
                InstrumentFile(
                    name = name,
                    // 末尾がスラッシュならディレクトリとみなす。判定できない場合はファイル扱い。
                    isDirectory = name.endsWith("/") || name.endsWith("\\"),
                    sizeBytes = null,
                    raw = raw,
                )
            }
    }

    /**
     * ファイル名として安全か確認する。
     *
     * 拒否するもの:
     * - 親ディレクトリ参照（`..`）
     * - 引用符（コマンドの引用が壊れる）
     * - 改行（コマンド注入になる）
     * - 空文字
     */
    fun validateName(name: String): Result<String> {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> Result.failure(
                IllegalArgumentException("ファイル名が空です"),
            )

            trimmed.contains("..") -> Result.failure(
                IllegalArgumentException("親ディレクトリ参照 (..) は使えません: $trimmed"),
            )

            trimmed.contains('"') -> Result.failure(
                IllegalArgumentException("引用符は使えません: $trimmed"),
            )

            trimmed.any { it == '\n' || it == '\r' || it == ';' } -> Result.failure(
                IllegalArgumentException("改行やセミコロンは使えません: $trimmed"),
            )

            trimmed.length > MAX_NAME_LENGTH -> Result.failure(
                IllegalArgumentException("ファイル名が長すぎます（最大 $MAX_NAME_LENGTH 文字）"),
            )

            else -> Result.success(trimmed)
        }
    }

    /** 設定を本体へ保存する。 */
    suspend fun saveSetup(fileName: String): Result<Unit> = withValidName(fileName) { name ->
        client.write("${TektronixCommands.Files.SAVE_SETUP} \"$name\"")
    }

    /** 設定を本体から呼び出す。現在の設定は失われるため、呼び出し側で確認を取る。 */
    suspend fun recallSetup(fileName: String): Result<Unit> = withValidName(fileName) { name ->
        client.write("${TektronixCommands.Files.RECALL_SETUP} \"$name\"")
    }

    /** 波形を本体へ保存する。 */
    suspend fun saveWaveform(source: String, fileName: String): Result<Unit> = withValidName(fileName) { name ->
        client.write("${TektronixCommands.Files.SAVE_WAVEFORM} $source,\"$name\"")
    }

    /** 画面イメージを本体へ保存する。 */
    suspend fun saveImage(fileName: String): Result<Unit> = withValidName(fileName) { name ->
        client.write("${TektronixCommands.Files.SAVE_IMAGE} \"$name\"")
    }

    /** ファイルを削除する。取り消せないため、呼び出し側で必ず確認を取る。 */
    suspend fun delete(fileName: String): Result<Unit> = withValidName(fileName) { name ->
        client.write("${TektronixCommands.Files.DELETE} \"$name\"")
    }

    suspend fun rename(from: String, to: String): Result<Unit> {
        val validFrom = validateName(from).getOrElse { return Result.failure(it) }
        val validTo = validateName(to).getOrElse { return Result.failure(it) }
        return runCatching {
            client.write("${TektronixCommands.Files.RENAME} \"$validFrom\",\"$validTo\"")
        }
    }

    /**
     * 本体のファイルを読み出す。
     *
     * 応答は IEEE 488.2 ブロック。大きなファイルもあり得るため、
     * 上限は接続設定の `maxBinaryResponseBytes` に従う。
     */
    suspend fun readFile(fileName: String): Result<ByteArray> {
        val name = validateName(fileName).getOrElse { return Result.failure(it) }
        return runCatching {
            client.queryBinary("${TektronixCommands.Files.READ_FILE} \"$name\"")
        }
    }

    /** 空き容量。 */
    suspend fun freeSpaceBytes(): Long? = runCatching {
        client.queryLong(TektronixCommands.Files.FREE_SPACE_QUERY)
    }.getOrNull()

    /**
     * 画面イメージの取得を開始する。
     *
     * `HARDCopy STARt` の後にデータを読み出す。機種によって手順が異なるため、
     * 失敗した場合は理由をそのまま返す。
     */
    suspend fun startHardcopy(): Result<Unit> = runCatching {
        client.write(TektronixCommands.Files.HARDCOPY_START)
    }

    private suspend inline fun withValidName(fileName: String, crossinline block: suspend (String) -> Unit): Result<Unit> {
        val name = validateName(fileName).getOrElse { return Result.failure(it) }
        return try {
            block(name)
            val error = client.errorQueue.classifyLatest(fileName)
            if (error != null) Result.failure(ScpiException(error)) else Result.success(Unit)
        } catch (exception: ScpiException) {
            Result.failure(exception)
        }
    }

    companion object {
        private const val MAX_NAME_LENGTH = 255

        /** 拒否理由をアプリのエラー分類へ写す。 */
        fun toScopeError(throwable: Throwable): ScopeError = when (throwable) {
            is ScpiException -> throwable.error
            is IllegalArgumentException -> ScopeError.ArgumentOutOfRange("FILESystem", throwable.message)
            else -> ScopeError.Unknown(throwable.message, throwable)
        }
    }
}
