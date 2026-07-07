package com.example.radioarealocator.data.satellite

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [FavoriteSatellitesStore] 集成测试。
 *
 * 验证 Bug #3 修复：toggle() 在并发场景下不丢失修改。
 * 使用真实 SharedPreferences，覆盖单线程读写一致性与多线程原子性。
 */
@RunWith(AndroidJUnit4::class)
class FavoriteSatellitesStoreIntegrationTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("radio_area_favorites", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun load_returnsEmptySet_byDefault() {
        val store = FavoriteSatellitesStore(context)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun save_andLoad_roundtrip() {
        val store = FavoriteSatellitesStore(context)
        store.save(setOf(25544, 43013, 99999))
        val loaded = store.load()
        assertEquals(setOf(25544, 43013, 99999), loaded)
    }

    @Test
    fun toggle_addsWhenAbsent() {
        val store = FavoriteSatellitesStore(context)
        val result = store.toggle(25544)
        assertTrue(25544 in result)
        assertTrue(25544 in store.load())
    }

    @Test
    fun toggle_removesWhenPresent() {
        val store = FavoriteSatellitesStore(context)
        store.toggle(25544)
        val result = store.toggle(25544)
        assertFalse(25544 in result)
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun toggle_persistsAcrossStoreInstances() {
        // 模拟进程重启：先 toggle，再新建 Store 读取
        FavoriteSatellitesStore(context).toggle(25544)
        val restored = FavoriteSatellitesStore(context)
        assertTrue(25544 in restored.load())
    }

    /**
     * 验证 Bug #3 修复：多线程并发 toggle 不同卫星编号不丢失。
     *
     * 修复前（无 @Synchronized）：并发 load-modify-save 会导致部分修改丢失，
     * 最终集合大小可能小于预期。
     * 修复后（@Synchronized）：所有 toggle 串行执行，最终集合包含所有添加的编号。
     */
    @Test
    fun toggle_concurrentDifferentSatellites_noLoss() = runBlocking {
        val store = FavoriteSatellitesStore(context)
        val catalogNumbers = (1..50).toList()

        // 50 个协程并发 toggle 不同的卫星编号
        withContext(Dispatchers.IO) {
            catalogNumbers.map { num ->
                async { store.toggle(num) }
            }.awaitAll()
        }

        val finalSet = store.load()
        assertEquals(
            "并发 toggle 不应丢失任何卫星编号",
            catalogNumbers.toSet(),
            finalSet
        )
    }

    /**
     * 验证 Bug #3 修复：多线程并发 toggle 同一卫星编号，最终状态一致。
     *
     * 偶数次 toggle 应恢复到初始状态（未关注），奇数次应处于已关注。
     * 修复前可能出现中间状态丢失，导致最终状态不确定。
     */
    @Test
    fun toggle_concurrentSameSatellite_finalStateConsistent() = runBlocking {
        val store = FavoriteSatellitesStore(context)
        val catalogNumber = 25544
        val toggleCount = 20 // 偶数次，最终应未关注

        withContext(Dispatchers.IO) {
            (1..toggleCount).map {
                async { store.toggle(catalogNumber) }
            }.awaitAll()
        }

        val finalSet = store.load()
        assertEquals(
            "20 次（偶数）并发 toggle 后应恢复未关注",
            false,
            catalogNumber in finalSet
        )
    }
}
