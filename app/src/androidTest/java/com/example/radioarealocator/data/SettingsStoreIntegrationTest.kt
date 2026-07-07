package com.example.radioarealocator.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SettingsStore] 集成测试。
 *
 * 验证 Bug #7 修复：satelliteSource 设置项在进程重启（或 Store 重建）后能正确恢复。
 * 使用真实 SharedPreferences，覆盖读写一致性与默认值回归。
 */
@RunWith(AndroidJUnit4::class)
class SettingsStoreIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPrefs() {
        // 每个测试前清空 SharedPreferences，避免相互污染
        context.getSharedPreferences("radio_area_settings", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun satelliteSource_defaultsToAll_whenNeverSet() {
        val store = SettingsStore(context)
        assertEquals("ALL", store.satelliteSource)
    }

    @Test
    fun satelliteSource_persistsAcrossStoreInstances() {
        // 模拟进程重启：先写入，再新建 Store 读取
        SettingsStore(context).satelliteSource = "CT"
        val restored = SettingsStore(context)
        assertEquals("CT", restored.satelliteSource)
    }

    @Test
    fun satelliteSource_overwriteAndRestore() {
        val first = SettingsStore(context)
        first.satelliteSource = "SNOGS"
        first.satelliteSource = "CT"
        assertEquals("CT", SettingsStore(context).satelliteSource)
    }

    @Test
    fun backgroundUri_defaultsToNull_whenNeverSet() {
        val store = SettingsStore(context)
        assertNull(store.backgroundUri)
    }

    @Test
    fun backgroundUri_persistsAcrossStoreInstances() {
        SettingsStore(context).backgroundUri = "content://media/external/images/1"
        val restored = SettingsStore(context)
        assertEquals("content://media/external/images/1", restored.backgroundUri)
    }

    @Test
    fun backgroundUri_clearWithNull() {
        val store = SettingsStore(context)
        store.backgroundUri = "content://media/external/images/2"
        store.backgroundUri = null
        assertNull(SettingsStore(context).backgroundUri)
    }

    @Test
    fun satelliteSource_andBackgroundUri_areIndependent() {
        val store = SettingsStore(context)
        store.satelliteSource = "SNOGS"
        store.backgroundUri = "content://x/1"
        // 修改一个不应影响另一个
        store.satelliteSource = "CT"
        val restored = SettingsStore(context)
        assertEquals("CT", restored.satelliteSource)
        assertEquals("content://x/1", restored.backgroundUri)
    }
}
