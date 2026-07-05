package com.example.radioarealocator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.radioarealocator.R
import com.example.radioarealocator.data.LocationResult
import com.example.radioarealocator.data.satellite.SatelliteInfo
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
private const val APP_NAME_FONT_SIZE = 32
private const val LOCAL_TIME_FONT_SIZE = 44
private const val UTC_FONT_SIZE_SCALE = 0.4f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit
) {
    val uiState by viewModel.uiState
    val favorites by viewModel.favoriteSatellites
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    // 主页子页面：0=列表, 1=定位详情, 2=卫星详情, 3=卫星管理（三级）
    var homeSubScreen by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    // 启动时初始化：从缓存加载 TLE，必要时后台拉取
    LaunchedEffect(Unit) {
        viewModel.initializeIfNeeded()
    }

    // 时钟状态，每秒刷新
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }

    // 背景图选择器（Photo Picker）
    val pickBackgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // 尝试获取持久化读取权限；Photo Picker 在 Android 13+ 上的 URI 通常是临时的，
            // 但低版本回退到 ACTION_OPEN_DOCUMENT 时此处会成功。失败也无碍——本次会话仍可读取。
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.setBackgroundUri(uri)
        }
    }

    BackHandler(enabled = showAbout || (selectedTab == 0 && homeSubScreen != 0)) {
        when {
            showAbout -> showAbout = false
            selectedTab == 0 && homeSubScreen == 3 -> homeSubScreen = 2
            selectedTab == 0 && homeSubScreen != 0 -> homeSubScreen = 0
        }
    }

    if (showAbout) {
        AboutScreen(onBackClick = { showAbout = false })
        return
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            when {
                selectedTab == 0 && homeSubScreen == 0 -> HomeHeader(
                    uiState = uiState,
                    now = now
                )
                selectedTab == 0 && homeSubScreen == 1 -> TopAppBar(
                    title = { Text(stringResource(R.string.home_location)) },
                    navigationIcon = {
                        IconButton(onClick = { homeSubScreen = 0 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                selectedTab == 0 && homeSubScreen == 3 -> TopAppBar(
                    title = { Text(stringResource(R.string.satellite_management)) },
                    navigationIcon = {
                        IconButton(onClick = { homeSubScreen = 2 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                selectedTab == 0 && homeSubScreen == 2 -> TopAppBar(
                    title = { Text(stringResource(R.string.home_satellite)) },
                    navigationIcon = {
                        IconButton(onClick = { homeSubScreen = 0 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                else -> TopAppBar(
                    title = { Text(stringResource(R.string.settings)) }
                )
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text(stringResource(R.string.home)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                    label = { Text(stringResource(R.string.settings)) }
                )
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            when (homeSubScreen) {
                0 -> HomeListContent(
                    onLocationClick = { homeSubScreen = 1 },
                    onSatelliteClick = { homeSubScreen = 2 },
                    contentPadding = padding
                )
                1 -> LocationDetailContent(
                    uiState = uiState,
                    hasLocationPermission = viewModel.hasLocationPermission,
                    onRequestPermission = onRequestPermission,
                    onRefresh = { viewModel.refreshLocation() },
                    onDismissError = { viewModel.dismissError() },
                    contentPadding = padding
                )
                2 -> SatelliteDetailContent(
                    uiState = uiState,
                    filter = viewModel.satelliteFilter.value,
                    onFilterChange = viewModel::updateSatelliteFilter,
                    onGetLocation = { viewModel.refreshLocationOnly() },
                    onUpdateSource = { viewModel.refreshSatelliteSourceOnly() },
                    favorites = favorites,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onOpenManagement = { homeSubScreen = 3 },
                    contentPadding = padding
                )
                3 -> SatelliteManagementContent(
                    uiState = uiState,
                    favorites = favorites,
                    onToggleFavorite = viewModel::toggleFavorite,
                    contentPadding = padding
                )
            }
        } else {
            SettingsScreen(
                satelliteSource = viewModel.satelliteSource.value,
                onSourceSelected = { viewModel.setSatelliteSource(it) },
                backgroundUri = viewModel.backgroundUri.value,
                onPickBackground = {
                    pickBackgroundLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onClearBackground = { viewModel.setBackgroundUri(null) },
                onAboutClick = { showAbout = true },
                contentPadding = padding
            )
        }
    }
}

/**
 * 主页头部：应用名位于左上（类似应用标题），下方居中显示本地时间 + UTC。
 * 时间区域带背景色，与状态色联动。
 */
@Composable
private fun HomeHeader(
    uiState: MainUiState,
    now: Instant
) {
    val stateColor = if (uiState.result != null) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    val appNameSize = APP_NAME_FONT_SIZE.sp
    val localTimeSize = LOCAL_TIME_FONT_SIZE.sp
    val utcSize = (LOCAL_TIME_FONT_SIZE * UTC_FONT_SIZE_SCALE).sp

    val localTime = now.atZone(ZoneId.systemDefault()).format(timeFormatter)
    val utcTime = now.atZone(ZoneOffset.UTC).format(timeFormatter)

    // 时间区域背景色：状态色半透明
    val timeBgColor = stateColor.copy(alpha = 0.12f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = APP_NAME_FONT_SIZE.dp)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            style = TextStyle(
                fontSize = appNameSize,
                fontWeight = FontWeight.Normal
            ),
            color = stateColor,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )
        // 时间区域：带圆角背景色
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(timeBgColor)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = localTime,
                style = TextStyle(
                    fontSize = localTimeSize,
                    fontWeight = FontWeight.Normal
                ),
                color = stateColor,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Text(
                text = "$utcTime UTC",
                style = TextStyle(fontSize = utcSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * 主页列表：定位、卫星两个入口。
 */
@Composable
private fun HomeListContent(
    onLocationClick: () -> Unit,
    onSatelliteClick: () -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HomeListItem(
                title = stringResource(R.string.home_location),
                description = stringResource(R.string.home_location_desc),
                badgeChar = "定",
                onClick = onLocationClick
            )
        }
        item {
            HomeListItem(
                title = stringResource(R.string.home_satellite),
                description = stringResource(R.string.home_satellite_desc),
                badgeChar = "卫",
                onClick = onSatelliteClick
            )
        }
    }
}

@Composable
private fun HomeListItem(
    title: String,
    description: String,
    badgeChar: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeChar,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LocationDetailContent(
    uiState: MainUiState,
    hasLocationPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit,
    onDismissError: () -> Unit,
    contentPadding: PaddingValues
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        item {
            LocationStatusCard(
                isLoading = uiState.isLoading,
                result = uiState.result,
                hasPermission = hasLocationPermission,
                onRequestPermission = onRequestPermission,
                onRefresh = onRefresh
            )
        }

        if (uiState.result != null) {
            item {
                ZoneInfoCard(result = uiState.result)
            }
        }
    }

    uiState.error?.let { message ->
        ErrorDialog(message = message, onDismiss = onDismissError)
    }
}

@Composable
private fun SatelliteDetailContent(
    uiState: MainUiState,
    filter: SatelliteFilter,
    onFilterChange: (SatelliteFilter) -> Unit,
    onGetLocation: () -> Unit,
    onUpdateSource: () -> Unit,
    favorites: Set<Int>,
    onToggleFavorite: (Int) -> Unit,
    onOpenManagement: () -> Unit,
    contentPadding: PaddingValues
) {
    val filteredSatellites = remember(uiState.satellites, filter, favorites) {
        uiState.satellites.applyFilter(filter, favorites)
    }
    val totalCount = uiState.satellites.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        item {
            SatelliteActionCard(
                isLoading = uiState.isLoading,
                isSatelliteLoading = uiState.isSatelliteLoading,
                lastLocationTime = uiState.lastLocationUpdateTime,
                lastLocationCity = uiState.lastLocationCity,
                lastSatelliteTime = uiState.lastSatelliteUpdateTime,
                onGetLocation = onGetLocation,
                onUpdateSource = onUpdateSource
            )
        }
        item {
            SatelliteSectionHeader(
                isLoading = uiState.isSatelliteLoading,
                filteredCount = filteredSatellites.size,
                totalCount = totalCount,
                isActive = filter.isActive,
                filter = filter,
                onFilterChange = onFilterChange,
                onOpenManagement = onOpenManagement
            )
        }
        when {
            uiState.isSatelliteLoading && filteredSatellites.isEmpty() -> {
                item {
                    SatellitePlaceholderCard { CircularProgressIndicator() }
                }
            }
            uiState.satelliteError != null -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(R.string.satellite_load_failed, uiState.satelliteError),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            uiState.result == null -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(R.string.satellite_need_location),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            filteredSatellites.isEmpty() -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(
                                if (filter.isActive) R.string.no_satellites_filtered
                                else R.string.no_satellites
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                items(
                    items = filteredSatellites,
                    key = { it.catalogNumber }
                ) { sat ->
                    SatelliteItem(
                        satellite = sat,
                        isFavorite = sat.catalogNumber in favorites,
                        onToggleFavorite = { onToggleFavorite(sat.catalogNumber) }
                    )
                }
            }
        }
    }
}

/**
 * 卫星管理三级页面：展示所有可见卫星，可对任意卫星加/取消特别关注。
 */
@Composable
private fun SatelliteManagementContent(
    uiState: MainUiState,
    favorites: Set<Int>,
    onToggleFavorite: (Int) -> Unit,
    contentPadding: PaddingValues
) {
    val satellites = uiState.satellites
    val favoriteCount = satellites.count { it.catalogNumber in favorites }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.satellite_management_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ManagementStat(
                            label = stringResource(R.string.satellite_count, satellites.size),
                            value = satellites.size.toString()
                        )
                        ManagementStat(
                            label = stringResource(R.string.favorites_count),
                            value = favoriteCount.toString()
                        )
                    }
                }
            }
        }
        when {
            uiState.isSatelliteLoading && satellites.isEmpty() -> {
                item {
                    SatellitePlaceholderCard { CircularProgressIndicator() }
                }
            }
            uiState.result == null -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(R.string.satellite_need_location),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            satellites.isEmpty() -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(R.string.no_satellites),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                // 已关注的卫星排在前面
                val sorted = satellites.sortedWith(
                    compareByDescending<SatelliteInfo> { it.catalogNumber in favorites }
                        .thenByDescending { it.isCurrentlyVisible }
                        .thenBy { it.aosTime }
                )
                items(
                    items = sorted,
                    key = { it.catalogNumber }
                ) { sat ->
                    SatelliteManagementItem(
                        satellite = sat,
                        isFavorite = sat.catalogNumber in favorites,
                        onToggleFavorite = { onToggleFavorite(sat.catalogNumber) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ManagementStat(label: String, value: String) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SatelliteManagementItem(
    satellite: SatelliteInfo,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    val timeText = if (satellite.isCurrentlyVisible) {
        stringResource(R.string.in_pass)
    } else {
        satellite.aosTime.atZone(zone).format(formatter)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isFavorite) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            contentColor = if (isFavorite) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleFavorite)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = if (isFavorite) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = satellite.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (satellite.isCurrentlyVisible) {
                        "${stringResource(R.string.in_pass)} · $timeText"
                    } else {
                        "${stringResource(R.string.aos_time)}：$timeText"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFavorite) {
                        MaterialTheme.colorScheme.onTertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.tertiary)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.favorited),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiary
                    )
                }
            }
        }
    }
}


@Composable
private fun SatelliteActionCard(
    isLoading: Boolean,
    isSatelliteLoading: Boolean,
    lastLocationTime: Instant?,
    lastLocationCity: String,
    lastSatelliteTime: Instant?,
    onGetLocation: () -> Unit,
    onUpdateSource: () -> Unit
) {
    val dateTimeFormatter = remember { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss") }
    val zoneId = remember { ZoneId.systemDefault() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 第一行：获取定位按钮 + 最近定位信息
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onGetLocation,
                    enabled = !isLoading,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.sat_action_get_location))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lastLocationTime != null) {
                            lastLocationTime.atZone(zoneId).format(dateTimeFormatter)
                        } else {
                            stringResource(R.string.sat_no_location_time)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = lastLocationCity.ifBlank { stringResource(R.string.sat_no_city) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            // 第二行：更新卫星源按钮 + 最近更新时间 + 是否过期
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onUpdateSource,
                    enabled = !isSatelliteLoading,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    if (isSatelliteLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.sat_action_update_source))
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (lastSatelliteTime != null) {
                            lastSatelliteTime.atZone(zoneId).format(dateTimeFormatter)
                        } else {
                            stringResource(R.string.sat_no_source_time)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    val expired = isSatelliteSourceExpired(lastSatelliteTime)
                    Text(
                        text = if (expired) {
                            stringResource(R.string.sat_source_expired)
                        } else {
                            stringResource(R.string.sat_source_fresh)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (expired) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LocationStatusCard(
    isLoading: Boolean,
    result: LocationResult?,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onRefresh: () -> Unit
) {
    val isSuccess = result != null
    val containerColor = if (isSuccess) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isSuccess) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (isSuccess) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
                Text(
                    text = if (isSuccess) stringResource(R.string.location_success) else stringResource(R.string.location_status),
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    CircularProgressIndicator(color = contentColor)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.locating),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                }

                result != null -> {
                    Text(
                        text = "%.5f".format(result.latitude),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = "%.5f".format(result.longitude),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRefresh,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.action_refresh))
                    }
                }

                else -> {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = contentColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (hasPermission) {
                            stringResource(R.string.tap_to_locate)
                        } else {
                            stringResource(R.string.location_permission_required)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = if (hasPermission) onRefresh else onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            if (hasPermission) {
                                stringResource(R.string.action_refresh)
                            } else {
                                stringResource(R.string.grant_permission)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneInfoCard(result: LocationResult) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                text = stringResource(R.string.zone_info),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ZoneItem(
                    label = stringResource(R.string.cq_zone),
                    value = result.cqZone?.toString() ?: "-"
                )
                ZoneItem(
                    label = stringResource(R.string.itu_zone),
                    value = result.ituZone?.toString() ?: "-"
                )
                ZoneItem(
                    label = stringResource(R.string.maidenhead),
                    value = result.maidenhead.uppercase()
                )
            }

            if (result.address.isNotBlank()) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${stringResource(R.string.address)}：",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = result.address,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ZoneItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SatelliteSectionHeader(
    isLoading: Boolean,
    filteredCount: Int,
    totalCount: Int,
    isActive: Boolean,
    filter: SatelliteFilter,
    onFilterChange: (SatelliteFilter) -> Unit,
    onOpenManagement: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.nearby_satellites),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (totalCount > 0) {
                val countText = if (isActive) {
                    stringResource(R.string.satellite_count_filtered, filteredCount, totalCount)
                } else {
                    stringResource(R.string.satellite_count, totalCount)
                }
                Text(
                    text = countText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SatelliteFilterPopup(
                filter = filter,
                onFilterChange = onFilterChange,
                onOpenManagement = onOpenManagement
            )
        }
    }
}

/**
 * 卫星筛选弹窗：以 Dialog 形式呈现，支持模式多选与开关筛选，
 * 底部提供"更多"入口进入三级页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatelliteFilterPopup(
    filter: SatelliteFilter,
    onFilterChange: (SatelliteFilter) -> Unit,
    onOpenManagement: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = if (filter.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = null,
                tint = if (filter.isActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (filter.isActive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        if (expanded) {
            BasicAlertDialog(
                onDismissRequest = { expanded = false }
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 560.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 标题区
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.filter_title),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (filter.isActive) {
                                TextButton(
                                    onClick = { onFilterChange(SatelliteFilter()) }
                                ) {
                                    Text(
                                        text = stringResource(R.string.filter_reset),
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        HorizontalDivider()

                        // 内容区（可滚动）
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.filter_mode_section),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                            )
                            FILTER_MODE_OPTIONS.forEach { mode ->
                                val selected = mode.value in filter.modes
                                FilterCheckRow(
                                    label = mode.label,
                                    checked = selected,
                                    onToggle = {
                                        val newModes = if (selected) {
                                            filter.modes - mode.value
                                        } else {
                                            filter.modes + mode.value
                                        }
                                        onFilterChange(filter.copy(modes = newModes))
                                    }
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            FilterSwitchRow(
                                label = stringResource(R.string.filter_only_in_pass),
                                checked = filter.onlyInPass,
                                onToggle = {
                                    onFilterChange(
                                        filter.copy(onlyInPass = !filter.onlyInPass, onlyUpcoming = false)
                                    )
                                }
                            )
                            FilterSwitchRow(
                                label = stringResource(R.string.filter_only_upcoming),
                                checked = filter.onlyUpcoming,
                                onToggle = {
                                    onFilterChange(
                                        filter.copy(onlyUpcoming = !filter.onlyUpcoming, onlyInPass = false)
                                    )
                                }
                            )
                            FilterSwitchRow(
                                label = stringResource(R.string.filter_only_amsat),
                                checked = filter.onlyAmsat,
                                onToggle = {
                                    onFilterChange(filter.copy(onlyAmsat = !filter.onlyAmsat))
                                }
                            )
                            FilterSwitchRow(
                                label = stringResource(R.string.filter_only_favorites),
                                checked = filter.onlyFavorites,
                                onToggle = {
                                    onFilterChange(filter.copy(onlyFavorites = !filter.onlyFavorites))
                                }
                            )
                        }

                        // 底部按钮区
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                expanded = false
                                onOpenManagement()
                            }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(stringResource(R.string.filter_more))
                            }
                            Button(onClick = { expanded = false }) {
                                Text(stringResource(R.string.filter_done))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterCheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun FilterSwitchRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

private data class FilterModeOption(val value: String, val label: String)

private val FILTER_MODE_OPTIONS = listOf(
    FilterModeOption("FM", "FM"),
    FilterModeOption("SSTV", "SSTV"),
    FilterModeOption("DSTAR", "D-Star"),
    FilterModeOption("CW", "CW"),
    FilterModeOption("USB", "USB"),
    FilterModeOption("LSB", "LSB")
)

@Composable
private fun SatellitePlaceholderCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

private val satelliteTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SatelliteItem(
    satellite: SatelliteInfo,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {}
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    if (satellite.isCurrentlyVisible) {
        LaunchedEffect(satellite.losTime) {
            while (true) {
                nowMillis = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    val timeInfo = remember(satellite.aosTime, satellite.losTime, satellite.isCurrentlyVisible, nowMillis) {
        val formatter = satelliteTimeFormatter
        val zone = ZoneId.systemDefault()
        if (satellite.isCurrentlyVisible) {
            val losTime = satellite.losTime.atZone(zone).format(formatter)
            val remainingSeconds = Duration.between(Instant.now(), satellite.losTime).seconds
            val remainingText = formatRemainingTime(remainingSeconds)
            SatelliteTimeInfo.InPass(losTime, remainingText)
        } else {
            val aosTime = satellite.aosTime.atZone(zone).format(formatter)
            SatelliteTimeInfo.Upcoming(aosTime)
        }
    }

    // 关注卫星在境内/即将过境时使用 tertiary 系列强调色；境内优先用 primaryContainer
    val cardContainerColor = when {
        satellite.isCurrentlyVisible -> MaterialTheme.colorScheme.primaryContainer
        isFavorite -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val cardContentColor = when {
        satellite.isCurrentlyVisible -> MaterialTheme.colorScheme.onPrimaryContainer
        isFavorite -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFavorite) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(
            containerColor = cardContainerColor,
            contentColor = cardContentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                if (satellite.isCurrentlyVisible) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Text(
                        text = satellite.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = cardContentColor
                    )
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${satellite.maxElevation.toInt()}°",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = cardContentColor
                    )
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (isFavorite) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (satellite.source.isNotEmpty()) {
                    SourceChip(source = satellite.source)
                }
                if (satellite.status.isNotEmpty()) {
                    StatusChip(status = satellite.status)
                }
                if (satellite.modes.isEmpty()) {
                    ModeChip(mode = stringResource(R.string.mode_unknown))
                } else {
                    satellite.modes.forEach { mode ->
                        ModeChip(mode = mode)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (timeInfo) {
                is SatelliteTimeInfo.InPass -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimeBadge(
                            label = stringResource(R.string.los_time),
                            value = timeInfo.losTime,
                            isActive = true
                        )
                        TimeBadge(
                            label = stringResource(R.string.time_remaining),
                            value = timeInfo.remainingText,
                            isActive = true
                        )
                    }
                }

                is SatelliteTimeInfo.Upcoming -> {
                    TimeBadge(
                        label = stringResource(R.string.aos_time),
                        value = timeInfo.aosTime,
                        isActive = false
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeBadge(label: String, value: String, isActive: Boolean) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$label：",
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

private sealed class SatelliteTimeInfo {
    data class InPass(val losTime: String, val remainingText: String) : SatelliteTimeInfo()
    data class Upcoming(val aosTime: String) : SatelliteTimeInfo()
}

private fun formatRemainingTime(seconds: Long): String {
    if (seconds <= 0) return "0秒"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) {
        "${minutes}分${remainingSeconds}秒"
    } else {
        "${remainingSeconds}秒"
    }
}

@Composable
private fun SourceChip(source: String) {
    val (bgColor, contentColor) = when (source) {
        "CT" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "SNOGS" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "ALL" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Chip(text = source, bgColor = bgColor, contentColor = contentColor)
}

@Composable
private fun StatusChip(status: String) {
    val displayText = when (status) {
        "Heard" -> stringResource(R.string.status_heard)
        "Telemetry Only" -> stringResource(R.string.status_telemetry_only)
        "Not Heard" -> stringResource(R.string.status_not_heard)
        "Crew Active" -> stringResource(R.string.status_crew_active)
        else -> status
    }
    val (bgColor, contentColor) = when (status) {
        "Heard" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "Telemetry Only" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "Not Heard" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "Crew Active" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Chip(text = displayText, bgColor = bgColor, contentColor = contentColor)
}

@Composable
private fun ModeChip(mode: String) {
    val color = when (mode.uppercase()) {
        "FM" -> MaterialTheme.colorScheme.primaryContainer
        "SSTV" -> MaterialTheme.colorScheme.secondaryContainer
        "DSTAR" -> MaterialTheme.colorScheme.tertiaryContainer
        "CW" -> MaterialTheme.colorScheme.errorContainer
        "USB", "LSB" -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (mode.uppercase()) {
        "FM" -> MaterialTheme.colorScheme.onPrimaryContainer
        "SSTV" -> MaterialTheme.colorScheme.onSecondaryContainer
        "DSTAR" -> MaterialTheme.colorScheme.onTertiaryContainer
        "CW" -> MaterialTheme.colorScheme.onErrorContainer
        "USB", "LSB" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Chip(text = mode, bgColor = color, contentColor = contentColor)
}

@Composable
private fun Chip(
    text: String,
    bgColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        title = { Text(stringResource(R.string.location_failed)) },
        text = { Text(message) }
    )
}
