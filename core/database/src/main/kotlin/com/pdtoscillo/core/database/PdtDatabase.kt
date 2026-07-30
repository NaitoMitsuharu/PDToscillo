package com.pdtoscillo.core.database

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** 登録済み機器。 */
@Entity(tableName = "saved_instruments")
data class SavedInstrumentEntity(
    @PrimaryKey val id: String,
    val label: String,
    val host: String,
    val port: Int,
    val lastIdentity: String?,
    val lastConnectedAtEpochMillis: Long,
)

/** 接続履歴。 */
@Entity(tableName = "connection_history")
data class ConnectionHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val port: Int,
    val succeeded: Boolean,
    val identity: String?,
    val errorDetail: String?,
    val connectedAtEpochMillis: Long,
)

/** 測定セッション。 */
@Entity(tableName = "measurement_sessions")
data class MeasurementSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val instrumentModel: String?,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val iterationCount: Int,
    val failureCount: Int,
)

/**
 * 波形のメタデータ。
 *
 * **波形本体は入れない。** 10 M 点で数十 MB になるため、BLOB へ入れると DB が肥大化する。
 * 本体はアプリ専用ストレージへ置き、ここにはパス・サイズ・ハッシュだけを残す。
 */
@Entity(tableName = "waveform_metadata")
data class WaveformMetadataEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    val source: String,
    val filePath: String,
    val format: String,
    val sizeBytes: Long,
    val sha256: String,
    val pointCount: Int,
    /** プリアンブルの生応答。後から解釈し直せるように残す。 */
    val preambleRaw: String?,
    val capturedAtEpochMillis: Long,
)

/** 測定値のログ。 */
@Entity(tableName = "measurement_logs")
data class MeasurementLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long?,
    val measurementType: String,
    val source: String,
    val value: Double?,
    val unit: String?,
    val recordedAtEpochMillis: Long,
)

/** SCPI 実行履歴。 */
@Entity(tableName = "scpi_history")
data class ScpiHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val command: String,
    val kind: String,
    val responsePreview: String?,
    val responseByteCount: Long?,
    val responseSha256: String?,
    val errorDetail: String?,
    val elapsedMillis: Long,
    val executedAtEpochMillis: Long,
)

/** お気に入りコマンド。 */
@Entity(tableName = "favorite_commands")
data class FavoriteCommandEntity(@PrimaryKey val command: String, val addedAtEpochMillis: Long)

/** エラーログ。 */
@Entity(tableName = "error_logs")
data class ErrorLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val detail: String?,
    val command: String?,
    val occurredAtEpochMillis: Long,
)

@Dao
interface SavedInstrumentDao {
    @Query("SELECT * FROM saved_instruments ORDER BY lastConnectedAtEpochMillis DESC")
    fun observeAll(): Flow<List<SavedInstrumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SavedInstrumentEntity)

    @Query("DELETE FROM saved_instruments WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ConnectionHistoryDao {
    @Query("SELECT * FROM connection_history ORDER BY connectedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ConnectionHistoryEntity>>

    @Insert
    suspend fun insert(entity: ConnectionHistoryEntity)

    /** 古い履歴を消す。無制限に貯めない。 */
    @Query(
        "DELETE FROM connection_history WHERE id NOT IN " +
            "(SELECT id FROM connection_history ORDER BY connectedAtEpochMillis DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int = 200)
}

@Dao
interface MeasurementSessionDao {
    @Query("SELECT * FROM measurement_sessions ORDER BY startedAtEpochMillis DESC")
    fun observeAll(): Flow<List<MeasurementSessionEntity>>

    @Insert
    suspend fun insert(entity: MeasurementSessionEntity): Long

    @Query(
        "UPDATE measurement_sessions SET finishedAtEpochMillis = :finishedAt, iterationCount = :iterations, failureCount = :failures WHERE id = :id",
    )
    suspend fun finish(id: Long, finishedAt: Long, iterations: Int, failures: Int)
}

@Dao
interface WaveformMetadataDao {
    @Query("SELECT * FROM waveform_metadata ORDER BY capturedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 100): Flow<List<WaveformMetadataEntity>>

    @Query("SELECT * FROM waveform_metadata WHERE sessionId = :sessionId ORDER BY capturedAtEpochMillis")
    suspend fun forSession(sessionId: Long): List<WaveformMetadataEntity>

    @Insert
    suspend fun insert(entity: WaveformMetadataEntity): Long

    @Query("DELETE FROM waveform_metadata WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface MeasurementLogDao {
    @Query("SELECT * FROM measurement_logs WHERE sessionId = :sessionId ORDER BY recordedAtEpochMillis")
    suspend fun forSession(sessionId: Long): List<MeasurementLogEntity>

    @Insert
    suspend fun insertAll(entities: List<MeasurementLogEntity>)
}

@Dao
interface ScpiHistoryDao {
    @Query("SELECT * FROM scpi_history ORDER BY executedAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ScpiHistoryEntity>>

    @Insert
    suspend fun insert(entity: ScpiHistoryEntity)

    @Query(
        "DELETE FROM scpi_history WHERE id NOT IN " +
            "(SELECT id FROM scpi_history ORDER BY executedAtEpochMillis DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int = 1000)
}

@Dao
interface FavoriteCommandDao {
    @Query("SELECT * FROM favorite_commands ORDER BY addedAtEpochMillis")
    fun observeAll(): Flow<List<FavoriteCommandEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: FavoriteCommandEntity)

    @Query("DELETE FROM favorite_commands WHERE command = :command")
    suspend fun delete(command: String)
}

@Dao
interface ErrorLogDao {
    @Query("SELECT * FROM error_logs ORDER BY occurredAtEpochMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<ErrorLogEntity>>

    @Insert
    suspend fun insert(entity: ErrorLogEntity)

    @Query(
        "DELETE FROM error_logs WHERE id NOT IN " +
            "(SELECT id FROM error_logs ORDER BY occurredAtEpochMillis DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int = 500)
}

/**
 * ローカル保存。
 *
 * 保存するのは「メタデータと履歴」だけ。波形本体のような大きなデータは
 * アプリ専用ストレージのファイルへ置き、ここにはパスとハッシュを残す。
 */
@Database(
    entities = [
        SavedInstrumentEntity::class,
        ConnectionHistoryEntity::class,
        MeasurementSessionEntity::class,
        WaveformMetadataEntity::class,
        MeasurementLogEntity::class,
        ScpiHistoryEntity::class,
        FavoriteCommandEntity::class,
        ErrorLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PdtDatabase : RoomDatabase() {
    abstract fun savedInstrumentDao(): SavedInstrumentDao
    abstract fun connectionHistoryDao(): ConnectionHistoryDao
    abstract fun measurementSessionDao(): MeasurementSessionDao
    abstract fun waveformMetadataDao(): WaveformMetadataDao
    abstract fun measurementLogDao(): MeasurementLogDao
    abstract fun scpiHistoryDao(): ScpiHistoryDao
    abstract fun favoriteCommandDao(): FavoriteCommandDao
    abstract fun errorLogDao(): ErrorLogDao

    companion object {
        private const val DATABASE_NAME = "pdtoscillo.db"

        @Volatile
        private var instance: PdtDatabase? = null

        fun get(context: Context): PdtDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                PdtDatabase::class.java,
                DATABASE_NAME,
            ).build().also { instance = it }
        }
    }
}
