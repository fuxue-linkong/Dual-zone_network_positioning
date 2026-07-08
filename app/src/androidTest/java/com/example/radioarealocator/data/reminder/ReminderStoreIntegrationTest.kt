package com.example.radioarealocator.data.reminder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [ReminderStore] 集成测试。
 *
 * 使用真实 [Context]（ApplicationProvider）而非 Mock，
 * 验证 SharedPreferences + JSON 序列化的端到端正确性。
 *
 * 覆盖：
 * - 设置默认值与持久化
 * - 提醒项 CRUD（增删改查）
 * - JSON 序列化/反序列化往返一致性
 * - 进程重启模拟（重新实例化 Store 后状态可恢复）
 * - 损坏 JSON 的容错处理
 */
@RunWith(AndroidJUnit4::class)
class ReminderStoreIntegrationTest {

    private lateinit var context: Context
    private lateinit var store: ReminderStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // 清除可能残留的旧数据，避免测试间相互影响
        context.getSharedPreferences("radio_area_reminders", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = ReminderStore(context)
    }

    @After
    fun tearDown() {
        context.getSharedPreferences("radio_area_reminders", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun defaultSettings_areEnabledWith10MinutesLeadTime() {
        val settings = store.loadSettings()
        assertTrue(settings.enabled)
        assertEquals(10, settings.leadMinutes)
        assertEquals(RepeatMode.ALWAYS, settings.repeatMode)
        assertTrue(settings.soundEnabled)
        assertTrue(settings.vibrationEnabled)
    }

    @Test
    fun savedSettings_areRestoredOnNextLoad() {
        val custom = ReminderSettings(
            enabled = false,
            leadMinutes = 30,
            repeatMode = RepeatMode.DAYTIME_ONLY,
            soundEnabled = false,
            vibrationEnabled = true
        )
        store.saveSettings(custom)

        val loaded = store.loadSettings()
        assertEquals(custom, loaded)
    }

    @Test
    fun settings_surviveStoreRecreation_simulatingProcessRestart() {
        store.saveSettings(ReminderSettings(enabled = false, leadMinutes = 15))
        // 模拟进程重启：重新实例化 Store
        val newStore = ReminderStore(context)
        val loaded = newStore.loadSettings()
        assertFalse(loaded.enabled)
        assertEquals(15, loaded.leadMinutes)
    }

    @Test
    fun emptyItemsList_whenNothingSaved() {
        assertEquals(emptyList<ReminderItem>(), store.loadItems())
    }

    @Test
    fun upsertItem_addsNewItem() {
        store.upsertItem(sampleItem(catalogNumber = 25544, name = "ISS"))

        val items = store.loadItems()
        assertEquals(1, items.size)
        assertEquals("ISS", items[0].name)
        assertEquals(25544, items[0].catalogNumber)
    }

    @Test
    fun upsertItem_updatesExistingItemByCatalogNumber() {
        store.upsertItem(sampleItem(catalogNumber = 25544, name = "ISS", maxElevation = 30.0))
        store.upsertItem(sampleItem(catalogNumber = 25544, name = "ISS (Zarya)", maxElevation = 45.0))

        val items = store.loadItems()
        assertEquals(1, items.size)
        assertEquals("ISS (Zarya)", items[0].name)
        assertEquals(45.0, items[0].maxElevation, 0.01)
    }

    @Test
    fun items_areSortedByAosTimeAscending() {
        store.upsertItem(sampleItem(catalogNumber = 1, aosTimeMillis = 3000L))
        store.upsertItem(sampleItem(catalogNumber = 2, aosTimeMillis = 1000L))
        store.upsertItem(sampleItem(catalogNumber = 3, aosTimeMillis = 2000L))

        val items = store.loadItems()
        assertEquals(listOf(2, 3, 1), items.map { it.catalogNumber })
    }

    @Test
    fun removeItem_deletesOnlyMatchingCatalogNumber() {
        store.upsertItem(sampleItem(catalogNumber = 1))
        store.upsertItem(sampleItem(catalogNumber = 2))
        store.upsertItem(sampleItem(catalogNumber = 3))

        store.removeItem(2)

        val items = store.loadItems()
        assertEquals(setOf(1, 3), items.map { it.catalogNumber }.toSet())
    }

    @Test
    fun setItemEnabled_togglesFlag_withoutLosingOtherFields() {
        store.upsertItem(sampleItem(catalogNumber = 25544, name = "ISS", enabled = true))

        val updated = store.setItemEnabled(25544, false)
        assertEquals(1, updated.size)
        assertFalse(updated[0].enabled)
        // 其他字段保持不变
        assertEquals("ISS", updated[0].name)

        // 重新加载验证持久化
        val reloaded = store.loadItems()
        assertFalse(reloaded[0].enabled)
    }

    @Test
    fun modesList_survivesSerializationRoundtrip() {
        val item = sampleItem(catalogNumber = 25544, modes = listOf("FM", "SSTV", "DSTAR"))
        store.upsertItem(item)

        val loaded = store.loadItems()
        assertEquals(listOf("FM", "SSTV", "DSTAR"), loaded[0].modes)
    }

    @Test
    fun emptyModesList_survivesSerializationRoundtrip() {
        val item = sampleItem(catalogNumber = 25544, modes = emptyList())
        store.upsertItem(item)

        val loaded = store.loadItems()
        assertTrue(loaded[0].modes.isEmpty())
    }

    @Test
    fun corruptedJson_returnsEmptyList_withoutThrowing() {
        // 直接写入损坏的 JSON
        context.getSharedPreferences("radio_area_reminders", Context.MODE_PRIVATE)
            .edit().putString("items", "{ broken json").commit()

        val items = store.loadItems()
        assertEquals(emptyList<ReminderItem>(), items)
    }

    @Test
    fun saveItems_overwritesPreviousListCompletely() {
        store.upsertItem(sampleItem(catalogNumber = 1))
        store.upsertItem(sampleItem(catalogNumber = 2))

        // 全量覆盖为单个项
        store.saveItems(listOf(sampleItem(catalogNumber = 3)))

        val items = store.loadItems()
        assertEquals(1, items.size)
        assertEquals(3, items[0].catalogNumber)
    }

    @Test
    fun items_surviveStoreRecreation_simulatingProcessRestart() {
        store.upsertItem(sampleItem(catalogNumber = 25544, name = "ISS"))
        store.upsertItem(sampleItem(catalogNumber = 40069, name = "NOAA 19"))

        // 模拟进程重启
        val newStore = ReminderStore(context)
        val items = newStore.loadItems()
        assertEquals(2, items.size)
        val names = items.map { it.name }.toSet()
        assertTrue("ISS" in names)
        assertTrue("NOAA 19" in names)
    }

    @Test
    fun repeatMode_fromName_returnsDefaultForInvalidValue() {
        assertEquals(RepeatMode.ALWAYS, RepeatMode.fromName(null))
        assertEquals(RepeatMode.ALWAYS, RepeatMode.fromName("INVALID"))
        assertEquals(RepeatMode.DAYTIME_ONLY, RepeatMode.fromName("DAYTIME_ONLY"))
    }

    private fun sampleItem(
        catalogNumber: Int = 25544,
        name: String = "TestSat",
        aosTimeMillis: Long = System.currentTimeMillis() + 3600_000L,
        maxElevation: Double = 45.0,
        modes: List<String> = listOf("FM"),
        enabled: Boolean = true
    ): ReminderItem {
        return ReminderItem(
            catalogNumber = catalogNumber,
            name = name,
            aosTimeMillis = aosTimeMillis,
            losTimeMillis = aosTimeMillis + 600_000L,
            maxElevation = maxElevation,
            aosAzimuth = 120,
            losAzimuth = 280,
            modes = modes,
            enabled = enabled
        )
    }
}
