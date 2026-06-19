package com.example.radioarealocator.data.zone

import kotlin.math.floor

object MaidenheadCalculator {

    private const val LON_RANGE = 360.0
    private const val LAT_RANGE = 180.0

    /**
     * 计算 6 位梅登兰德定位（ Maidenhead Locator ）。
     */
    fun calculate(latitude: Double, longitude: Double): String {
        require(latitude in -90.0..90.0) { "纬度必须在 -90 到 90 之间" }
        require(longitude in -180.0..180.0) { "经度必须在 -180 到 180 之间" }

        val adjustedLon = longitude + 180.0
        val adjustedLat = latitude + 90.0

        val fieldLon = floor(adjustedLon / (LON_RANGE / 18)).toInt().coerceAtMost(17)
        val fieldLat = floor(adjustedLat / (LAT_RANGE / 18)).toInt().coerceAtMost(17)

        val squareLon = floor((adjustedLon % (LON_RANGE / 18)) / (LON_RANGE / 18 / 10)).toInt()
        val squareLat = floor((adjustedLat % (LAT_RANGE / 18)) / (LAT_RANGE / 18 / 10)).toInt()

        val subsquareLon = floor((adjustedLon % (LON_RANGE / 18 / 10)) / (LON_RANGE / 18 / 10 / 24)).toInt()
        val subsquareLat = floor((adjustedLat % (LAT_RANGE / 18 / 10)) / (LAT_RANGE / 18 / 10 / 24)).toInt()

        return buildString {
            append(('A' + fieldLon))
            append(('A' + fieldLat))
            append(squareLon)
            append(squareLat)
            append(('a' + subsquareLon))
            append(('a' + subsquareLat))
        }
    }

    /**
     * 将 Maidenhead 字符串解析回中心坐标。
     */
    fun decode(locator: String): Pair<Double, Double> {
        require(locator.length >= 6) { "至少需要 6 位 Maidenhead 字符串" }
        val upper = locator.uppercase()

        val fieldLon = upper[0] - 'A'
        val fieldLat = upper[1] - 'A'
        val squareLon = upper[2].digitToInt()
        val squareLat = upper[3].digitToInt()
        val subLon = upper[4].lowercaseChar() - 'a'
        val subLat = upper[5].lowercaseChar() - 'a'

        val lon = -180.0 +
                fieldLon * (LON_RANGE / 18) +
                squareLon * (LON_RANGE / 18 / 10) +
                subLon * (LON_RANGE / 18 / 10 / 24) +
                (LON_RANGE / 18 / 10 / 24) / 2

        val lat = -90.0 +
                fieldLat * (LAT_RANGE / 18) +
                squareLat * (LAT_RANGE / 18 / 10) +
                subLat * (LAT_RANGE / 18 / 10 / 24) +
                (LAT_RANGE / 18 / 10 / 24) / 2

        return lat to lon
    }
}
