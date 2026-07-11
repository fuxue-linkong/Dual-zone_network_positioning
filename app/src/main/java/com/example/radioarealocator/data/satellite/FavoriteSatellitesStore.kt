package com.example.radioarealocator.data.satellite

import android.content.Context
import android.content.SharedPreferences

/**
 * 用户关注的卫星 NORAD 编号持久化存储。
 * 基于 SharedPreferences，进程重启后可恢复关注列表。
 */
class FavoriteSatellitesStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 加载已关注的卫星 NORAD 编号集合。
     */
    fun load(): Set<Int> =
        prefs.getStringSet(KEY_FAVORITES, emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?: emptySet()

    /**
     * 写入完整关注集合。
     */
    fun save(favorites: Set<Int>) {
        prefs.edit()
            .putStringSet(KEY_FAVORITES, favorites.map { it.toString() }.toSet())
            .apply()
    }

    /**
     * 切换某颗卫星的关注状态，返回新的关注集合。
     */
    @Synchronized
    fun toggle(catalogNumber: Int): Set<Int> {
        val current = load()
        val updated = if (catalogNumber in current) {
            current - catalogNumber
        } else {
            current + catalogNumber
        }
        save(updated)
        return updated
    }

    companion object {
        private const val PREFS_NAME = "radio_area_favorites"
        private const val KEY_FAVORITES = "favorite_satellites"
    }
}
