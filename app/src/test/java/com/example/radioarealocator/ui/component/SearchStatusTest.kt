package com.example.radioarealocator.ui.component

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SearchStatus] 单元测试。
 *
 * 覆盖状态机转换逻辑。
 */
class SearchStatusTest {

    @Test
    fun `isExpand returns true when EXPANDED`() {
        val status = SearchStatus(label = "test", current = SearchStatus.Status.EXPANDED)
        assertTrue(status.isExpand())
        assertFalse(status.isCollapsed())
    }

    @Test
    fun `isCollapsed returns true when COLLAPSED`() {
        val status = SearchStatus(label = "test", current = SearchStatus.Status.COLLAPSED)
        assertTrue(status.isCollapsed())
        assertFalse(status.isExpand())
    }

    @Test
    fun `shouldExpand returns true for EXPANDED and EXPANDING`() {
        assertTrue(
            SearchStatus(label = "test", current = SearchStatus.Status.EXPANDED).shouldExpand()
        )
        assertTrue(
            SearchStatus(label = "test", current = SearchStatus.Status.EXPANDING).shouldExpand()
        )
        assertFalse(
            SearchStatus(label = "test", current = SearchStatus.Status.COLLAPSED).shouldExpand()
        )
        assertFalse(
            SearchStatus(label = "test", current = SearchStatus.Status.COLLAPSING).shouldExpand()
        )
    }

    @Test
    fun `shouldCollapsed returns true for COLLAPSED and COLLAPSING`() {
        assertTrue(
            SearchStatus(label = "test", current = SearchStatus.Status.COLLAPSED).shouldCollapsed()
        )
        assertTrue(
            SearchStatus(label = "test", current = SearchStatus.Status.COLLAPSING).shouldCollapsed()
        )
        assertFalse(
            SearchStatus(label = "test", current = SearchStatus.Status.EXPANDED).shouldCollapsed()
        )
        assertFalse(
            SearchStatus(label = "test", current = SearchStatus.Status.EXPANDING).shouldCollapsed()
        )
    }

    @Test
    fun `isAnimatingExpand returns true only for EXPANDING`() {
        assertTrue(
            SearchStatus(label = "test", current = SearchStatus.Status.EXPANDING).isAnimatingExpand()
        )
        assertFalse(
            SearchStatus(label = "test", current = SearchStatus.Status.EXPANDED).isAnimatingExpand()
        )
    }

    @Test
    fun `onAnimationComplete transitions EXPANDING to EXPANDED`() {
        val status = SearchStatus(label = "test", current = SearchStatus.Status.EXPANDING)
        val result = status.onAnimationComplete()
        assertEquals(SearchStatus.Status.EXPANDED, result.current)
    }

    @Test
    fun `onAnimationComplete transitions COLLAPSING to COLLAPSED and clears text`() {
        val status = SearchStatus(
            label = "test",
            searchText = "hello",
            current = SearchStatus.Status.COLLAPSING
        )
        val result = status.onAnimationComplete()
        assertEquals(SearchStatus.Status.COLLAPSED, result.current)
        assertEquals("", result.searchText)
    }

    @Test
    fun `onAnimationComplete leaves EXPANDED unchanged`() {
        val status = SearchStatus(
            label = "test",
            searchText = "hello",
            current = SearchStatus.Status.EXPANDED
        )
        val result = status.onAnimationComplete()
        assertEquals(SearchStatus.Status.EXPANDED, result.current)
        assertEquals("hello", result.searchText)
    }

    @Test
    fun `onAnimationComplete leaves COLLAPSED unchanged`() {
        val status = SearchStatus(
            label = "test",
            searchText = "",
            current = SearchStatus.Status.COLLAPSED
        )
        val result = status.onAnimationComplete()
        assertEquals(SearchStatus.Status.COLLAPSED, result.current)
    }

    @Test
    fun `resultStatus defaults to DEFAULT`() {
        val status = SearchStatus(label = "test")
        assertEquals(SearchStatus.ResultStatus.DEFAULT, status.resultStatus)
    }

    @Test
    fun `resultStatus can be set to all values`() {
        SearchStatus.ResultStatus.entries.forEach { resultStatus ->
            val status = SearchStatus(label = "test", resultStatus = resultStatus)
            assertEquals(resultStatus, status.resultStatus)
        }
    }
}
