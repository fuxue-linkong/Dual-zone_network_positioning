package com.example.radioarealocator.ui.viewmodel

import com.example.radioarealocator.ui.component.SearchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [searchLoadingStatusFor] 与 [searchResultStatusFor] 单元测试。
 */
class SearchViewModelHelperTest {

    // ---- searchLoadingStatusFor ----

    @Test
    fun `searchLoadingStatusFor returns DEFAULT for empty text`() {
        assertEquals(SearchStatus.ResultStatus.DEFAULT, searchLoadingStatusFor(""))
    }

    @Test
    fun `searchLoadingStatusFor returns LOAD for non-empty text`() {
        assertEquals(SearchStatus.ResultStatus.LOAD, searchLoadingStatusFor("a"))
        assertEquals(SearchStatus.ResultStatus.LOAD, searchLoadingStatusFor("test"))
        assertEquals(SearchStatus.ResultStatus.LOAD, searchLoadingStatusFor(" "))
    }

    // ---- searchResultStatusFor ----

    @Test
    fun `searchResultStatusFor returns DEFAULT when text is empty`() {
        assertEquals(SearchStatus.ResultStatus.DEFAULT, searchResultStatusFor("", true))
        assertEquals(SearchStatus.ResultStatus.DEFAULT, searchResultStatusFor("", false))
    }

    @Test
    fun `searchResultStatusFor returns EMPTY when text non-empty and results empty`() {
        assertEquals(SearchStatus.ResultStatus.EMPTY, searchResultStatusFor("test", true))
    }

    @Test
    fun `searchResultStatusFor returns SHOW when text non-empty and results non-empty`() {
        assertEquals(SearchStatus.ResultStatus.SHOW, searchResultStatusFor("test", false))
    }

    @Test
    fun `searchResultStatusFor prioritizes empty text over empty results`() {
        // Even if isEmpty is false, empty text returns DEFAULT
        assertEquals(SearchStatus.ResultStatus.DEFAULT, searchResultStatusFor("", false))
    }
}
