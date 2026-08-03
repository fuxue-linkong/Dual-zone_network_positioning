package com.example.radioarealocator.data.aprs

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "aprs_messages")
data class AprsMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "source")
    val source: String,
    @ColumnInfo(name = "destination")
    val destination: String,
    @ColumnInfo(name = "body")
    val body: String,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "is_outgoing")
    val isOutgoing: Boolean = false,
    @ColumnInfo(name = "is_read")
    val isRead: Boolean = false,
    @ColumnInfo(name = "msg_number")
    val msgNumber: String? = null,
    @ColumnInfo(name = "is_acknowledged")
    val isAcknowledged: Boolean = false,
    @ColumnInfo(name = "status")
    val status: Int = 0,
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0
)

@Entity(tableName = "aprs_stations")
data class AprsStationEntity(
    @PrimaryKey
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val symbolTable: String = "/",
    val symbolCode: String = ">",
    val comment: String = "",
    val altitude: Double? = null,
    val course: Int? = null,
    val speed: Int? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface AprsMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: AprsMessageEntity): Long

    @Update
    suspend fun update(message: AprsMessageEntity)

    @Query("SELECT * FROM aprs_messages ORDER BY timestamp DESC")
    fun getAllMessages(): Flow<List<AprsMessageEntity>>

    @Query("SELECT * FROM aprs_messages WHERE source = :callsign OR destination = :callsign ORDER BY timestamp ASC")
    fun getConversation(callsign: String): Flow<List<AprsMessageEntity>>

    @Query("SELECT * FROM aprs_messages WHERE is_outgoing = 0 AND is_read = 0 ORDER BY timestamp DESC")
    fun getUnreadMessages(): Flow<List<AprsMessageEntity>>

    @Query("SELECT COUNT(*) FROM aprs_messages WHERE is_outgoing = 0 AND is_read = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("UPDATE aprs_messages SET is_read = 1 WHERE source = :callsign AND is_outgoing = 0")
    suspend fun markConversationRead(callsign: String)

    @Query("DELETE FROM aprs_messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldMessages(beforeTimestamp: Long)

    @Query("SELECT DISTINCT CASE WHEN is_outgoing = 1 THEN destination ELSE source END as callsign FROM aprs_messages ORDER BY timestamp DESC")
    fun getConversationPartners(): Flow<List<String>>

    @Query("SELECT * FROM aprs_messages WHERE is_outgoing = 1 AND status IN (0, 3) ORDER BY timestamp ASC")
    suspend fun getPendingOutgoingMessages(): List<AprsMessageEntity>

    @Query("UPDATE aprs_messages SET status = :status, retry_count = :retryCount WHERE id = :id")
    suspend fun updateMessageStatus(id: Long, status: Int, retryCount: Int)

    @Query("UPDATE aprs_messages SET status = 2, is_acknowledged = 1 WHERE is_outgoing = 1 AND msg_number = :msgNumber AND destination = :peerCallsign")
    suspend fun markAcknowledged(peerCallsign: String, msgNumber: String)
}

@Dao
interface AprsStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(station: AprsStationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stations: List<AprsStationEntity>)

    @Query("SELECT * FROM aprs_stations ORDER BY timestamp DESC")
    fun getAllStations(): Flow<List<AprsStationEntity>>

    @Query("SELECT * FROM aprs_stations WHERE callsign = :callsign LIMIT 1")
    suspend fun getStation(callsign: String): AprsStationEntity?

    @Query("SELECT * FROM aprs_stations WHERE timestamp > :since ORDER BY timestamp DESC")
    fun getRecentStations(since: Long): Flow<List<AprsStationEntity>>

    @Query("DELETE FROM aprs_stations WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldStations(beforeTimestamp: Long)

    @Query("SELECT COUNT(*) FROM aprs_stations")
    suspend fun getStationCount(): Int
}

@Database(
    entities = [AprsMessageEntity::class, AprsStationEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AprsDatabase : RoomDatabase() {
    abstract fun messageDao(): AprsMessageDao
    abstract fun stationDao(): AprsStationDao

    companion object {
        @Volatile
        private var INSTANCE: AprsDatabase? = null

        fun getDatabase(context: Context): AprsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AprsDatabase::class.java,
                    "aprs_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class AprsMessageStore private constructor(
    private val messageDao: AprsMessageDao,
    private val stationDao: AprsStationDao
) {
    constructor(context: Context) : this(
        AprsDatabase.getDatabase(context).messageDao(),
        AprsDatabase.getDatabase(context).stationDao()
    )

    suspend fun insertMessage(message: AprsMessage): Long {
        return messageDao.insert(message.toEntity())
    }

    suspend fun updateMessage(message: AprsMessage) {
        messageDao.update(message.toEntity())
    }

    fun getAllMessages(): Flow<List<AprsMessage>> = messageDao.getAllMessages().map { list ->
        list.map { it.toModel() }
    }

    fun getConversation(callsign: String): Flow<List<AprsMessage>> =
        messageDao.getConversation(callsign).map { list ->
            list.map { it.toModel() }
        }

    fun getUnreadMessages(): Flow<List<AprsMessage>> =
        messageDao.getUnreadMessages().map { list ->
            list.map { it.toModel() }
        }

    fun getUnreadCount(): Flow<Int> = messageDao.getUnreadCount()

    suspend fun markConversationRead(callsign: String) = messageDao.markConversationRead(callsign)

    suspend fun deleteOldMessages(maxAgeMillis: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        messageDao.deleteOldMessages(cutoff)
    }

    fun getConversationPartners(): Flow<List<String>> = messageDao.getConversationPartners()

    suspend fun getPendingOutgoingMessages(): List<AprsMessage> =
        messageDao.getPendingOutgoingMessages().map { it.toModel() }

    suspend fun updateMessageStatus(id: Long, status: Int, retryCount: Int) {
        messageDao.updateMessageStatus(id, status, retryCount)
    }

    suspend fun markAcknowledged(peerCallsign: String, msgNumber: String) {
        messageDao.markAcknowledged(peerCallsign, msgNumber)
    }

    suspend fun insertStation(station: AprsStation) {
        stationDao.insert(station.toEntity())
    }

    fun getAllStations(): Flow<List<AprsStation>> = stationDao.getAllStations().map { list ->
        list.map { it.toModel() }
    }

    fun getRecentStations(maxAgeMillis: Long): Flow<List<AprsStation>> {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        return stationDao.getRecentStations(cutoff).map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun getStation(callsign: String): AprsStation? =
        stationDao.getStation(callsign)?.toModel()

    suspend fun deleteOldStations(maxAgeMillis: Long) {
        val cutoff = System.currentTimeMillis() - maxAgeMillis
        stationDao.deleteOldStations(cutoff)
    }

    suspend fun getStationCount(): Int = stationDao.getStationCount()

    private fun AprsMessageEntity.toModel() = AprsMessage(
        id = id,
        source = source,
        destination = destination,
        body = body,
        timestamp = timestamp,
        isOutgoing = isOutgoing,
        isRead = isRead,
        msgNumber = msgNumber,
        isAcknowledged = isAcknowledged,
        status = status,
        retryCount = retryCount
    )

    private fun AprsMessage.toEntity() = AprsMessageEntity(
        id = id,
        source = source,
        destination = destination,
        body = body,
        timestamp = timestamp,
        isOutgoing = isOutgoing,
        isRead = isRead,
        msgNumber = msgNumber,
        isAcknowledged = isAcknowledged,
        status = status,
        retryCount = retryCount
    )

    private fun AprsStationEntity.toModel() = AprsStation(
        callsign = callsign,
        latitude = latitude,
        longitude = longitude,
        symbolTable = symbolTable.firstOrNull() ?: '/',
        symbolCode = symbolCode.firstOrNull() ?: '>',
        comment = comment,
        altitude = altitude,
        course = course,
        speed = speed,
        timestamp = timestamp
    )

    private fun AprsStation.toEntity() = AprsStationEntity(
        callsign = callsign,
        latitude = latitude,
        longitude = longitude,
        symbolTable = symbolTable.toString(),
        symbolCode = symbolCode.toString(),
        comment = comment,
        altitude = altitude,
        course = course,
        speed = speed,
        timestamp = timestamp
    )
}
