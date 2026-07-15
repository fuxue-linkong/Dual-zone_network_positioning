package com.example.radioarealocator.data.cw

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface CWProgressDao {
    @Insert
    suspend fun insert(progress: CWProgress): Long

    @Query("SELECT * FROM cw_progress ORDER BY completedAt DESC")
    fun getAllProgress(): Flow<List<CWProgress>>

    @Query("SELECT * FROM cw_progress WHERE courseId = :courseId ORDER BY lessonId")
    fun getProgressByCourse(courseId: Int): Flow<List<CWProgress>>

    @Query("SELECT * FROM cw_progress WHERE courseId = :courseId AND lessonId = :lessonId ORDER BY completedAt DESC LIMIT 1")
    suspend fun getLatestLessonProgress(courseId: Int, lessonId: Int): CWProgress?

    @Query("SELECT MAX(lessonId) FROM cw_progress WHERE courseId = :courseId AND accuracy >= 80")
    suspend fun getMaxCompletedLessonId(courseId: Int): Int?

    @Query("SELECT AVG(accuracy) FROM cw_progress")
    fun getAverageAccuracy(): Flow<Float?>

    @Query("SELECT SUM(duration) FROM cw_progress")
    fun getTotalPracticeTime(): Flow<Int?>

    @Query("SELECT * FROM cw_progress ORDER BY completedAt DESC LIMIT 1")
    fun getLatestProgress(): Flow<CWProgress?>

    @Query("SELECT COUNT(*) FROM cw_progress WHERE courseId = :courseId AND accuracy >= 80")
    suspend fun getCompletedLessonCount(courseId: Int): Int
}

@Database(entities = [CWProgress::class], version = 1)
abstract class CWProgressDatabase : RoomDatabase() {
    abstract fun cwProgressDao(): CWProgressDao

    companion object {
        @Volatile
        private var INSTANCE: CWProgressDatabase? = null

        fun getDatabase(context: Context): CWProgressDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CWProgressDatabase::class.java,
                    "cw_progress_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class CWProgressStore private constructor(private val dao: CWProgressDao) {

    constructor(context: Context) : this(CWProgressDatabase.getDatabase(context).cwProgressDao())

    internal constructor(context: Context, database: CWProgressDatabase) : this(database.cwProgressDao())

    val allProgress: Flow<List<CWProgress>> = dao.getAllProgress()
    val averageAccuracy: Flow<Float?> = dao.getAverageAccuracy()
    val totalPracticeTime: Flow<Int?> = dao.getTotalPracticeTime()
    val latestProgress: Flow<CWProgress?> = dao.getLatestProgress()

    fun getProgressByCourse(courseId: Int): Flow<List<CWProgress>> {
        return dao.getProgressByCourse(courseId)
    }

    suspend fun insertProgress(progress: CWProgress) {
        dao.insert(progress)
    }

    suspend fun getLatestLessonProgress(courseId: Int, lessonId: Int): CWProgress? {
        return dao.getLatestLessonProgress(courseId, lessonId)
    }

    suspend fun getMaxCompletedLessonId(courseId: Int): Int? {
        return dao.getMaxCompletedLessonId(courseId)
    }

    suspend fun getCompletedLessonCount(courseId: Int): Int {
        return dao.getCompletedLessonCount(courseId)
    }
}
