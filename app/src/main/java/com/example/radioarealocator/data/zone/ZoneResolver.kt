package com.example.radioarealocator.data.zone

import com.example.radioarealocator.data.ZoneInfo
import kotlin.math.floor

/**
 * CQ / ITU 分区解析器。
 *
 * 内置了一套常见国家/地区的经纬度包围盒覆盖，用于在大部分陆地人口密集区获得较准确结果；
 * 未覆盖区域使用基于经度的经验公式作为兜底估算，适合海上或偏远地区。
 * 如需更高精度，可后续替换为从 assets 加载的 GeoJSON 多边形数据。
 */
object ZoneResolver {

    private data class ZoneRegion(
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double,
        val cqZone: Int,
        val ituZone: Int
    ) {
        fun contains(lat: Double, lon: Double): Boolean {
            return lat in minLat..maxLat && lon in minLon..maxLon
        }
    }

    /**
     * 区域列表按优先级排列（后覆盖前）。包围盒越小的地区排在后面，
     * 以便在重叠时让小区域优先命中。
     */
    private val regions: List<ZoneRegion> = buildList {
        // 北美
        add(ZoneRegion(24.0, 72.0, -180.0, -50.0, cqZone = 5, ituZone = 8))
        add(ZoneRegion(50.0, 72.0, -141.0, -52.0, cqZone = 2, ituZone = 75)) // 加拿大大部分地区
        add(ZoneRegion(18.0, 50.0, -125.0, -66.0, cqZone = 4, ituZone = 8))  // 美国本土
        add(ZoneRegion(18.0, 30.0, -160.0, -154.0, cqZone = 31, ituZone = 61)) // 夏威夷
        add(ZoneRegion(51.0, 72.0, -179.0, -130.0, cqZone = 1, ituZone = 1))  // 阿拉斯加

        // 中美洲与加勒比
        add(ZoneRegion(5.0, 32.0, -118.0, -59.0, cqZone = 7, ituZone = 11))
        add(ZoneRegion(18.0, 24.0, -80.0, -71.0, cqZone = 8, ituZone = 11))   // 古巴/牙买加
        add(ZoneRegion(5.0, 19.0, -95.0, -77.0, cqZone = 7, ituZone = 10))    // 墨西哥/中美洲

        // 南美洲
        add(ZoneRegion(-56.0, 13.0, -82.0, -34.0, cqZone = 11, ituZone = 12))
        add(ZoneRegion(-24.0, 6.0, -80.0, -35.0, cqZone = 11, ituZone = 13))  // 巴西
        add(ZoneRegion(-56.0, -20.0, -75.0, -53.0, cqZone = 12, ituZone = 14)) // 阿根廷/智利等
        add(ZoneRegion(-5.0, 13.0, -80.0, -60.0, cqZone = 9, ituZone = 11))   // 哥伦比亚/委内瑞拉

        // 欧洲
        add(ZoneRegion(36.0, 71.0, -11.0, 40.0, cqZone = 14, ituZone = 27))
        add(ZoneRegion(50.0, 60.0, -8.0, 2.0, cqZone = 14, ituZone = 27))      // 英国/爱尔兰
        add(ZoneRegion(43.0, 51.0, -5.0, 8.0, cqZone = 14, ituZone = 27))      // 法国/比荷卢
        add(ZoneRegion(47.0, 55.0, 5.0, 15.0, cqZone = 14, ituZone = 28))      // 德国/波兰
        add(ZoneRegion(36.0, 44.0, -10.0, 4.0, cqZone = 14, ituZone = 37))     // 西班牙/葡萄牙
        add(ZoneRegion(39.0, 47.0, 6.0, 19.0, cqZone = 15, ituZone = 28))      // 意大利
        add(ZoneRegion(35.0, 42.0, 19.0, 30.0, cqZone = 15, ituZone = 28))     // 希腊/土耳其西
        add(ZoneRegion(55.0, 71.0, 5.0, 32.0, cqZone = 16, ituZone = 29))      // 北欧
        add(ZoneRegion(55.0, 60.0, 20.0, 32.0, cqZone = 15, ituZone = 29))     // 波罗的海
        add(ZoneRegion(59.0, 70.0, 20.0, 45.0, cqZone = 16, ituZone = 29))     // 芬兰/俄罗斯欧洲

        // 俄罗斯/独联体（乌拉尔以东）
        add(ZoneRegion(50.0, 77.0, 40.0, 180.0, cqZone = 16, ituZone = 30))
        add(ZoneRegion(50.0, 70.0, 60.0, 90.0, cqZone = 17, ituZone = 30))
        add(ZoneRegion(50.0, 70.0, 90.0, 120.0, cqZone = 18, ituZone = 31))
        add(ZoneRegion(50.0, 70.0, 120.0, 150.0, cqZone = 19, ituZone = 32))
        add(ZoneRegion(50.0, 70.0, 150.0, 180.0, cqZone = 19, ituZone = 33))

        // 中东/中亚
        add(ZoneRegion(12.0, 42.0, 25.0, 63.0, cqZone = 21, ituZone = 39))
        add(ZoneRegion(24.0, 40.0, 34.0, 48.0, cqZone = 20, ituZone = 39))     // 中东
        add(ZoneRegion(38.0, 43.0, 26.0, 46.0, cqZone = 20, ituZone = 39))     // 土耳其
        add(ZoneRegion(24.0, 40.0, 43.0, 63.0, cqZone = 21, ituZone = 40))     // 伊朗/中亚

        // 非洲
        add(ZoneRegion(-35.0, 38.0, -17.0, 52.0, cqZone = 33, ituZone = 44))
        add(ZoneRegion(0.0, 38.0, -18.0, 12.0, cqZone = 33, ituZone = 46))     // 西北非
        add(ZoneRegion(0.0, 18.0, 12.0, 32.0, cqZone = 37, ituZone = 47))      // 东非
        add(ZoneRegion(-35.0, 0.0, 12.0, 52.0, cqZone = 38, ituZone = 53))     // 南非/东南非
        add(ZoneRegion(-35.0, 6.0, -18.0, 12.0, cqZone = 36, ituZone = 46))    // 中西非

        // 南亚
        add(ZoneRegion(6.0, 37.0, 68.0, 97.0, cqZone = 22, ituZone = 41))
        add(ZoneRegion(6.0, 37.0, 77.0, 90.0, cqZone = 22, ituZone = 41))      // 印度
        add(ZoneRegion(26.0, 37.0, 60.0, 78.0, cqZone = 21, ituZone = 40))     // 巴基斯坦/阿富汗

        // 东亚
        add(ZoneRegion(18.0, 54.0, 97.0, 146.0, cqZone = 24, ituZone = 44))
        add(ZoneRegion(18.0, 54.0, 100.0, 123.0, cqZone = 24, ituZone = 44))   // 中国东部
        add(ZoneRegion(20.0, 46.0, 122.0, 146.0, cqZone = 25, ituZone = 45))   // 日本/韩国
        add(ZoneRegion(18.0, 26.0, 105.0, 121.0, cqZone = 24, ituZone = 44))   // 华南/台湾/香港
        add(ZoneRegion(35.0, 43.0, 128.0, 146.0, cqZone = 25, ituZone = 45))   // 日本

        // 东南亚/大洋洲
        add(ZoneRegion(-11.0, 20.0, 95.0, 142.0, cqZone = 26, ituZone = 49))
        add(ZoneRegion(-11.0, 7.0, 95.0, 120.0, cqZone = 28, ituZone = 54))    // 印尼/马来
        add(ZoneRegion(-11.0, 7.0, 120.0, 142.0, cqZone = 28, ituZone = 51))   // 印尼东部
        add(ZoneRegion(-44.0, -10.0, 112.0, 155.0, cqZone = 30, ituZone = 55)) // 澳大利亚
        add(ZoneRegion(-48.0, -34.0, 166.0, 179.0, cqZone = 32, ituZone = 60)) // 新西兰
        add(ZoneRegion(-10.0, -1.0, 140.0, 155.0, cqZone = 28, ituZone = 51))  // 巴布亚新几内亚

        // 太平洋
        add(ZoneRegion(-25.0, 25.0, 142.0, 180.0, cqZone = 27, ituZone = 51))
        add(ZoneRegion(-25.0, 25.0, -180.0, -120.0, cqZone = 31, ituZone = 61))
        add(ZoneRegion(-25.0, 25.0, -120.0, -80.0, cqZone = 35, ituZone = 11))
        add(ZoneRegion(-25.0, 25.0, -80.0, -30.0, cqZone = 13, ituZone = 12))
        add(ZoneRegion(-25.0, 25.0, -30.0, 20.0, cqZone = 36, ituZone = 46))
    }

    fun resolve(latitude: Double, longitude: Double): ZoneInfo {
        val maidenhead = MaidenheadCalculator.calculate(latitude, longitude)

        // 优先命中精确覆盖的区域
        for (region in regions.asReversed()) {
            if (region.contains(latitude, longitude)) {
                return ZoneInfo(region.cqZone, region.ituZone, maidenhead)
            }
        }

        // 兜底：基于经度的经验公式（海上/偏远地区）
        return ZoneInfo(
            cqZone = fallbackCqZone(longitude),
            ituZone = fallbackItuZone(longitude),
            maidenhead = maidenhead
        )
    }

    private fun fallbackCqZone(longitude: Double): Int {
        val normalized = longitude + 180.0
        val zone = floor(normalized / 9.0).toInt() + 1
        return zone.coerceIn(1, 40)
    }

    private fun fallbackItuZone(longitude: Double): Int {
        val normalized = longitude + 180.0
        val zone = floor(normalized / 4.0).toInt() + 1
        return zone.coerceIn(1, 90)
    }
}
