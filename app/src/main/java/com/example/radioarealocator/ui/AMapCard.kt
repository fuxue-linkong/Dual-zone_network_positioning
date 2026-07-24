package com.example.radioarealocator.ui

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.radioarealocator.ui.theme.LocalCardAlpha
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AMapCard(
    latitude: Double,
    longitude: Double,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val markerTitle = stringResource(R.string.map_center_marker)

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
        }
    }

    val aMap = remember(mapView) { mapView.map }

    val lastCoord = remember { object { var lat = Double.NaN; var lng = Double.NaN } }

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
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
            contentColor = MiuixTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.map_title),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            AndroidView(
                factory = {
                    aMap.apply {
                        uiSettings.isZoomControlsEnabled = true
                        uiSettings.isZoomGesturesEnabled = true
                        uiSettings.isScrollGesturesEnabled = true
                        uiSettings.isRotateGesturesEnabled = true
                        uiSettings.isTiltGesturesEnabled = true

                        val target = LatLng(latitude, longitude)
                        moveCamera(
                            CameraUpdateFactory.newLatLngZoom(target, DEFAULT_ZOOM)
                        )

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
                text = String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceSecondary
            )
        }
    }
}

private const val DEFAULT_ZOOM = 15f
