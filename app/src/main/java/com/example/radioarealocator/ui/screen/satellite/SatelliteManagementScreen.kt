package com.example.radioarealocator.ui.screen.satellite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.radioarealocator.R
import com.example.radioarealocator.data.satellite.SatelliteInfo
import com.example.radioarealocator.ui.MainViewModel
import com.example.radioarealocator.ui.navigation3.LocalNavigator
import com.example.radioarealocator.ui.theme.LocalEnableBlur
import com.example.radioarealocator.ui.util.BlurredBar
import com.example.radioarealocator.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 卫星管理：展示附近过境卫星，点击星标切换关注。
 * 关注的卫星会触发自动提醒（见 MainViewModel.toggleFavorite）。
 */
@Composable
fun SatelliteManagementScreen() {
    val navigator = LocalNavigator.current
    val mainViewModel = viewModel<MainViewModel>()
    // MainViewModel 的状态是 Compose State<T>（非 StateFlow），直接用 by 委托即可
    val satelliteState by mainViewModel.satelliteState
    val favorites by mainViewModel.favoriteSatellites

    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()

    MiuixTheme {
        Scaffold(
            topBar = {
                BlurredBar(backdrop) {
                    TopAppBar(
                        color = barColor,
                        title = stringResource(R.string.satellite_management),
                        navigationIcon = {
                            Box(modifier = Modifier.padding(start = 12.dp)) {
                                IconButton(onClick = dropUnlessResumed { navigator.pop() }) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = colorScheme.onBackground
                                    )
                                }
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
            },
            popupHost = { },
            contentWindowInsets = WindowInsets.systemBars
                .add(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal)
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    satelliteState.isSatelliteLoading && satelliteState.satellites.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.processing),
                                color = colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    satelliteState.satelliteError != null && satelliteState.satellites.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(
                                    R.string.satellite_load_failed,
                                    satelliteState.satelliteError ?: ""
                                ),
                                color = colorScheme.onError
                            )
                        }
                    }
                    satelliteState.satellites.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.no_satellites),
                                color = colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .scrollEndHaptic()
                                .overScrollVertical()
                                .padding(horizontal = 12.dp),
                            contentPadding = innerPadding,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            overscrollEffect = null,
                        ) {
                            items(
                                items = satelliteState.satellites,
                                key = { it.catalogNumber }
                            ) { sat ->
                                SatelliteRow(
                                    satellite = sat,
                                    isFavorite = sat.catalogNumber in favorites,
                                    onToggleFavorite = { mainViewModel.toggleFavorite(sat.catalogNumber) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SatelliteRow(
    satellite: SatelliteInfo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
) {
    val timeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())
    val cardColor = if (isFavorite) {
        colorScheme.primaryContainer.copy(alpha = 0.4f)
    } else {
        colorScheme.surface
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.defaultColors(color = cardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = satellite.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "NORAD: ${satellite.catalogNumber} · ${satellite.modes.joinToString("/")}",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = stringResource(R.string.aos_time) + ": " + timeFormatter.format(satellite.aosTime) +
                        " · " + stringResource(R.string.max_elevation) + ": ${satellite.maxElevation.toInt()}°",
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
                Text(
                    text = (if (satellite.isCurrentlyVisible) stringResource(R.string.in_pass) else stringResource(R.string.upcoming)) +
                        " · " + satellite.source,
                    fontSize = 12.sp,
                    color = colorScheme.onSurfaceVariantSummary
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                    contentDescription = stringResource(R.string.satellite_management),
                    tint = if (isFavorite) colorScheme.primary else colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
