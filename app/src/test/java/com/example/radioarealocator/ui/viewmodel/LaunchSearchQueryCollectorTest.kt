package com.example.radioarealocator.ui.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [launchSearchQueryCollector] 单元测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LaunchSearchQueryCollectorTest {

    private val testDispatcher = StandardTestDispatcher()

    @Test
    fun `collects debounced query`() = runTest(testDispatcher) {
        val queryFlow = MutableStateFlow("")
        val results = mutableListOf<String>()

        launchSearchQueryCollector(queryFlow) { query ->
            results.add(query)
        }

        // Emit multiple values rapidly - only the last should pass debounce
        queryFlow.value = "a"
        queryFlow.value = "ab"
        queryFlow.value = "abc"

        advanceTimeBy(200) // Past debounce threshold (150ms)
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals("abc", results[0])
    }

    @Test
    fun `does not emit duplicate consecutive values`() = runTest(testDispatcher) {
        val queryFlow = MutableStateFlow("")
        val results = mutableListOf<String>()

        launchSearchQueryCollector(queryFlow) { query ->
            results.add(query)
        }

        queryFlow.value = "test"
        advanceTimeBy(200)
        advanceUntilIdle()

        queryFlow.value = "test" // Same value
        advanceTimeBy(200)
        advanceUntilIdle()

        // Only one emission because distinctUntilChanged
        assertEquals(1, results.size)
    }

    @Test
    fun `emits after debounce period`() = runTest(testDispatcher) {
        val queryFlow = MutableStateFlow("")
        val results = mutableListOf<String>()

        launchSearchQueryCollector(queryFlow) { query ->
            results.add(query)
        }

        queryFlow.value = "hello"
        advanceTimeBy(100) // Not past debounce
        advanceUntilIdle()

        assertEquals(0, results.size) // Too early

        advanceTimeBy(100) // Now past 150ms total
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals("hello", results[0])
    }

    @Test
    fun `empty query is collected`() = runTest(testDispatcher) {
        val queryFlow = MutableStateFlow("initial")
        val results = mutableListOf<String>()

        launchSearchQueryCollector(queryFlow) { query ->
            results.add(query)
        }

        queryFlow.value = ""
        advanceTimeBy(200)
        advanceUntilIdle()

        assertEquals(1, results.size)
        assertEquals("", results[0])
    }
}
