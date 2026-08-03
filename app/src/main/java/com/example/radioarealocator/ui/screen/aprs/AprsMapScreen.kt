package com.example.radioarealocator.ui.screen.aprs

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.example.radioarealocator.data.aprs.AprsStation
import com.example.radioarealocator.ui.appViewModel
import com.example.radioarealocator.ui.viewmodel.AprsViewModel
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

@Composable
fun AprsMapScreen(
    onNavigateBack: () -> Unit = {}
) {
    val viewModel = appViewModel<AprsViewModel>()
    val stations by viewModel.stations.collectAsStateWithLifecycle()
    val config by viewModel.settings.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    val addedCallsigns = remember { HashSet<String>() }
    val hasInitialized = remember { mutableStateOf(false) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(32.dp))

        // 顶栏
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "APRS 站点地图",
                style = MiuixTheme.textStyles.title2,
                color = MiuixTheme.colorScheme.onSurface
            )
        }

        Text(
            text = "共 ${stations.distinctBy { it.callsign.substringBefore("-") }.size} 个站点（点击标记查看详情）",
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        AndroidView(
            factory = {
                mapView.onCreate(Bundle())
                val aMap = mapView.map
                aMap.uiSettings.isZoomControlsEnabled = true
                mapView
            },
            update = { mv ->
                val aMap = mv.map
                val myCallsign = config.fullCallsign
                val myStation = stations.find { it.callsign == myCallsign }
                // 增量添加 marker：只添加未显示的站点，不 clear，避免用户交互丢失
                fun addMarkerFor(station: AprsStation, isMyStation: Boolean) {
                    if (station.callsign in addedCallsigns) return
                    aMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(station.latitude, station.longitude))
                            .title(if (isMyStation) "${station.callsign}（本站）" else station.callsign)
                            .snippet(
                                buildString {
                                    append(
                                        String.format(
                                            Locale.US,
                                            "%.4f, %.4f",
                                            station.latitude,
                                            station.longitude
                                        )
                                    )
                                    if (station.comment.isNotEmpty()) {
                                        append("\n${station.comment}")
                                    }
                                }
                            )
                    )
                    addedCallsigns.add(station.callsign)
                }
                myStation?.let { addMarkerFor(it, true) }
                stations.filter { it.callsign != myCallsign }.forEach { addMarkerFor(it, false) }
                // 仅首次设置地图中心，避免每次 stations 更新都强制移回本站
                if (!hasInitialized.value && stations.isNotEmpty()) {
                    val center = myStation ?: stations.first()
                    aMap.moveCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(center.latitude, center.longitude), 12f
                        )
                    )
                    hasInitialized.value = true
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(0.dp)
                .weight(1f)
        )
    }
}
