package com.example.radioarealocator.data.cw

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class CWProgressStoreTest {

    private lateinit var context: Context
    private lateinit var db: CWProgressDatabase
    private lateinit var store: CWProgressStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 使用内存数据库，每个测试独立、不落盘
        db = Room.inMemoryDatabaseBuilder(context, CWProgressDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // 重置 Room 单例，避免测试间数据污染
        val field = CWProgressDatabase::class.java.getDeclaredField("INSTANCE")
        field.isAccessible = true
        field.set(null, null)
        store = CWProgressStore(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun createProgress(
        courseId: Int = 1,
        lessonId: Int = 1,
        completedAt: Long = System.currentTimeMillis(),
        accuracy: Float = 0.85f,
        wpm: Int = 15,
        duration: Int = 300
    ) = CWProgress(
        courseId = courseId,
        lessonId = lessonId,
        completedAt = completedAt,
        accuracy = accuracy,
        wpm = wpm,
        duration = duration
    )

    @Test
    fun `insertProgress and allProgress returns inserted items`() = runBlocking {
        val progress = createProgress(courseId = 1, lessonId = 1, accuracy = 0.9f, wpm = 20)
        store.insertProgress(progress)

        val all = store.allProgress.first()
        assertEquals(1, all.size)
        assertEquals(1, all[0].courseId)
        assertEquals(1, all[0].lessonId)
        assertEquals(0.9f, all[0].accuracy, 0.001f)
        assertEquals(20, all[0].wpm)
        assertTrue(all[0].id > 0) // autoGenerate ID
    }

    @Test
    fun `getProgressByCourse filters by courseId`() = runBlocking {
        store.insertProgress(createProgress(courseId = 1, lessonId = 1))
        store.insertProgress(createProgress(courseId = 1, lessonId = 2))
        store.insertProgress(createProgress(courseId = 2, lessonId = 1))

        val course1 = store.getProgressByCourse(1).first()
        val course2 = store.getProgressByCourse(2).first()

        assertEquals(2, course1.size)
        assertEquals(1, course2.size)
        assertTrue(course1.all { it.courseId == 1 })
        assertTrue(course2.all { it.courseId == 2 })
    }

    @Test
    fun `averageAccuracy returns correct average`() = runBlocking {
        store.insertProgress(createProgress(accuracy = 0.8f))
        store.insertProgress(createProgress(accuracy = 0.6f))

        val avg = store.averageAccuracy.first()
        assertNotNull(avg)
        assertEquals(0.7f, avg!!, 0.001f)
    }

    @Test
    fun `averageAccuracy returns null when no data`() = runBlocking {
        val avg = store.averageAccuracy.take(1).first()
        assertNull(avg)
    }

    @Test
    fun `totalPracticeTime returns sum of durations`() = runBlocking {
        store.insertProgress(createProgress(duration = 300))
        store.insertProgress(createProgress(duration = 600))

        val total = store.totalPracticeTime.first()
        assertNotNull(total)
        assertEquals(900, total!!)
    }

    @Test
    fun `totalPracticeTime returns null when no data`() = runBlocking {
        val total = store.totalPracticeTime.take(1).first()
        assertNull(total)
    }

    @Test
    fun `latestProgress returns most recent by completedAt`() = runBlocking {
        store.insertProgress(createProgress(completedAt = 1000L, wpm = 10))
        store.insertProgress(createProgress(completedAt = 3000L, wpm = 25))
        store.insertProgress(createProgress(completedAt = 2000L, wpm = 15))

        val latest = store.latestProgress.first()
        assertNotNull(latest)
        assertEquals(3000L, latest!!.completedAt)
        assertEquals(25, latest.wpm)
    }

    @Test
    fun `latestProgress returns null when no data`() = runBlocking {
        val latest = store.latestProgress.take(1).first()
        assertNull(latest)
    }
}
