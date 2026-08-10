package com.example.radioarealocator.data.satellite

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

/**
 * 转发器频率本地缓存实体（Room）。
 *
 * @param uuid SatNOGS API 记录主键
 * @param fetchedAt 本条数据写入时间（epoch millis），用于判断缓存新鲜度
 * @param status 转发器状态（active/inactive/future/unknown），v2 新增
 * @param description 转发器描述（如 "FM Voice"），v2 新增
 */
@Entity(tableName = "radio_info")
data class RadioInfoEntity(
    @PrimaryKey
    @ColumnInfo(name = "uuid")
    val uuid: String,
    @ColumnInfo(name = "norad_cat_id")
    val noradCatId: Int,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "uplink_hz")
    val uplinkHz: Long?,
    @ColumnInfo(name = "downlink_hz")
    val downlinkHz: Long?,
    @ColumnInfo(name = "mode")
    val mode: String,
    @ColumnInfo(name = "inverted")
    val inverted: Boolean,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "fetched_at")
    val fetchedAt: Long,
)

@Dao
interface RadioInfoDao {
    @Query("SELECT * FROM radio_info WHERE norad_cat_id = :noradCatId")
    suspend fun getByNorad(noradCatId: Int): List<RadioInfoEntity>

    @Query("SELECT * FROM radio_info")
    suspend fun getAll(): List<RadioInfoEntity>

    @Query("SELECT MAX(fetched_at) FROM radio_info")
    suspend fun lastFetchedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<RadioInfoEntity>)

    @Query("DELETE FROM radio_info")
    suspend fun clear()
}

/**
 * 转发器频率缓存数据库（单表 [RadioInfoEntity]）。
 * 缓存最近一次拉取结果，离线时由 [RadioInfoRepository] 回退使用。
 *
 * v1 → v2：新增 status / description 列（旧缓存通过迁移保留或重建）。
 */
@Database(
    entities = [RadioInfoEntity::class],
    version = 2,
    exportSchema = false
)
abstract class TransmitterDatabase : RoomDatabase() {
    abstract fun radioInfoDao(): RadioInfoDao

    companion object {
        @Volatile
        private var INSTANCE: TransmitterDatabase? = null

        fun getDatabase(context: Context): TransmitterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransmitterDatabase::class.java,
                    "radio_info_database"
                )
                    // v1→v2 新增 status/description 列；转发器数据是低价值缓存，
                    // 直接用重建迁移，避免手写 SQL 迁移的维护成本
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
