package com.example.radioarealocator.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.offset
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
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.CheckboxDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.SwitchDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.example.radioarealocator.R
import com.example.radioarealocator.data.LocationResult
import com.example.radioarealocator.data.satellite.SatelliteCatalog
import com.example.radioarealocator.data.satellite.SatelliteInfo
import com.example.radioarealocator.data.satellite.SatelliteStatusTracker
import com.example.radioarealocator.data.satellite.SatelliteStatusSegmenter
import com.example.radioarealocator.data.satellite.SegmentStatus
import com.example.radioarealocator.ui.cw.CWPracticeScreen
import com.example.radioarealocator.ui.cw.FreePracticeSettingsScreen
import com.example.radioarealocator.ui.cw.MorseCodeScreen
import com.example.radioarealocator.ui.cw.PracticeScreen
import com.example.radioarealocator.ui.cw.TutorialListScreen
import com.example.radioarealocator.ui.theme.LocalCardAlpha
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
// 星期格式化：中文 locale 下返回“周三”等短星期
private val weekdayFormatter = DateTimeFormatter.ofPattern("E")
private const val APP_NAME_FONT_SIZE = 32
private const val LOCAL_TIME_FONT_SIZE = 44
private const val UTC_FONT_SIZE_SCALE = 0.4f
// 日期字号：星期与“年/月”部分 14sp，“日”部分放大至 20sp 形成视觉重点
private const val DATE_FONT_SIZE = 14
private const val DATE_DAY_FONT_SIZE = 20

// 每日一言滚动速度（px/秒），控制在 10-15 范围内
private const val QUOTE_SCROLL_SPEED_PX_PER_SEC = 12f
// 滚动到端点后暂停时长（毫秒）
private const val QUOTE_PAUSE_MS = 2000L
// 子页面切换动画时长（毫秒）：前进/返回滑入滑出统一时长
private const val NAV_ANIM_DURATION_MS = 300
// 关于页淡入淡出动画时长（毫秒）
private const val ABOUT_ANIM_DURATION_MS = 250

@Suppress("UnusedPrivateMember")
private val navSlideTween = tween<IntOffset>(NAV_ANIM_DURATION_MS)
private val navFadeTween = tween<Float>(NAV_ANIM_DURATION_MS)
private val aboutFadeTween = tween<Float>(ABOUT_ANIM_DURATION_MS)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onRequestPermission: () -> Unit
) {
    val locationState by viewModel.locationState
    val satelliteState by viewModel.satelliteState
    val favorites by viewModel.favoriteSatellites
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAbout by remember { mutableStateOf(false) }
    // 主页子页面：0=列表, 1=定位详情, 2=卫星详情, 3=卫星管理（三级）
    var homeSubScreen by rememberSaveable { mutableIntStateOf(0) }
    // 设置 tab 下的子页面：0=设置主页, 1=提醒列表
    var settingsSubScreen by rememberSaveable { mutableIntStateOf(0) }
    // CW 练习子页面：0=主页, 1=自由练习设置, 2=教程列表, 3=练习页面
    var cwSubScreen by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // 监听提醒反馈消息，自动显示 SnackBar
    val feedback by viewModel.reminderFeedback
    androidx.compose.runtime.LaunchedEffect(feedback) {
        feedback?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeReminderFeedback()
        }
    }

    // 启动时初始化：从缓存加载 TLE，必要时后台拉取；
    // 若已授予定位权限，自动发起首次定位以启动持续位置监听，
    // 确保设备位置变化能被实时捕获并更新到界面
    LaunchedEffect(Unit) {
        viewModel.initializeIfNeeded()
        if (viewModel.hasLocationPermission) {
            viewModel.refreshLocation()
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

    BackHandler(
        enabled = showAbout ||
            (selectedTab == 0 && (homeSubScreen != 0 || cwSubScreen != 0)) ||
            (selectedTab == 1 && settingsSubScreen != 0)
    ) {
        when {
            showAbout -> showAbout = false
            selectedTab == 0 && homeSubScreen == 3 -> homeSubScreen = 2
            selectedTab == 0 && cwSubScreen != 0 -> {
                if (cwSubScreen == 3) viewModel.stopCWPractice()
                // 练习页按来源返回：课程练习回课程列表，自由练习回设置页
                cwSubScreen = when {
                    cwSubScreen != 3 -> 0
                    viewModel.currentCourseId.value > 0 -> 2
                    else -> 1
                }
            }
            selectedTab == 0 && homeSubScreen != 0 -> homeSubScreen = 0
            selectedTab == 1 && settingsSubScreen != 0 -> settingsSubScreen = 0
        }
    }

    // 用 Box 叠加：底层是主 Scaffold，顶层是 AboutScreen 浮层。
    // AboutScreen 通过淡入淡出过渡显示，避免瞬时硬切。
    // navKey 同时驱动 TopAppBar Crossfade 与主内容区 AnimatedContent，保证视觉同步。
    val navKey = listOf(selectedTab, homeSubScreen, settingsSubScreen, cwSubScreen)
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            Crossfade(
                targetState = navKey,
                animationSpec = tween(NAV_ANIM_DURATION_MS),
                label = "TopBarCrossfade"
            ) { key ->
                val tab = key[0]; val home = key[1]; val settings = key[2]; val cw = key[3]
                when {
                tab == 0 && home == 0 -> HomeHeader(
                    locationState = locationState,
                    satellites = satelliteState.satellites,
                    weather = viewModel.weather.value,
                    weatherLoading = viewModel.weatherLoading.value,
                    weatherError = viewModel.weatherError.value,
                    onRefreshWeather = { viewModel.refreshWeather(force = true) },
                    dailyQuote = viewModel.dailyQuote.value
                )
                tab == 0 && home == 1 -> TopAppBar(
                    title = stringResource(R.string.home_location),
                    navigationIcon = {
                        IconButton(onClick = { homeSubScreen = 0 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                tab == 0 && home == 3 -> TopAppBar(
                    title = stringResource(R.string.satellite_management),
                    navigationIcon = {
                        IconButton(onClick = { homeSubScreen = 2 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                tab == 0 && home == 2 -> TopAppBar(
                    title = stringResource(R.string.home_satellite),
                    navigationIcon = {
                        IconButton(onClick = { homeSubScreen = 0 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                tab == 0 && home == 4 -> TopAppBar(
                    title = when (cw) {
                        0 -> stringResource(R.string.cw_practice)
                        1 -> stringResource(R.string.free_practice)
                        2 -> stringResource(R.string.tutorial_practice)
                        3 -> stringResource(R.string.practice)
                        4 -> stringResource(R.string.morsecode_title)
                        else -> stringResource(R.string.cw_practice)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (cw == 0) homeSubScreen = 0
                            else if (cw == 3) {
                                viewModel.stopCWPractice()
                                // 按来源返回：课程练习回课程列表，自由练习回设置页
                                cwSubScreen = if (viewModel.currentCourseId.value > 0) 2 else 1
                            }
                            else cwSubScreen = 0
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                tab == 1 && settings == 1 -> TopAppBar(
                    title = stringResource(R.string.reminder_list_title),
                    navigationIcon = {
                        IconButton(onClick = { settingsSubScreen = 0 }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.back)
                            )
                        }
                    }
                )
                else -> TopAppBar(
                    title = stringResource(R.string.settings)
                )
                }
            }
        },
        snackbarHost = {
            SnackbarHost(state = snackbarHostState)
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = Icons.Default.Home,
                    label = stringResource(R.string.home)
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        if (selectedTab == 0 && cwSubScreen == 3) viewModel.stopCWPractice()
                        selectedTab = 1
                    },
                    icon = Icons.Default.Settings,
                    label = stringResource(R.string.settings)
                )
            }
        }
    ) { padding ->
        // navKey 已在外层声明。深度比较：targetDepth >= initialDepth 视为前进（右入左出），否则视为返回（左入右出）。
        AnimatedContent(
            targetState = navKey,
            transitionSpec = {
                // initialState/targetState 由 AnimatedContentTransitionScope 提供。
                // CW 子页面（home==4）叠加 cwSubScreen 深度：0=列表, 1/2/4=二级页, 3=练习页，
                // 否则 CW 内部跳转 depth 恒等，返回动画方向错误
                fun depthOf(state: List<Int>): Int {
                    if (state[0] != 0) return 10 + state[2]
                    if (state[1] != 4) return state[1]
                    return 4 + when (state[3]) {
                        0 -> 0
                        3 -> 2
                        else -> 1
                    }
                }
                val initialDepth = depthOf(initialState)
                val targetDepth = depthOf(targetState)
                val forward = targetDepth >= initialDepth
                val enter = if (forward) {
                    slideInHorizontally(animationSpec = navSlideTween) { fullWidth -> fullWidth } +
                        fadeIn(animationSpec = navFadeTween)
                } else {
                    slideInHorizontally(animationSpec = navSlideTween) { fullWidth -> -fullWidth } +
                        fadeIn(animationSpec = navFadeTween)
                }
                val exit = if (forward) {
                    slideOutHorizontally(animationSpec = navSlideTween) { fullWidth -> -fullWidth } +
                        fadeOut(animationSpec = navFadeTween)
                } else {
                    slideOutHorizontally(animationSpec = navSlideTween) { fullWidth -> fullWidth } +
                        fadeOut(animationSpec = navFadeTween)
                }
                enter togetherWith exit
            },
            contentKey = { it },
            label = "MainNavTransition"
        ) { key ->
            val tab = key[0]; val home = key[1]; val settings = key[2]; val cw = key[3]
            if (tab == 0) {
                when (home) {
                    0 -> HomeListContent(
                        onLocationClick = { homeSubScreen = 1 },
                        onSatelliteClick = { homeSubScreen = 2 },
                        onCWPracticeClick = { homeSubScreen = 4 },
                        contentPadding = padding
                    )
                    1 -> LocationDetailContent(
                        locationState = locationState,
                        hasLocationPermission = viewModel.hasLocationPermission,
                        onRequestPermission = onRequestPermission,
                        onRefresh = { viewModel.refreshLocation() },
                        onDismissError = { viewModel.dismissError() },
                        contentPadding = padding
                    )
                    2 -> SatelliteDetailContent(
                        locationState = locationState,
                        satelliteState = satelliteState,
                        filter = viewModel.satelliteFilter.value,
                        onFilterChange = viewModel::updateSatelliteFilter,
                        onGetLocation = { viewModel.refreshLocationOnly() },
                        onUpdateSource = { viewModel.refreshSatelliteSourceOnly() },
                        favorites = favorites,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onOpenManagement = { homeSubScreen = 3 },
                        statusTracker = viewModel.statusTracker,
                        contentPadding = padding
                    )
                    3 -> SatelliteManagementContent(
                        locationState = locationState,
                        satelliteState = satelliteState,
                        favorites = favorites,
                        onToggleFavorite = viewModel::toggleFavorite,
                        contentPadding = padding
                    )
                    4 -> when (cw) {
                        0 -> CWPracticeScreen(
                            onBackClick = { homeSubScreen = 0 },
                            onFreePracticeClick = { cwSubScreen = 1 },
                            onTutorialClick = {
                                viewModel.loadAllCourseProgress()
                                cwSubScreen = 2
                            },
                            onMorseCodeClick = { cwSubScreen = 4 },
                            contentPadding = padding
                        )
                        1 -> FreePracticeSettingsScreen(
                            settings = viewModel.cwSettings.value,
                            onSettingsChange = { viewModel.updateCWSettings(it) },
                            onStartPractice = {
                                // 重置为自由练习模式并按当前设置生成文本，
                                // 避免残留上次课程练习的标题/课时/文本状态
                                viewModel.generateCWPracticeText()
                                cwSubScreen = 3
                            },
                            contentPadding = padding
                        )
                        2 -> TutorialListScreen(
                            onLessonClick = { lessonId ->
                                viewModel.generateTutorialText(lessonId)
                                cwSubScreen = 3
                            },
                            courseProgress = viewModel.courseProgress.value,
                            contentPadding = padding
                        )
                        3 -> PracticeScreen(
                            currentText = viewModel.cwCurrentText.value,
                            morseCode = viewModel.cwMorseCode.value,
                            userInput = viewModel.cwUserInput.value,
                            isPlaying = viewModel.cwIsPlaying.value,
                            isPaused = viewModel.cwIsPaused.value,
                            accuracy = viewModel.cwAccuracy.value,
                            courseTitle = viewModel.currentCourseTitle.value,
                            lessonInfo = viewModel.currentLessonInfo.value,
                            isTutorialMode = viewModel.currentCourseId.value > 0,
                            onUserInputChange = { viewModel.updateCWUserInput(it) },
                            onGenerateText = { viewModel.generateCWPracticeText() },
                            onStartPractice = { viewModel.startCWPractice() },
                            onPausePractice = { viewModel.pauseCWPractice() },
                            onResumePractice = { viewModel.resumeCWPractice() },
                            onStopPractice = { viewModel.stopCWPractice() },
                            onCheckResults = { viewModel.checkCWResults() },
                            contentPadding = padding
                        )
                        4 -> MorseCodeScreen(
                            bgPage = MiuixTheme.colorScheme.background,
                            bgCard = MiuixTheme.colorScheme.surface,
                            primaryColor = MiuixTheme.colorScheme.primary,
                            textPrimary = MiuixTheme.colorScheme.onSurface,
                            textSecondary = MiuixTheme.colorScheme.onSurfaceSecondary,
                            contentPadding = padding
                        )
                    }
                }
            } else {
                // 设置 tab：根据 settingsSubScreen 切换主页 / 提醒列表
                if (settings == 1) {
                    ReminderListScreen(
                        items = viewModel.reminderItems.value,
                        onToggleEnabled = viewModel::setReminderItemEnabled,
                        onDelete = viewModel::deleteReminderItem,
                        contentPadding = padding
                    )
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
                        cardOpacity = viewModel.cardOpacity.value,
                        onCardOpacityChange = { viewModel.setCardOpacity(it) },
                        backgroundOpacity = viewModel.backgroundOpacity.value,
                        onBackgroundOpacityChange = { viewModel.setBackgroundOpacity(it) },
                        onAboutClick = { showAbout = true },
                        reminderSettings = viewModel.reminderSettings.value,
                        onUpdateReminderSettings = viewModel::updateReminderSettings,
                        onOpenReminderList = { settingsSubScreen = 1 },
                        contentPadding = padding
                    )
                }
            }
        }
    }

    // 关于页作为顶层覆盖，淡入淡出过渡。
    // visible=false 时 AnimatedVisibility 仍会播放 exit 动画，期间 AboutScreen 在最上层覆盖。
    AnimatedVisibility(
        visible = showAbout,
        enter = fadeIn(animationSpec = aboutFadeTween),
        exit = fadeOut(animationSpec = aboutFadeTween)
    ) {
        AboutScreen(onBackClick = { showAbout = false })
    }
    }  // Box 闭合
}

/**
 * 主页头部：应用名位于左上（类似应用标题），下方纵向排列本地时间卡片 + 天气卡片。
 * 时间区域与天气区域均带背景色，与状态色联动；天气卡片位于时间卡片正下方，
 * 视觉层级清晰，符合从上到下的阅读习惯。
 */
@Composable
private fun HomeHeader(
    locationState: LocationUiState,
    satellites: List<SatelliteInfo>,
    weather: com.example.radioarealocator.data.weather.WeatherResult?,
    weatherLoading: Boolean,
    weatherError: String?,
    onRefreshWeather: () -> Unit,
    dailyQuote: String
) {
    // 时钟状态，每秒刷新 —— 仅 HomeHeader 内部持有，避免 1s tick 触发整个 MainScreen 重组
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Instant.now()
            delay(1000)
        }
    }

    val stateColor = if (locationState.result != null) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.outline
    }
    val appNameSize = APP_NAME_FONT_SIZE.sp
    val localTimeSize = LOCAL_TIME_FONT_SIZE.sp
    val utcSize = (LOCAL_TIME_FONT_SIZE * UTC_FONT_SIZE_SCALE).sp

    val zonedNow = now.atZone(ZoneId.systemDefault())
    val localTime = zonedNow.format(timeFormatter)
    val utcTime = now.atZone(ZoneOffset.UTC).format(timeFormatter)
    // 日期：第一行星期几（14sp），第二行“年 月 日”，其中“日”部分放大至 20sp
    val weekday = zonedNow.format(weekdayFormatter)
    val dateYearMonth = "${zonedNow.year}年 ${zonedNow.monthValue}月 "
    val dateDayText = "${zonedNow.dayOfMonth}日"
    val dateLine = remember(dateYearMonth, dateDayText) {
        buildAnnotatedString {
            append(dateYearMonth)
            withStyle(SpanStyle(fontSize = DATE_DAY_FONT_SIZE.sp)) {
                append(dateDayText)
            }
        }
    }
    // 下颗即将过境的卫星（AOS 在未来且非当前在境），用于显示过境天气预测
    val nextSatellite = satellites.firstOrNull {
        !it.isCurrentlyVisible && it.aosTime.isAfter(now)
    }

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
        // 时间 + 天气 纵向排列：时间卡片在上，天气卡片在其正下方
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 时间卡片
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(stateColor.copy(alpha = 0.12f * LocalCardAlpha.current))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 本地时间 + 日期并排：时间在左，月/日（两行）在右
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = localTime,
                        style = TextStyle(
                            fontSize = localTimeSize,
                            fontWeight = FontWeight.Normal
                        ),
                        color = stateColor,
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = weekday,
                            style = TextStyle(
                                fontSize = DATE_FONT_SIZE.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = stateColor
                        )
                        Text(
                            text = dateLine,
                            style = TextStyle(
                                fontSize = DATE_FONT_SIZE.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = stateColor
                        )
                    }
                }
                Text(
                    text = "$utcTime UTC",
                    style = TextStyle(fontSize = utcSize),
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    textAlign = TextAlign.Start
                )
                // 每日一言：超宽时水平滚动，到端点暂停 2 秒后反向
                DailyQuoteScroller(
                    quote = dailyQuote,
                    contentColor = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
            // 天气卡片：位于时间卡片正下方，占满宽度
            WeatherCard(
                weather = weather,
                isLoading = weatherLoading,
                error = weatherError,
                nextSatellite = nextSatellite,
                onRefresh = onRefreshWeather,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * 每日一言水平滚动组件。
 *
 * 当文本宽度超过容器宽度时，以 [QUOTE_SCROLL_SPEED_PX_PER_SEC] px/秒 速度水平滚动，
 * 到达端点后暂停 [QUOTE_PAUSE_MS] 再反向滚动，循环往复；文本不超宽时静态居左显示。
 * 使用 [Animatable] 驱动平滑动画，避免逐帧重组造成的卡顿。
 */
@Composable
private fun DailyQuoteScroller(
    quote: String,
    contentColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    if (quote.isBlank()) return
    var textWidthPx by remember { mutableStateOf(0) }
    BoxWithConstraints(
        modifier = modifier.clipToBounds()
    ) {
        val containerWidthPx = constraints.maxWidth
        val scrollRangePx = (textWidthPx - containerWidthPx).coerceAtLeast(0)
        val offsetAnim = remember(scrollRangePx) { Animatable(0f) }
        LaunchedEffect(scrollRangePx) {
            if (scrollRangePx <= 0) return@LaunchedEffect
            // 单程时长 = 距离 / 速度（秒→毫秒），保证平滑无卡顿
            val durationMs = (scrollRangePx / QUOTE_SCROLL_SPEED_PX_PER_SEC * 1000)
                .toInt()
                .coerceAtLeast(1)
            while (true) {
                offsetAnim.animateTo(-scrollRangePx.toFloat(), tween(durationMs))
                delay(QUOTE_PAUSE_MS)
                offsetAnim.animateTo(0f, tween(durationMs))
                delay(QUOTE_PAUSE_MS)
            }
        }
        Text(
            text = quote,
            maxLines = 1,
            softWrap = false,
            color = contentColor,
            style = TextStyle(fontSize = 13.sp),
            modifier = Modifier
                .offset { IntOffset(offsetAnim.value.roundToInt(), 0) }
                .onSizeChanged { if (it.width != textWidthPx) textWidthPx = it.width }
        )
    }
}

/**
 * 主页列表：定位、卫星两个入口。
 */
@Composable
private fun HomeListContent(
    onLocationClick: () -> Unit,
    onSatelliteClick: () -> Unit,
    onCWPracticeClick: () -> Unit,
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
        item {
            HomeListItem(
                title = stringResource(R.string.cw_practice),
                description = stringResource(R.string.cw_practice_desc),
                badgeChar = "CW",
                onClick = onCWPracticeClick
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
        colors = CardDefaults.defaultColors(
            // 提亮 25%（保持色相一致），凸显"定位"/"卫星"两个导航入口卡片
            color = lerp(MiuixTheme.colorScheme.surface, if (isSystemInDarkTheme()) Color.Black else Color.White, 0.25f).copy(alpha = LocalCardAlpha.current),
            contentColor = MiuixTheme.colorScheme.onSurface
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
                    .background(MiuixTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeChar,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MiuixTheme.textStyles.title2,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun LocationDetailContent(
    locationState: LocationUiState,
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
                isLoading = locationState.isLoading,
                result = locationState.result,
                hasPermission = hasLocationPermission,
                onRequestPermission = onRequestPermission,
                onRefresh = onRefresh
            )
        }

        if (locationState.result != null) {
            item {
                ZoneInfoCard(result = locationState.result)
            }
            item {
                AMapCard(
                    latitude = locationState.result.latitude,
                    longitude = locationState.result.longitude
                )
            }
        }
    }

    locationState.error?.let { message ->
        ErrorDialog(message = message, onDismiss = onDismissError)
    }
}

@Composable
private fun SatelliteDetailContent(
    locationState: LocationUiState,
    satelliteState: SatelliteUiState,
    filter: SatelliteFilter,
    onFilterChange: (SatelliteFilter) -> Unit,
    onGetLocation: () -> Unit,
    onUpdateSource: () -> Unit,
    favorites: Set<Int>,
    onToggleFavorite: (Int) -> Unit,
    onOpenManagement: () -> Unit,
    statusTracker: SatelliteStatusTracker,
    contentPadding: PaddingValues
) {
    // 订阅跟踪器状态字典：AMSAT 状态字典变化（5 分钟刷新或跨槽延续）时触发重组，
    // 使每颗卫星的 isStatusInherited 标记与有效状态值得以及时更新
    @Suppress("UnusedVariable")
    val statusEntries = statusTracker.statusMap.value
    val filteredSatellites = remember(satelliteState.satellites, filter, favorites) {
        satelliteState.satellites.applyFilter(filter, favorites)
    }
    val totalCount = satelliteState.satellites.size

    // 统一的倒计时时钟：仅当有在境卫星时才每秒更新，
    // 避免每颗在境卫星各自启动 LaunchedEffect 导致 N 次/秒重组
    var inPassNowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val hasInPassSatellites = filteredSatellites.any { it.isCurrentlyVisible }
    LaunchedEffect(hasInPassSatellites) {
        if (hasInPassSatellites) {
            while (true) {
                inPassNowMillis = System.currentTimeMillis()
                delay(1000)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
    ) {
        item {
            SatelliteActionCard(
                isLoading = locationState.isLoading,
                isSatelliteLoading = satelliteState.isSatelliteLoading,
                lastLocationTime = locationState.lastLocationUpdateTime,
                lastLocationCity = locationState.lastLocationCity,
                lastSatelliteTime = satelliteState.lastSatelliteUpdateTime,
                onGetLocation = onGetLocation,
                onUpdateSource = onUpdateSource
            )
        }
        item {
            SatelliteSectionHeader(
                isLoading = satelliteState.isSatelliteLoading,
                filteredCount = filteredSatellites.size,
                totalCount = totalCount,
                isActive = filter.isActive,
                filter = filter,
                onFilterChange = onFilterChange,
                onOpenManagement = onOpenManagement
            )
        }
        when {
            satelliteState.isSatelliteLoading && filteredSatellites.isEmpty() -> {
                item {
                    SatellitePlaceholderCard { CircularProgressIndicator() }
                }
            }
            satelliteState.satelliteError != null -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(R.string.satellite_load_failed, satelliteState.satelliteError),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.error
                        )
                    }
                }
            }
            locationState.result == null -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(R.string.satellite_need_location),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
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
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }
            else -> {
                items(
                    items = filteredSatellites,
                    key = { it.catalogNumber }
                ) { sat ->
                    // 查询状态跟踪器：获取实时状态值与延续标记。
                    // 跟踪器无该卫星记录时（首次抓取前或长期无报告）回退到卫星源附带的初始状态
                    val amsatName = SatelliteCatalog.AMSAT_STATUS_NAME_BY_CATALOG_NUMBER[sat.catalogNumber]
                    val statusQuery = if (amsatName != null) statusTracker.queryStatus(amsatName) else null
                    val effectiveStatus = statusQuery?.status?.takeIf { it.isNotBlank() } ?: sat.status
                    val isInherited = statusQuery?.isInherited ?: false
                    SatelliteItem(
                        satellite = sat.copy(status = effectiveStatus),
                        isFavorite = sat.catalogNumber in favorites,
                        onToggleFavorite = { onToggleFavorite(sat.catalogNumber) },
                        nowMillis = if (sat.isCurrentlyVisible) inPassNowMillis else 0L,
                        isStatusInherited = isInherited,
                        statusSegments = satelliteState.segmentStatuses[sat.catalogNumber]
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
    locationState: LocationUiState,
    satelliteState: SatelliteUiState,
    favorites: Set<Int>,
    onToggleFavorite: (Int) -> Unit,
    contentPadding: PaddingValues
) {
    val satellites = satelliteState.satellites
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
                modifier = Modifier
                    .fillMaxWidth(),
                colors = CardDefaults.defaultColors(
                    color = MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
                    contentColor = MiuixTheme.colorScheme.onSurface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.satellite_management_desc),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
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
            satelliteState.isSatelliteLoading && satellites.isEmpty() -> {
                item {
                    SatellitePlaceholderCard { CircularProgressIndicator() }
                }
            }
            locationState.result == null -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(R.string.satellite_need_location),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
                        )
                    }
                }
            }
            satellites.isEmpty() -> {
                item {
                    SatellitePlaceholderCard {
                        Text(
                            text = stringResource(R.string.no_satellites),
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary
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
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
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
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = if (isFavorite) {
                MiuixTheme.colorScheme.tertiaryContainer.copy(alpha = LocalCardAlpha.current)
            } else {
                MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current)
            },
            contentColor = if (isFavorite) {
                MiuixTheme.colorScheme.onTertiaryContainer
            } else {
                MiuixTheme.colorScheme.onSurface
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
                    MiuixTheme.colorScheme.tertiaryContainer
                } else {
                    MiuixTheme.colorScheme.onSurfaceSecondary
                }
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = satellite.name,
                    style = MiuixTheme.textStyles.title4,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (satellite.isCurrentlyVisible) {
                        "${stringResource(R.string.in_pass)} · $timeText"
                    } else {
                        "${stringResource(R.string.aos_time)}：$timeText"
                    },
                    style = MiuixTheme.textStyles.footnote1,
                    color = if (isFavorite) {
                        MiuixTheme.colorScheme.onTertiaryContainer
                    } else {
                        MiuixTheme.colorScheme.onSurfaceSecondary
                    }
                )
            }
            if (isFavorite) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(MiuixTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = stringResource(R.string.favorited),
                        style = MiuixTheme.textStyles.footnote2,
                        color = MiuixTheme.colorScheme.onTertiaryContainer
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
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
            contentColor = MiuixTheme.colorScheme.onSurface
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
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
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
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = lastLocationCity.ifBlank { stringResource(R.string.sat_no_city) },
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                    enabled = !isSatelliteLoading
                ) {
                    if (isSatelliteLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
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
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    val expired = isSatelliteSourceExpired(lastSatelliteTime)
                    Text(
                        text = if (expired) {
                            stringResource(R.string.sat_source_expired)
                        } else {
                            stringResource(R.string.sat_source_fresh)
                        },
                        style = MiuixTheme.textStyles.footnote1,
                        color = if (expired) {
                            MiuixTheme.colorScheme.error
                        } else {
                            MiuixTheme.colorScheme.onSurfaceSecondary
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
    val color = if (isSuccess) {
        MiuixTheme.colorScheme.primaryContainer
    } else {
        MiuixTheme.colorScheme.surface
    }
    val contentColor = if (isSuccess) {
        MiuixTheme.colorScheme.onPrimaryContainer
    } else {
        MiuixTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = color.copy(alpha = LocalCardAlpha.current),
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
                            if (isSuccess) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.outline
                        )
                )
                Text(
                    text = if (isSuccess) stringResource(R.string.location_success) else stringResource(R.string.location_status),
                    style = MiuixTheme.textStyles.body1,
                    color = contentColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                result != null -> {
                    Text(
                        text = "%.5f".format(result.latitude),
                        style = MiuixTheme.textStyles.headline1,
                        fontWeight = FontWeight.Bold,
                        color = contentColor
                    )
                    Text(
                        text = "%.5f".format(result.longitude),
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (isLoading) {
                        // 刷新中：保留显示旧位置，同时在下方显示 loading 指示，
                        // 避免位置突然消失导致用户误以为"刷新失灵"
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.locating),
                            style = MiuixTheme.textStyles.body1,
                            color = contentColor
                        )
                    } else {
                        Button(
                            onClick = onRefresh
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
                }

                isLoading -> {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.locating),
                        style = MiuixTheme.textStyles.body1,
                        color = contentColor
                    )
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
                        style = MiuixTheme.textStyles.body1,
                        color = contentColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = if (hasPermission) onRefresh else onRequestPermission
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
        modifier = Modifier
            .fillMaxWidth(),
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
                text = stringResource(R.string.zone_info),
                style = MiuixTheme.textStyles.title2,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
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
                HorizontalDivider(color = MiuixTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "${stringResource(R.string.address)}：",
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurfaceSecondary
                    )
                    Text(
                        text = result.address,
                        style = MiuixTheme.textStyles.body1,
                        color = MiuixTheme.colorScheme.onSurface,
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
            style = MiuixTheme.textStyles.title3,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
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
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface
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
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceSecondary
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
@Composable
private fun SatelliteFilterPopup(
    filter: SatelliteFilter,
    onFilterChange: (SatelliteFilter) -> Unit,
    onOpenManagement: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = 1.dp,
                    color = if (filter.isActive) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.outline
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
                    MiuixTheme.colorScheme.primary
                } else {
                    MiuixTheme.colorScheme.onSurfaceSecondary
                }
            )
            if (filter.isActive) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MiuixTheme.colorScheme.primary)
                )
            }
        }
        if (expanded) {
            OverlayDialog(
                show = true,
                title = stringResource(R.string.filter_title),
                onDismissRequest = { expanded = false }
            ) {
                // Title row with close/dismiss
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                            Text(
                                text = stringResource(R.string.filter_title),
                                style = MiuixTheme.textStyles.title2,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            if (filter.isActive) {
                                TextButton(
                                    text = stringResource(R.string.filter_reset),
                                    onClick = { onFilterChange(SatelliteFilter()) },
                                )
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
                                style = MiuixTheme.textStyles.body1,
                                color = MiuixTheme.colorScheme.onSurfaceSecondary,
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
                            Button(
                                onClick = {
                                    expanded = false
                                    onOpenManagement()
                                },
                                colors = ButtonDefaults.buttonColorsSecondary()
                            ) {
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
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface
        )
        Checkbox(
            state = if (checked) ToggleableState.On else ToggleableState.Off,
            onClick = { onToggle() },
            colors = CheckboxDefaults.checkboxColors()
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.switchColors(
                checkedTrackColor = MiuixTheme.colorScheme.primary
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
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.surface.copy(alpha = LocalCardAlpha.current),
            contentColor = MiuixTheme.colorScheme.onSurface
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
    onToggleFavorite: () -> Unit = {},
    nowMillis: Long = 0L,
    isStatusInherited: Boolean = false,
    statusSegments: List<SegmentStatus>? = null
) {
    val timeInfo = remember(satellite.aosTime, satellite.losTime, satellite.isCurrentlyVisible, nowMillis) {
        val formatter = satelliteTimeFormatter
        val zone = ZoneId.systemDefault()
        if (satellite.isCurrentlyVisible) {
            val losTime = satellite.losTime.atZone(zone).format(formatter)
            // 使用父级传入的 nowMillis 计算剩余时间，避免每颗卫星各自维护时钟
            val now = if (nowMillis > 0) Instant.ofEpochMilli(nowMillis) else Instant.now()
            val remainingSeconds = Duration.between(now, satellite.losTime).seconds
            val remainingText = formatRemainingTime(remainingSeconds)
            SatelliteTimeInfo.InPass(losTime, remainingText)
        } else {
            val aosTime = satellite.aosTime.atZone(zone).format(formatter)
            SatelliteTimeInfo.Upcoming(aosTime)
        }
    }

    // 关注卫星在境内/即将过境时使用 tertiary 系列强调色；境内优先用 primaryContainer
    val cardContainerColor = when {
        satellite.isCurrentlyVisible -> MiuixTheme.colorScheme.primaryContainer
        isFavorite -> MiuixTheme.colorScheme.tertiaryContainer
        else -> MiuixTheme.colorScheme.surface
    }
    val cardContentColor = when {
        satellite.isCurrentlyVisible -> MiuixTheme.colorScheme.onPrimaryContainer
        isFavorite -> MiuixTheme.colorScheme.onTertiaryContainer
        else -> MiuixTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isFavorite) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MiuixTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.defaultColors(
            color = cardContainerColor.copy(alpha = LocalCardAlpha.current),
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
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.outline
                                }
                            )
                    )
                    Text(
                        text = satellite.name,
                        style = MiuixTheme.textStyles.title4,
                        fontWeight = FontWeight.Bold,
                        color = cardContentColor
                    )
                    if (isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.tertiaryContainer,
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
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.SemiBold,
                        color = cardContentColor
                    )
                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isFavorite) {
                                MiuixTheme.colorScheme.tertiaryContainer
                            } else {
                                MiuixTheme.colorScheme.onSurfaceSecondary
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
                    StatusChip(status = satellite.status, isStatusInherited = isStatusInherited)
                }
                if (satellite.modes.isEmpty()) {
                    ModeChip(mode = stringResource(R.string.mode_unknown))
                } else {
                    satellite.modes.forEach { mode ->
                        ModeChip(mode = mode)
                    }
                }
            }

            SatelliteStatusSegments(statusSegments)

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
    val color = if (isActive) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)
    } else {
        MiuixTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (isActive) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurfaceSecondary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$label：",
            style = MiuixTheme.textStyles.footnote2,
            color = contentColor
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body1,
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
        "CT" -> MiuixTheme.colorScheme.secondaryContainer to MiuixTheme.colorScheme.onSecondaryContainer
        "SNOGS" -> MiuixTheme.colorScheme.tertiaryContainer to MiuixTheme.colorScheme.onTertiaryContainer
        "ALL" -> MiuixTheme.colorScheme.primaryContainer to MiuixTheme.colorScheme.onPrimaryContainer
        else -> MiuixTheme.colorScheme.surfaceVariant to MiuixTheme.colorScheme.onSurfaceSecondary
    }
    Chip(text = source, bgColor = bgColor, contentColor = contentColor)
}

@Composable
private fun StatusChip(status: String, isStatusInherited: Boolean = false) {
    val baseText = when (status) {
        "Heard" -> stringResource(R.string.status_heard)
        "Telemetry Only" -> stringResource(R.string.status_telemetry_only)
        "Not Heard" -> stringResource(R.string.status_not_heard)
        "Crew Active" -> stringResource(R.string.status_crew_active)
        else -> status
    }
    // 延续状态：追加右侧小星号标识，便于用户识别"非本时段实时报告"
    val displayText = if (isStatusInherited) "$baseText *" else baseText
    val (bgColor, contentColor) = when (status) {
        "Heard" -> MiuixTheme.colorScheme.primaryContainer to MiuixTheme.colorScheme.onPrimaryContainer
        "Telemetry Only" -> MiuixTheme.colorScheme.secondaryContainer to MiuixTheme.colorScheme.onSecondaryContainer
        "Not Heard" -> MiuixTheme.colorScheme.errorContainer to MiuixTheme.colorScheme.onErrorContainer
        "Crew Active" -> MiuixTheme.colorScheme.tertiaryContainer to MiuixTheme.colorScheme.onTertiaryContainer
        else -> MiuixTheme.colorScheme.surfaceVariant to MiuixTheme.colorScheme.onSurfaceSecondary
    }
    // 延续状态：降低 15% 透明度，与实时状态形成视觉层级区分
    val finalBg = if (isStatusInherited) bgColor.copy(alpha = 0.85f) else bgColor
    val finalContent = if (isStatusInherited) contentColor.copy(alpha = 0.85f) else contentColor
    Chip(text = displayText, bgColor = finalBg, contentColor = finalContent)
}

/**
 * 卫星 BJT 分段运行状态展示（4 个 6 小时时段，含延续）。
 *
 * 无分段数据时不渲染；优先展示"今天"的 4 个时段，否则展示时间线中最近一天的时段。
 */
@Composable
private fun SatelliteStatusSegments(segments: List<SegmentStatus>?) {
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
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceSecondary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            daySegments.forEach { seg ->
                SegmentCell(
                    segment = seg,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SegmentCell(segment: SegmentStatus, modifier: Modifier = Modifier) {
    val displayText = when (segment.status) {
        "Heard" -> stringResource(R.string.status_heard)
        "Telemetry Only" -> stringResource(R.string.status_telemetry_only)
        "Not Heard" -> stringResource(R.string.status_not_heard)
        "Crew Active" -> stringResource(R.string.status_crew_active)
        null -> stringResource(R.string.status_no_data)
        else -> segment.status
    }
    val bgColor = when (segment.status) {
        "Heard" -> MiuixTheme.colorScheme.primaryContainer
        "Telemetry Only" -> MiuixTheme.colorScheme.secondaryContainer
        "Not Heard" -> MiuixTheme.colorScheme.errorContainer
        "Crew Active" -> MiuixTheme.colorScheme.tertiaryContainer
        else -> MiuixTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (segment.status) {
        "Heard" -> MiuixTheme.colorScheme.onPrimaryContainer
        "Telemetry Only" -> MiuixTheme.colorScheme.onSecondaryContainer
        "Not Heard" -> MiuixTheme.colorScheme.onErrorContainer
        "Crew Active" -> MiuixTheme.colorScheme.onTertiaryContainer
        else -> MiuixTheme.colorScheme.onSurfaceSecondary
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
            style = MiuixTheme.textStyles.footnote2,
            color = contentColor,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = displayText,
            style = MiuixTheme.textStyles.footnote2,
            color = contentColor
        )
        if (segment.carriedOver) {
            Text(
                text = stringResource(R.string.status_segment_carryover),
                style = MiuixTheme.textStyles.footnote2,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ModeChip(mode: String) {
    val color = when (mode.uppercase()) {
        "FM" -> MiuixTheme.colorScheme.primaryContainer
        "SSTV" -> MiuixTheme.colorScheme.secondaryContainer
        "DSTAR" -> MiuixTheme.colorScheme.tertiaryContainer
        "CW" -> MiuixTheme.colorScheme.errorContainer
        "USB", "LSB" -> MiuixTheme.colorScheme.surfaceVariant
        else -> MiuixTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (mode.uppercase()) {
        "FM" -> MiuixTheme.colorScheme.onPrimaryContainer
        "SSTV" -> MiuixTheme.colorScheme.onSecondaryContainer
        "DSTAR" -> MiuixTheme.colorScheme.onTertiaryContainer
        "CW" -> MiuixTheme.colorScheme.onErrorContainer
        "USB", "LSB" -> MiuixTheme.colorScheme.onSurfaceSecondary
        else -> MiuixTheme.colorScheme.onSurfaceSecondary
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
            style = MiuixTheme.textStyles.footnote2,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ErrorDialog(message: String, onDismiss: () -> Unit) {
    OverlayDialog(
        show = true,
        title = stringResource(R.string.location_failed),
        summary = message,
        onDismissRequest = onDismiss,
    ) {
        TextButton(
            text = "OK",
            onClick = onDismiss
        )
    }
}
