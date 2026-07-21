package com.example.radioarealocator.ui

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.radioarealocator.RadioAreaLocatorApplication
import com.example.radioarealocator.data.SettingsStore
import com.example.radioarealocator.data.satellite.FavoriteSatellitesStore
import com.example.radioarealocator.radioApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [MainViewModel] 与持久化层的集成测试。
 *
 * 验证 Bug #7 修复：ViewModel 销毁重建后，satelliteSource 与 favorites 能从
 * [SettingsStore] / [FavoriteSatellitesStore] 正确恢复。
 *
 * 注意：本测试不触发网络/定位请求，仅验证状态恢复路径。
 */
@RunWith(AndroidJUnit4::class)
class MainViewModelIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before
    fun clearPrefs() {
        // MainViewModel 现通过全局 radioApp 访问上下文，需先初始化
        radioApp = context as RadioAreaLocatorApplication
        context.getSharedPreferences("radio_area_settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        context.getSharedPreferences("radio_area_favorites", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun satelliteSource_restoredFromSettingsStore_onViewModelRecreation() {
        // 第一次 ViewModel 实例：设置 satelliteSource
        val first = MainViewModel()
        first.setSatelliteSource("CT")
        assertEquals("CT", first.satelliteSource.value)

        // 模拟进程重启：新建 ViewModel 应从 SettingsStore 恢复
        val restored = MainViewModel()
        assertEquals("CT", restored.satelliteSource.value)
    }

    @Test
    fun satelliteSource_defaultAll_whenNeverSet() {
        val vm = MainViewModel()
        assertEquals("ALL", vm.satelliteSource.value)
    }

    @Test
    fun satelliteSource_switchAndRestore() {
        val first = MainViewModel()
        first.setSatelliteSource("SNOGS")
        first.setSatelliteSource("CT")

        val restored = MainViewModel()
        assertEquals("CT", restored.satelliteSource.value)
    }

    @Test
    fun favorites_restoredFromStore_onViewModelRecreation() {
        // 第一次 ViewModel 实例：切换关注
        val first = MainViewModel()
        first.toggleFavorite(25544)
        first.toggleFavorite(43013)
        assertEquals(setOf(25544, 43013), first.favoriteSatellites.value)

        // 模拟进程重启：新建 ViewModel 应从 FavoriteSatellitesStore 恢复
        val restored = MainViewModel()
        assertEquals(setOf(25544, 43013), restored.favoriteSatellites.value)
    }

    @Test
    fun favorites_toggleOffAndRestore() {
        val first = MainViewModel()
        first.toggleFavorite(25544) // 添加
        first.toggleFavorite(25544) // 移除

        val restored = MainViewModel()
        assertTrue("toggle 关闭后应恢复为空", restored.favoriteSatellites.value.isEmpty())
    }

    @Test
    fun setSatelliteSource_writesToSettingsStore_directly() {
        // ViewModel 应通过 SettingsStore 持久化，可直接验证 Store
        val vm = MainViewModel()
        vm.setSatelliteSource("SNOGS")

        val store = SettingsStore(context)
        assertEquals("SNOGS", store.satelliteSource)
    }

    @Test
    fun toggleFavorite_writesToFavoriteStore_directly() {
        val vm = MainViewModel()
        vm.toggleFavorite(25544)

        val store = FavoriteSatellitesStore(context)
        assertTrue(25544 in store.load())
    }
}
