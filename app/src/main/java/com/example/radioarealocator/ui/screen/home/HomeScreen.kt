package com.example.radioarealocator.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.radioarealocator.permission.PermissionManager
import com.example.radioarealocator.ui.LocalUiMode
import com.example.radioarealocator.ui.MainViewModel
import com.example.radioarealocator.ui.UiMode
import com.example.radioarealocator.ui.navigation3.Navigator
import com.example.radioarealocator.ui.navigation3.Route
import com.example.radioarealocator.ui.viewmodel.HomeViewModel

@Composable
fun HomePager(
    navigator: Navigator,
    bottomInnerPadding: Dp,
    isCurrentPage: Boolean = true
) {
    val homeViewModel = viewModel<HomeViewModel>()
    val mainViewModel = viewModel<MainViewModel>()
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionManager = remember(context) { PermissionManager(context) }
    val permissionState by permissionManager.state.collectAsStateWithLifecycle()

    // MainViewModel 的状态是 Compose State<T>（非 StateFlow），直接用 by 委托即可
    val locationState by mainViewModel.locationState
    val satelliteState by mainViewModel.satelliteState
    val weather by mainViewModel.weather
    val weatherLoading by mainViewModel.weatherLoading
    val weatherError by mainViewModel.weatherError
    val dailyQuote by mainViewModel.dailyQuote
    val favoriteSatellites by mainViewModel.favoriteSatellites

    var hasActivated by remember { mutableStateOf(false) }
    if (isCurrentPage) hasActivated = true

    // 首次进入主页时初始化业务 ViewModel（加载 TLE 缓存、启动天气自动刷新等）
    LaunchedEffect(hasActivated) {
        if (hasActivated) {
            mainViewModel.initializeIfNeeded()
            mainViewModel.startWeatherAutoRefresh()
            mainViewModel.refreshDailyQuote()
        }
    }

    if (hasActivated) {
        LaunchedEffect(Unit) {
            homeViewModel.refresh()
        }
    }
    LifecycleResumeEffect(permissionManager) {
        permissionManager.refresh()
        onPauseOrDispose { }
    }

    val actions = HomeActions(
        onPermissionsClick = { navigator.push(Route.Permissions) },
        onOpenUrl = uriHandler::openUri,
        onRefreshLocation = { mainViewModel.refreshLocation() },
        onRefreshWeather = { mainViewModel.refreshWeather(force = true) },
        onToggleFavorite = mainViewModel::toggleFavorite,
        onSatelliteManagementClick = { navigator.push(Route.SatelliteManagement) },
        onCWPracticeClick = { navigator.push(Route.CWPractice) },
        onLocationDetailClick = { navigator.push(Route.LocationDetail) },
    )

    // 计算下一次过境卫星，用于天气卡显示
    val nextSatellite = remember(satelliteState.satellites, favoriteSatellites) {
        satelliteState.satellites
            .filter { it.catalogNumber in favoriteSatellites || !it.isCurrentlyVisible }
            .minByOrNull { it.aosTime }
    }

    val businessState = HomeBusinessState(
        location = locationState,
        satellites = satelliteState,
        weather = weather,
        weatherLoading = weatherLoading,
        weatherError = weatherError,
        dailyQuote = dailyQuote,
        favorites = favoriteSatellites,
        nextSatellite = nextSatellite,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> HomePagerMiuix(
            state = uiState,
            businessState = businessState,
            permissionState = permissionState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )

        UiMode.Material -> HomePagerMaterial(
            state = uiState,
            businessState = businessState,
            permissionState = permissionState,
            actions = actions,
            bottomInnerPadding = bottomInnerPadding,
        )
    }
}
