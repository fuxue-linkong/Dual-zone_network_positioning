package com.example.radioarealocator.ui.screen.satellite

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.dropUnlessResumed
import com.example.radioarealocator.R
import com.example.radioarealocator.data.satellite.SatelliteCatalog
import com.example.radioarealocator.data.satellite.SatelliteInfo
import com.example.radioarealocator.data.satellite.SatelliteStatusSegmenter
import com.example.radioarealocator.data.satellite.SatelliteStatusTracker
import com.example.radioarealocator.data.satellite.SegmentStatus
import com.example.radioarealocator.ui.LocationUiState
import com.example.radioarealocator.ui.MainViewModel
import com.example.radioarealocator.ui.SatelliteFilter
import com.example.radioarealocator.ui.SatelliteUiState
import com.example.radioarealocator.ui.appViewModel
import com.example.radioarealocator.ui.applyFilter
import com.example.radioarealocator.ui.isSatelliteSourceExpired
import com.example.radioarealocator.ui.navigation3.LocalNavigator
import com.example.radioarealocator.ui.theme.LocalCardAlpha
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 卫星管理页 Material3 风格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteManagementMaterial() {
    val navigator = LocalNavigator.current
    val mainViewModel = appViewModel<MainViewModel>()
    val locationState by mainViewModel.locationState
    val satelliteState by mainViewModel.satelliteState
    val favorites by mainViewModel.favoriteSatellites
    val filter by mainViewModel.satelliteFilter

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.satellite_management)) },
                navigationIcon = {
                    IconButton(onClick = dropUnlessResumed { navigator.pop() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { innerPadding ->
        SatelliteManagementContentMaterial(
            locationState = locationState,
            satelliteState = satelliteState,
            filter = filter,
            favorites = favorites,
            statusTracker = mainViewModel.statusTracker,
            onToggleFavorite = mainViewModel::toggleFavorite,
            onFilterChange = mainViewModel::updateSatelliteFilter,
            onGetLocation = mainViewModel::refreshLocationOnly,
            onUpdateSource = mainViewModel::refreshSatelliteSourceOnly,
            contentPadding = innerPadding,
            nestedScrollConnection = scrollBehavior.nestedScrollConnection
        )
    }
}

@Composable
private fun SatelliteManagementContentMaterial(
    locationState: LocationUiState,
    satelliteState: SatelliteUiState,
    filter: SatelliteFilter,
    favorites: Set<Int>,
    statusTracker: SatelliteStatusTracker,
    onToggleFavorite: (Int) -> Unit,
    onFilterChange: (SatelliteFilter) -> Unit,
    onGetLocation: () -> Unit,
    onUpdateSource: () -> Unit,
    contentPadding: PaddingValues,
    nestedScrollConnection: androidx.compose.ui.input.nestedscroll.NestedScrollConnection
) {
    @Suppress("UnusedVariable")
    val statusEntries = statusTracker.statusMap.value

    val filteredSatellites = remember(satelliteState.satellites, filter, favorites) {
        satelliteState.satellites.applyFilter(filter, favorites)
    }
    val totalCount = satelliteState.satellites.size
    val favoriteCount = satelliteState.satellites.count { it.catalogNumber in favorites }

    // 统一倒计时时钟
    var inPassNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasInPassSatellites = filteredSatellites.any { it.isCurrentlyVisible }
    LaunchedEffect(hasInPassSatellites) {
        if (hasInPassSatellites) {
            while (true) {
                inPassNowMillis = System.currentTimeMillis()
                delay(5000)
            }
        }
    }

    // 预计算状态缓存
    val statusCache = remember(statusEntries, filteredSatellites) {
        filteredSatellites.associate { sat ->
            val amsatName = SatelliteCatalog.AMSAT_STATUS_NAME_BY_CATALOG_NUMBER[sat.catalogNumber]
            val statusQuery = if (amsatName != null) statusTracker.queryStatus(amsatName) else null
            val effectiveStatus = statusQuery?.status?.takeIf { it.isNotBlank() } ?: sat.status
            val isInherited = statusQuery?.isInherited ?: false
            sat.catalogNumber to (effectiveStatus to isInherited)
        }
    }

    // 排序
    val sortedSatellites = remember(filteredSatellites, favorites) {
        filteredSatellites.sortedWith(
            compareByDescending<SatelliteInfo> { it.catalogNumber in favorites }
                .thenByDescending { it.isCurrentlyVisible }
                .thenBy { it.aosTime }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
            .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 数据源刷新卡
        item {
            SatelliteActionCardMaterial(
                isLoading = locationState.isLoading,
                isSatelliteLoading = satelliteState.isSatelliteLoading,
                lastLocationTime = locationState.lastLocationUpdateTime,
                lastLocationCity = locationState.lastLocationCity,
                lastSatelliteTime = satelliteState.lastSatelliteUpdateTime,
                onGetLocation = onGetLocation,
                onUpdateSource = onUpdateSource
            )
        }

        // 统计 + 描述 + 筛选入口
        item {
            SatelliteHeaderCardMaterial(
                totalCount = totalCount,
                filteredCount = filteredSatellites.size,
                favoriteCount = favoriteCount,
                filter = filter,
                onFilterChange = onFilterChange
            )
        }

        when {
            satelliteState.isSatelliteLoading && filteredSatellites.isEmpty() -> {
                item { SatellitePlaceholderCardMaterial { CircularProgressIndicator() } }
            }
            satelliteState.satelliteError != null -> {
                item {
                    SatellitePlaceholderCardMaterial {
                        Text(
                            text = stringResource(R.string.satellite_load_failed, satelliteState.satelliteError),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            locationState.result == null -> {
                item {
                    SatellitePlaceholderCardMaterial {
                        Text(stringResource(R.string.satellite_need_location))
                    }
                }
            }
            filteredSatellites.isEmpty() -> {
                item {
                    SatellitePlaceholderCardMaterial {
                        Text(
                            stringResource(
                                if (filter.isActive) R.string.no_satellites_filtered
                                else R.string.no_satellites
                            )
                        )
                    }
                }
            }
            else -> {
                items(items = sortedSatellites, key = { it.catalogNumber }) { sat ->
                    val (effectiveStatus, isInherited) = statusCache[sat.catalogNumber]
                        ?: (sat.status to false)
                    SatelliteItemMaterial(
                        satellite = sat,
                        effectiveStatus = effectiveStatus,
                        isFavorite = sat.catalogNumber in favorites,
                        isStatusInherited = isInherited,
                        nowMillis = if (sat.isCurrentlyVisible) inPassNowMillis else 0L,
                        statusSegments = satelliteState.segmentStatuses[sat.catalogNumber],
                        onToggleFavorite = { onToggleFavorite(sat.catalogNumber) }
                    )
                }
            }
        }
    }
}

// ---- 数据源刷新卡 ----

@Composable
private fun SatelliteActionCardMaterial(
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
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ActionRowMaterial(
                buttonText = stringResource(R.string.sat_action_get_location),
                isLoading = isLoading,
                enabled = !isLoading,
                onClick = onGetLocation,
                primaryText = if (lastLocationTime != null) {
                    lastLocationTime.atZone(zoneId).format(dateTimeFormatter)
                } else {
                    stringResource(R.string.sat_no_location_time)
                },
                secondaryText = lastLocationCity.ifBlank { stringResource(R.string.sat_no_city) }
            )
            ActionRowMaterial(
                buttonText = stringResource(R.string.sat_action_update_source),
                isLoading = isSatelliteLoading,
                enabled = !isSatelliteLoading,
                onClick = onUpdateSource,
                primaryText = if (lastSatelliteTime != null) {
                    lastSatelliteTime.atZone(zoneId).format(dateTimeFormatter)
                } else {
                    stringResource(R.string.sat_no_source_time)
                },
                secondaryText = if (isSatelliteSourceExpired(lastSatelliteTime)) {
                    stringResource(R.string.sat_source_expired)
                } else {
                    stringResource(R.string.sat_source_fresh)
                },
                secondaryColor = if (isSatelliteSourceExpired(lastSatelliteTime)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@Composable
private fun ActionRowMaterial(
    buttonText: String,
    isLoading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    primaryText: String,
    secondaryText: String,
    secondaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(buttonText)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = primaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodySmall,
                color = secondaryColor,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

// ---- 统计 + 描述 + 筛选入口 ----

@Composable
private fun SatelliteHeaderCardMaterial(
    totalCount: Int,
    filteredCount: Int,
    favoriteCount: Int,
    filter: SatelliteFilter,
    onFilterChange: (SatelliteFilter) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
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
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ManagementStatMaterial(
                    label = stringResource(R.string.satellite_count, totalCount),
                    value = totalCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                ManagementStatMaterial(
                    label = stringResource(R.string.favorites_count),
                    value = favoriteCount.toString(),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (filter.isActive && totalCount > 0) {
                    Text(
                        text = stringResource(R.string.satellite_count_filtered, filteredCount, totalCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                SatelliteFilterButtonMaterial(filter = filter, onFilterChange = onFilterChange)
            }
        }
    }
}

@Composable
private fun ManagementStatMaterial(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
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

// ---- 筛选弹窗 ----

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatelliteFilterButtonMaterial(
    filter: SatelliteFilter,
    onFilterChange: (SatelliteFilter) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = if (filter.isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(10.dp)
                )
                .clickable { expanded = true }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = null,
                tint = if (filter.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.filter_title),
                style = MaterialTheme.typography.labelMedium,
                color = if (filter.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
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
            SatelliteFilterDialogMaterial(
                filter = filter,
                onFilterChange = onFilterChange,
                onDismiss = { expanded = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SatelliteFilterDialogMaterial(
    filter: SatelliteFilter,
    onFilterChange: (SatelliteFilter) -> Unit,
    onDismiss: () -> Unit
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
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
                        fontWeight = FontWeight.Bold
                    )
                    if (filter.isActive) {
                        TextButton(onClick = { onFilterChange(SatelliteFilter()) }) {
                            Text(
                                text = stringResource(R.string.filter_reset),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                HorizontalDivider()

                // 内容区
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
                    FILTER_MODE_OPTIONS_M.forEach { mode ->
                        val selected = mode.value in filter.modes
                        FilterCheckRowMaterial(
                            label = mode.label,
                            checked = selected,
                            onToggle = {
                                val newModes = if (selected) filter.modes - mode.value
                                else filter.modes + mode.value
                                onFilterChange(filter.copy(modes = newModes))
                            }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    FilterSwitchRowMaterial(
                        label = stringResource(R.string.filter_only_in_pass),
                        checked = filter.onlyInPass,
                        onToggle = {
                            onFilterChange(filter.copy(onlyInPass = !filter.onlyInPass, onlyUpcoming = false))
                        }
                    )
                    FilterSwitchRowMaterial(
                        label = stringResource(R.string.filter_only_upcoming),
                        checked = filter.onlyUpcoming,
                        onToggle = {
                            onFilterChange(filter.copy(onlyUpcoming = !filter.onlyUpcoming, onlyInPass = false))
                        }
                    )
                    FilterSwitchRowMaterial(
                        label = stringResource(R.string.filter_only_amsat),
                        checked = filter.onlyAmsat,
                        onToggle = { onFilterChange(filter.copy(onlyAmsat = !filter.onlyAmsat)) }
                    )
                    FilterSwitchRowMaterial(
                        label = stringResource(R.string.filter_only_favorites),
                        checked = filter.onlyFavorites,
                        onToggle = { onFilterChange(filter.copy(onlyFavorites = !filter.onlyFavorites)) }
                    )
                }

                // 底部按钮区
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.filter_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterCheckRowMaterial(label: String, checked: Boolean, onToggle: () -> Unit) {
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
            style = MaterialTheme.typography.bodyMedium
        )
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun FilterSwitchRowMaterial(label: String, checked: Boolean, onToggle: () -> Unit) {
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
            style = MaterialTheme.typography.bodyMedium
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

private data class FilterModeOptionM(val value: String, val label: String)

private val FILTER_MODE_OPTIONS_M = listOf(
    FilterModeOptionM("FM", "FM"),
    FilterModeOptionM("SSTV", "SSTV"),
    FilterModeOptionM("DSTAR", "D-Star"),
    FilterModeOptionM("CW", "CW"),
    FilterModeOptionM("USB", "USB"),
    FilterModeOptionM("LSB", "LSB")
)

// ---- 卫星列表项 ----

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SatelliteItemMaterial(
    satellite: SatelliteInfo,
    effectiveStatus: String,
    isFavorite: Boolean,
    isStatusInherited: Boolean,
    nowMillis: Long,
    statusSegments: List<SegmentStatus>?,
    onToggleFavorite: () -> Unit
) {
    val timeInfo = remember(satellite.aosTime, satellite.losTime, satellite.isCurrentlyVisible, nowMillis) {
        val formatter = satelliteTimeFormatterM
        val zone = ZoneId.systemDefault()
        if (satellite.isCurrentlyVisible) {
            val losTime = satellite.losTime.atZone(zone).format(formatter)
            val now = if (nowMillis > 0) Instant.ofEpochMilli(nowMillis) else Instant.now()
            val remainingSeconds = Duration.between(now, satellite.losTime).seconds
            val remainingText = formatRemainingTimeM(remainingSeconds)
            SatelliteTimeInfoM.InPass(losTime, remainingText)
        } else {
            val aosTime = satellite.aosTime.atZone(zone).format(formatter)
            SatelliteTimeInfoM.Upcoming(aosTime)
        }
    }

    val cardContainerColor = when {
        isFavorite -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }
    val cardContentColor = when {
        isFavorite -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (satellite.isCurrentlyVisible) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else if (isFavorite) {
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
            containerColor = cardContainerColor.copy(alpha = LocalCardAlpha.current),
            contentColor = cardContentColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleFavorite)
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
                                if (satellite.isCurrentlyVisible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline
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
                            imageVector = Icons.Filled.Star,
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
                    IconButton(onClick = onToggleFavorite, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (satellite.source.isNotEmpty()) {
                    SourceChipM(source = satellite.source)
                }
                if (effectiveStatus.isNotEmpty()) {
                    StatusChipM(status = effectiveStatus, isStatusInherited = isStatusInherited)
                }
                if (satellite.modes.isEmpty()) {
                    ModeChipM(mode = stringResource(R.string.mode_unknown))
                } else {
                    satellite.modes.forEach { mode -> ModeChipM(mode = mode) }
                }
            }

            SatelliteStatusSegmentsM(statusSegments)

            Spacer(Modifier.height(10.dp))

            when (timeInfo) {
                is SatelliteTimeInfoM.InPass -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TimeBadgeM(
                            label = stringResource(R.string.los_time),
                            value = timeInfo.losTime,
                            isActive = true
                        )
                        TimeBadgeM(
                            label = stringResource(R.string.time_remaining),
                            value = timeInfo.remainingText,
                            isActive = true
                        )
                    }
                }
                is SatelliteTimeInfoM.Upcoming -> {
                    TimeBadgeM(
                        label = stringResource(R.string.aos_time),
                        value = timeInfo.aosTime,
                        isActive = false
                    )
                }
            }

            if (isFavorite) {
                Spacer(Modifier.height(8.dp))
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

// ---- 时间相关辅助 ----

private val satelliteTimeFormatterM = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private sealed class SatelliteTimeInfoM {
    data class InPass(val losTime: String, val remainingText: String) : SatelliteTimeInfoM()
    data class Upcoming(val aosTime: String) : SatelliteTimeInfoM()
}

private fun formatRemainingTimeM(seconds: Long): String {
    if (seconds <= 0) return "0秒"
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) "${minutes}分${remainingSeconds}秒" else "${remainingSeconds}秒"
}

@Composable
private fun TimeBadgeM(label: String, value: String, isActive: Boolean) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActive) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant

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

// ---- Chips ----

@Composable
private fun SourceChipM(source: String) {
    val (bgColor, contentColor) = when (source) {
        "CT" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "SNOGS" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "ALL" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ChipM(text = source, bgColor = bgColor, contentColor = contentColor)
}

@Composable
private fun StatusChipM(status: String, isStatusInherited: Boolean = false) {
    val baseText = when (status) {
        "Heard" -> stringResource(R.string.status_heard)
        "Telemetry Only" -> stringResource(R.string.status_telemetry_only)
        "Not Heard" -> stringResource(R.string.status_not_heard)
        "Crew Active" -> stringResource(R.string.status_crew_active)
        else -> status
    }
    val displayText = if (isStatusInherited) "$baseText *" else baseText
    val (bgColor, contentColor) = when (status) {
        "Heard" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "Telemetry Only" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "Not Heard" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        "Crew Active" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    val finalBg = if (isStatusInherited) bgColor.copy(alpha = 0.85f) else bgColor
    val finalContent = if (isStatusInherited) contentColor.copy(alpha = 0.85f) else contentColor
    ChipM(text = displayText, bgColor = finalBg, contentColor = finalContent)
}

@Composable
private fun ModeChipM(mode: String) {
    val (bgColor, contentColor) = when (mode.uppercase()) {
        "FM" -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        "SSTV" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "DSTAR" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "CW" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    ChipM(text = mode, bgColor = bgColor, contentColor = contentColor)
}

@Composable
private fun ChipM(text: String, bgColor: Color, contentColor: Color) {
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

// ---- BJT 分段状态时间线 ----

@Composable
private fun SatelliteStatusSegmentsM(segments: List<SegmentStatus>?) {
    if (segments.isNullOrEmpty()) return
    val today = SatelliteStatusSegmenter.dateOf(Instant.now())
    val daySegments = remember(segments, today) {
        SatelliteStatusSegmenter.segmentsForDate(segments, today)
            .ifEmpty { segments.takeLast(4) }
    }
    if (daySegments.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.status_segment_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            daySegments.forEach { seg ->
                SegmentCellM(segment = seg, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SegmentCellM(segment: SegmentStatus, modifier: Modifier = Modifier) {
    val displayText = when (segment.status) {
        "Heard" -> stringResource(R.string.status_heard)
        "Telemetry Only" -> stringResource(R.string.status_telemetry_only)
        "Not Heard" -> stringResource(R.string.status_not_heard)
        "Crew Active" -> stringResource(R.string.status_crew_active)
        null -> stringResource(R.string.status_no_data)
        else -> segment.status
    }
    val bgColor = when (segment.status) {
        "Heard" -> MaterialTheme.colorScheme.primaryContainer
        "Telemetry Only" -> MaterialTheme.colorScheme.secondaryContainer
        "Not Heard" -> MaterialTheme.colorScheme.errorContainer
        "Crew Active" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (segment.status) {
        "Heard" -> MaterialTheme.colorScheme.onPrimaryContainer
        "Telemetry Only" -> MaterialTheme.colorScheme.onSecondaryContainer
        "Not Heard" -> MaterialTheme.colorScheme.onErrorContainer
        "Crew Active" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val rangeLabel = remember(segment.segment) {
        "${segment.segment.startHour.toString().padStart(2, '0')}" +
            "-${segment.segment.endHour.toString().padStart(2, '0')}"
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = rangeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = displayText,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

// ---- 占位卡 ----

@Composable
private fun SatellitePlaceholderCardMaterial(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}
