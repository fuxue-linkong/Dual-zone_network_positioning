package com.example.radioarealocator.ui

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.example.radioarealocator.R

/**
 * 高德地图交互式卡片。
 *
 * 使用高德地图 Android SDK（3D地图 V11.x）替代静态地图图片，实现：
 * - 用户自主缩放（捏合手势、双击放大、缩放按钮）
 * - 拖动平移地图
 * - 标记点（Marker）始终位于用户当前坐标
 * - 缩放级别范围 1-18 级
 * - 默认缩放级别 15（街道级）
 *
 * 地图生命周期由 Compose 的 [DisposableEffect] 和 [LifecycleEventObserver] 管理，
 * 自动转发 onCreate/onResume/onPause/onDestroy/onSaveInstanceState 给 MapView。
 *
 * SDK Key 通过 AndroidManifest meta-data（com.amap.api.v2.apikey）注入，
 * 由 manifestPlaceholders 在构建时填充。
 *
 * @param latitude 纬度
 * @param longitude 经度
 * @param modifier 修饰符
 */
@Composable
fun AMapCard(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val markerTitle = stringResource(R.string.map_center_marker)

    // 创建 MapView 并保持其引用（生命周期内不变）
    val mapView = remember {
        MapView(context).apply {
            // 必须先调用 onCreate，否则地图无法显示
            onCreate(Bundle())
        }
    }

    // 拿到 AMap 控制器用于配置地图
    val aMap = remember(mapView) { mapView.map }

    // 记录上一次已同步到地图的坐标，仅在经纬度真正变化时才移动相机/更新 Marker，
    // 避免父级重组导致地图视角被反复重置、与用户手势（缩放/拖动）对抗
    val lastCoord = remember { object { var lat = Double.NaN; var lng = Double.NaN } }

    // 管理地图生命周期：随宿主 Activity 生命周期转发事件给 MapView
    // 高德 MapView 生命周期方法：onCreate/onResume/onPause/onDestroy（无 onStop）
    // 注意：onDestroy 只在 onDispose 中调用一次。
    // 不能同时在 ON_DESTROY 事件和 onDispose 中调用，否则会双重销毁导致闪退
    // （Activity 销毁时 ON_DESTROY 先触发，随后 Compose 清理触发 onDispose）。
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.map_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 地图视图：16:9 比例，圆角裁剪
            AndroidView(
                factory = {
                    aMap.apply {
                        // 显示高德自带的缩放按钮（右下角 +/-）
                        uiSettings.isZoomControlsEnabled = true
                        // 启用缩放手势（捏合、双击放大）
                        uiSettings.isZoomGesturesEnabled = true
                        // 启用拖动手势
                        uiSettings.isScrollGesturesEnabled = true
                        // 启用旋转手势
                        uiSettings.isRotateGesturesEnabled = true
                        // 启用倾斜手势（双指下拉）
                        uiSettings.isTiltGesturesEnabled = true

                        // 移动到用户坐标 + 设置默认缩放级别
                        val target = LatLng(latitude, longitude)
                        moveCamera(
                            CameraUpdateFactory.newLatLngZoom(target, DEFAULT_ZOOM)
                        )

                        // 添加 Marker（保持位置在用户坐标，缩放时跟随）
                        clear()
                        addMarker(
                            MarkerOptions()
                                .position(target)
                                .title(markerTitle)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        )
                        lastCoord.lat = latitude
                        lastCoord.lng = longitude
                    }
                    mapView
                },
                update = {
                    // 仅在经纬度真正变化时（如定位更新）才重置相机和更新 Marker，
                    // 其他原因触发的重组（如主题/滚动）不应打扰用户的地图视角
                    val target = LatLng(latitude, longitude)
                    if (lastCoord.lat != latitude || lastCoord.lng != longitude) {
                        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(target, DEFAULT_ZOOM))
                        aMap.clear()
                        aMap.addMarker(
                            MarkerOptions()
                                .position(target)
                                .title(markerTitle)
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
                        )
                        lastCoord.lat = latitude
                        lastCoord.lng = longitude
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = String.format("%.4f, %.4f", latitude, longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private const val DEFAULT_ZOOM = 15f
