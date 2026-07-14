package com.example.radioarealocator.data.cw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CWProgressStoreTest {
    @Test
    fun `test CWProgress construction`() {
        val progress = CWProgress(
            id = 1,
            courseId = 1,
            lessonId = 2,
            completedAt = 1700000000000L,
            accuracy = 0.85f,
            wpm = 15,
            duration = 300
        )
        assertEquals(1L, progress.id)
        assertEquals(1, progress.courseId)
        assertEquals(2, progress.lessonId)
        assertEquals(1700000000000L, progress.completedAt)
        assertEquals(0.85f, progress.accuracy, 0.001f)
        assertEquals(15, progress.wpm)
        assertEquals(300, progress.duration)
    }

    @Test
    fun `test CWProgress default id is zero`() {
        val progress = CWProgress(
            courseId = 1,
            lessonId = 1,
            completedAt = 1700000000000L,
            accuracy = 0.9f,
            wpm = 20,
            duration = 600
        )
        assertEquals(0L, progress.id)
    }

    @Test
    fun `test CWProgress copy preserves unmodified fields`() {
        val progress = CWProgress(
            courseId = 1,
            lessonId = 2,
            completedAt = 1700000000000L,
            accuracy = 0.75f,
            wpm = 15,
            duration = 300
        )
        val updated = progress.copy(accuracy = 0.95f)
        assertEquals(0.95f, updated.accuracy, 0.001f)
        assertEquals(progress.courseId, updated.courseId)
        assertEquals(progress.lessonId, updated.lessonId)
        assertEquals(progress.wpm, updated.wpm)
        assertEquals(progress.duration, updated.duration)
    }

    @Test
    fun `test CWProgress equality`() {
        val progress1 = CWProgress(
            id = 1,
            courseId = 1,
            lessonId = 2,
            completedAt = 1700000000000L,
            accuracy = 0.85f,
            wpm = 15,
            duration = 300
        )
        val progress2 = CWProgress(
            id = 1,
            courseId = 1,
            lessonId = 2,
            completedAt = 1700000000000L,
            accuracy = 0.85f,
            wpm = 15,
            duration = 300
        )
        assertEquals(progress1, progress2)
    }

    @Test
    fun `test CWProgress with boundary accuracy values`() {
        val zeroAccuracy = CWProgress(
            courseId = 1, lessonId = 1, completedAt = 0L,
            accuracy = 0.0f, wpm = 5, duration = 60
        )
        val fullAccuracy = CWProgress(
            courseId = 1, lessonId = 1, completedAt = 0L,
            accuracy = 1.0f, wpm = 40, duration = 3600
        )
        assertTrue(zeroAccuracy.accuracy >= 0.0f)
        assertTrue(fullAccuracy.accuracy <= 1.0f)
    }
}
