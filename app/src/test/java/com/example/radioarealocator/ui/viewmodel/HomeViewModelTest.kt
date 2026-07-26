package com.example.radioarealocator.ui.viewmodel

import android.content.Context
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [HomeViewModel] 单元测试。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Initialize the global reference used by ViewModels
        com.example.radioarealocator.radioApp =
            com.example.radioarealocator.RadioAreaLocatorApplication()
        (com.example.radioarealocator.radioApp as com.example.radioarealocator.RadioAreaLocatorApplication)
            .onCreate()
        viewModel = HomeViewModel()
        advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state has valid app version`() = runTest {
        val state = viewModel.uiState.first()
        assertTrue(state.currentAppVersionCode > 0)
    }

    @Test
    fun `initial state has latestVersionInfo`() = runTest {
        val state = viewModel.uiState.first()
        // LatestVersionInfo defaults are fine; just verify it exists
        assertTrue(state.latestVersionInfo.versionCode >= 0)
    }

    @Test
    fun `refresh updates state`() = runTest {
        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.first()
        assertTrue(state.currentAppVersionCode > 0)
    }

    @Test
    fun `checkUpdateEnabled reflects preference`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("check_update", false)
            .commit()

        // Create new viewModel to pick up the preference
        val newViewModel = HomeViewModel()
        advanceUntilIdle()

        val state = newViewModel.uiState.first()
        assertEquals(false, state.checkUpdateEnabled)
    }
}
