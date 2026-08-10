package com.example.radioarealocator.ui.screen.satellite

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Marker
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.Polygon
import com.amap.api.maps.model.PolygonOptions
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.example.radioarealocator.data.location.CoordinateConverter
import com.example.radioarealocator.data.satellite.SatelliteLiveInfo
import com.example.radioarealocator.data.satellite.buildCoverageCircle
import com.example.radioarealocator.data.satellite.coverageRadiusKm
import com.example.radioarealocator.data.satellite.normalizeLongitude180
import com.example.radioarealocator.data.satellite.predict.SatellitePropagator
import com.example.radioarealocator.data.satellite.predict.TleElements
import com.example.radioarealocator.data.satellite.splitTrackAtAntimeridian
import com.example.radioarealocator.data.satellite.subpointFromAzElevation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// 轨道地图绘制的固定颜色（WGS84 墨卡托投影下的视觉标识）
private const val COLOR_STATION = 0xFF1E88E5.toInt()
private const val COLOR_SATELLITE = 0xFFFF7043.toInt()
private const val COLOR_TRACK = 0xFF1565C0.toInt()
private const val COLOR_COVERAGE_FILL = 0x331565C0.toInt()
private const val COLOR_COVERAGE_STROKE = 0xAA1565C0.toInt()

/** 轨迹采样步长（秒） */
private const val TRACK_SAMPLE_SECONDS = 60
/** 轨迹回溯/前瞻分钟数 */
private const val TRACK_MINUTES_BEFORE = 60
private const val TRACK_MINUTES_AFTER = 60

/**
 * 卫星轨道地图（共享组件，双主题通用）。
 *
 * 基于 AMap [MapView]（复用项目现有 SDK 集成）：
 * - 地面站 Marker（蓝色）
 * - 卫星实时星下点 Marker（橙色，随 [liveInfo] 每秒更新）
 * - 地面轨迹 Polyline（前后各 60 分钟、60 秒采样，跨 ±180° 经线自动分段）
 * - 卫星覆盖圆 Polygon（星下点为中心、半径随高度变化，跨经线自动分段）
 * - 太阳/月亮星下点 Marker（由 [SatelliteLiveInfo] 的日月方位/仰角反推，不可用时跳过）
 *
 * 所有 WGS84 坐标经 GCJ02 偏移后渲染（高德地图坐标系）。
 *
 * @param tle 卫星 TLE
 * @param stationLatDeg 地面站纬度（度）
 * @param stationLonDeg 地面站经度（度）
 * @param liveInfo 实时卫星信息（驱动星下点/日月/覆盖圆更新）
 */
@Composable
fun SatelliteOrbitMap(
    tle: TleElements,
    stationLatDeg: Double,
    stationLonDeg: Double,
    liveInfo: SatelliteLiveInfo?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    val hasInitialized = remember { mutableStateOf(false) }

    // Marker / 图形对象引用（跨重组保持）
    val stationMarker = remember { mutableStateOf<Marker?>(null) }
    val satelliteMarker = remember { mutableStateOf<Marker?>(null) }
    val sunMarker = remember { mutableStateOf<Marker?>(null) }
    val moonMarker = remember { mutableStateOf<Marker?>(null) }
    var trackPolylines by remember { mutableStateOf<List<Polyline>>(emptyList()) }
    var lastTrackSegments by remember { mutableStateOf<List<List<LatLng>>?>(null) }
    var coveragePolygons by remember { mutableStateOf<List<Polygon>>(emptyList()) }
    var lastCoverageRings by remember { mutableStateOf<List<List<LatLng>>?>(null) }
    var lastCelestial by remember { mutableStateOf<Pair<LatLng?, LatLng?>?>(null) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    // 轨迹窗口每分钟滚动一次（保持"前后 60 分钟"跟随当前时间）
    var trackEpochMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            trackEpochMillis = System.currentTimeMillis()
        }
    }

    // 轨迹计算在 Default 调度器执行，不阻塞主线程
    val trackSegments by produceState(
        initialValue = emptyList<List<LatLng>>(),
        key1 = TrackRequest(tle, stationLatDeg, stationLonDeg, trackEpochMillis),
    ) {
        value = withContext(Dispatchers.Default) {
            computeTrackSegments(tle, stationLatDeg, stationLonDeg, trackEpochMillis)
        }
    }

    // 覆盖圆：纯三角函数，每帧（1 秒）重算，主线程开销可忽略
    val coverageRings = remember(liveInfo) {
        val info = liveInfo ?: return@remember emptyList<List<LatLng>>()
        val radius = coverageRadiusKm(info.altitudeKm)
        buildCoverageCircle(info.subpointLatDeg, info.subpointLonDeg, radius, 180)
            .let { splitTrackAtAntimeridian(it) }
            .map { ring -> ring.map { (lat, lon) -> toGcj02(lat, lon) } }
    }

    // 太阳/月亮星下点（az/el → 地面投影，逐秒更新）
    val celestialSubpoints = remember(liveInfo) {
        val info = liveInfo ?: return@remember (null to null)
        val sun = info.sunAzimuthDeg?.let { az ->
            info.sunElevationDeg?.let { el -> subpointFromAzElevation(stationLatDeg, stationLonDeg, az, el) }
        }
        val moon = info.moonAzimuthDeg?.let { az ->
            info.moonElevationDeg?.let { el -> subpointFromAzElevation(stationLatDeg, stationLonDeg, az, el) }
        }
        sun?.let { (lat, lon) -> toGcj02(lat, lon) } to moon?.let { (lat, lon) -> toGcj02(lat, lon) }
    }

    AndroidView(
        factory = {
            mapView.onCreate(Bundle())
            mapView.map
            mapView
        },
        update = { mv ->
            val aMap = mv.map
            val stationGcj = toGcj02(stationLatDeg, stationLonDeg)

            // 地面站 Marker + 首次相机定位（仅一次）
            if (stationMarker.value == null) {
                stationMarker.value = aMap.addMarker(
                    MarkerOptions()
                        .position(stationGcj)
                        .title("地面站")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                )
                if (!hasInitialized.value) {
                    aMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(stationGcj, 3.5f)
                    )
                    hasInitialized.value = true
                }
            }

            // 卫星星下点 Marker（每秒随 liveInfo 更新）
            liveInfo?.let { info ->
                val subpoint = toGcj02(info.subpointLatDeg, info.subpointLonDeg)
                val marker = satelliteMarker.value
                    ?: aMap.addMarker(
                        MarkerOptions()
                            .position(subpoint)
                            .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE))
                    ).also { satelliteMarker.value = it }
                marker.position = subpoint
                marker.title = "仰角 %.0f° · %.0f km".format(info.elevationDeg, info.rangeKm)
                marker.isVisible = info.isAboveHorizon || info.elevationDeg > -20.0
            }

            // 太阳/月亮星下点 Marker（数据不可用时隐藏）
            updateBodyMarker(
                aMap = aMap,
                markerRef = sunMarker,
                position = celestialSubpoints.first,
                title = "太阳",
                hue = BitmapDescriptorFactory.HUE_YELLOW
            )
            updateBodyMarker(
                aMap = aMap,
                markerRef = moonMarker,
                position = celestialSubpoints.second,
                title = "月亮",
                hue = BitmapDescriptorFactory.HUE_AZURE
            )

            // 地面轨迹 Polyline（仅当轨迹段变化时重建）
            if (trackSegments !== lastTrackSegments) {
                trackPolylines.forEach { it.remove() }
                trackPolylines = trackSegments.map { points ->
                    aMap.addPolyline(
                        PolylineOptions()
                            .addAll(points)
                            .color(COLOR_TRACK)
                            .width(3f)
                    )
                }
                lastTrackSegments = trackSegments
            }

            // 覆盖圆 Polygon（仅当环变化时重建）
            if (coverageRings !== lastCoverageRings) {
                coveragePolygons.forEach { it.remove() }
                coveragePolygons = coverageRings.map { ring ->
                    aMap.addPolygon(
                        PolygonOptions()
                            .addAll(ring)
                            .fillColor(COLOR_COVERAGE_FILL)
                            .strokeColor(COLOR_COVERAGE_STROKE)
                            .strokeWidth(2f)
                    )
                }
                lastCoverageRings = coverageRings
            }
        },
        modifier = modifier
    )
}

/** 轨迹计算请求（produceState 的 key） */
private data class TrackRequest(
    val tle: TleElements,
    val stationLatDeg: Double,
    val stationLonDeg: Double,
    val epochMillis: Long,
)

/**
 * 更新（或创建）太阳/月亮星下点 Marker。
 * [position] 为 null 时隐藏 Marker（数据不可用）。
 */
private fun updateBodyMarker(
    aMap: AMap,
    markerRef: androidx.compose.runtime.MutableState<Marker?>,
    position: LatLng?,
    title: String,
    hue: Float,
) {
    val marker = markerRef.value
    if (position == null) {
        marker?.isVisible = false
        return
    }
    val active = marker ?: aMap.addMarker(
        MarkerOptions()
            .position(position)
            .title(title)
            .icon(BitmapDescriptorFactory.defaultMarker(hue))
    ).also { markerRef.value = it }
    active.position = position
    active.isVisible = true
}

/**
 * 计算地面轨迹：getTrack(now, 60s, 前后各 60 分钟) → WGS84 点列 →
 * 跨经线分段 → GCJ02 转换。
 */
private fun computeTrackSegments(
    tle: TleElements,
    stationLatDeg: Double,
    stationLonDeg: Double,
    epochMillis: Long,
): List<List<LatLng>> {
    return try {
        val propagator = SatellitePropagator(tle, stationLatDeg, stationLonDeg, 0.0)
        val positions = propagator.getTrack(
            epochMillis,
            TRACK_SAMPLE_SECONDS,
            TRACK_MINUTES_BEFORE,
            TRACK_MINUTES_AFTER
        )
        val wgsPoints = positions.mapNotNull { pos ->
            val lat = Math.toDegrees(pos.latitudeRad)
            val lon = normalizeLongitude180(Math.toDegrees(pos.longitudeRad))
            lat to lon
        }
        splitTrackAtAntimeridian(wgsPoints).map { segment ->
            segment.map { (lat, lon) -> toGcj02(lat, lon) }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // TLE 无效或计算失败：返回空轨迹，地图仅显示地面站
        emptyList()
    }
}

/** WGS84 → GCJ02 坐标转换（高德坐标系） */
private fun toGcj02(latDeg: Double, lonDeg: Double): LatLng {
    val (gcjLat, gcjLon) = CoordinateConverter.wgs84ToGcj02(latDeg, lonDeg)
    return LatLng(gcjLat, gcjLon)
}
