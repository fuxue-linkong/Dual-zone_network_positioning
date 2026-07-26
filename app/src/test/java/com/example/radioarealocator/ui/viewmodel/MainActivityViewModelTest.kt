package com.example.radioarealocator.ui.viewmodel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [MainActivityViewModel] 单元测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class MainActivityViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: MainActivityViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Initialize the global reference used by ViewModels
        com.example.radioarealocator.radioApp =
            com.example.radioarealocator.RadioAreaLocatorApplication()
        (com.example.radioarealocator.radioApp as com.example.radioarealocator.RadioAreaLocatorApplication)
            .onCreate()
        viewModel = MainActivityViewModel(SavedStateHandle())
        advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState has valid app settings`() = runTest {
        val state = viewModel.uiState.first()
        assertNotNull(state.appSettings)
        assertEquals(1.0f, state.pageScale, 0.001f)
    }

    @Test
    fun `selectedMainPage starts at 0`() = runTest {
        val page = viewModel.selectedMainPage.first()
        assertEquals(0, page)
    }

    @Test
    fun `setSelectedMainPage updates selected page`() = runTest {
        viewModel.setSelectedMainPage(1)
        advanceUntilIdle()

        val page = viewModel.selectedMainPage.first()
        assertEquals(1, page)
    }

    @Test
    fun `setSelectedMainPage clamps to valid range`() = runTest {
        viewModel.setSelectedMainPage(5)
        advanceUntilIdle()

        val page = viewModel.selectedMainPage.first()
        assertEquals(1, page) // Clamped to LAST_PAGE_INDEX

        viewModel.setSelectedMainPage(-1)
        advanceUntilIdle()

        val page2 = viewModel.selectedMainPage.first()
        assertEquals(0, page2) // Clamped to 0
    }

    @Test
    fun `uiState reflects preference changes`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putFloat("page_scale", 1.5f)
            .commit()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertEquals(1.5f, state.pageScale, 0.001f)
    }
}
