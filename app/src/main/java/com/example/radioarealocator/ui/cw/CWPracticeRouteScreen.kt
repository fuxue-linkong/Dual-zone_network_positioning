package com.example.radioarealocator.ui.cw

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.radioarealocator.R
import com.example.radioarealocator.ui.navigation3.LocalNavigator
import com.example.radioarealocator.ui.theme.LocalEnableBlur
import com.example.radioarealocator.ui.util.BlurredBar
import com.example.radioarealocator.ui.util.rememberBlurBackdrop
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme

/**
 * CW 练习路由入口：用状态机在主菜单和三个子页面之间切换，
 * 子页面（自由练习设置 / 教程列表 / 摩斯电码编解码）保留在 cw/ 子目录中，
 * 不污染 Routes.kt，符合"原 UI 处理方式"。
 */
@Composable
fun CWPracticeRouteScreen() {
    val navigator = LocalNavigator.current
    val cwViewModel = viewModel<CWPracticeViewModel>()
    val settings by cwViewModel.settings.collectAsStateWithLifecycle()

    var page by rememberSaveable { mutableStateOf(CWPage.Main) }

    // 子页面返回逻辑：先回主菜单，主菜单再退回上一级路由
    BackHandler(enabled = page != CWPage.Main) {
        page = CWPage.Main
    }

    val enableBlur = LocalEnableBlur.current
    val backdrop = rememberBlurBackdrop(enableBlur)
    val blurActive = backdrop != null
    val barColor = if (blurActive) Color.Transparent else colorScheme.surface
    val scrollBehavior = MiuixScrollBehavior()

    val title = when (page) {
        CWPage.Main -> stringResource(R.string.cw_practice)
        CWPage.FreeSettings -> stringResource(R.string.free_practice)
        CWPage.Tutorial -> stringResource(R.string.tutorial_practice)
        CWPage.MorseCode -> stringResource(R.string.morsecode_codec)
    }

    val onBack: () -> Unit = when (page) {
        CWPage.Main -> dropUnlessResumed { navigator.pop() }
        else -> { { page = CWPage.Main } }
    }

    // CW 子组件均使用 Miuix 组件 + LocalCardAlpha，统一在 MiuixTheme 下渲染
    MiuixTheme {
        Scaffold(
            topBar = {
                BlurredBar(backdrop) {
                    TopAppBar(
                        color = barColor,
                        title = title,
                        navigationIcon = {
                            Box(modifier = Modifier.padding(start = 12.dp)) {
                                IconButton(onClick = onBack) {
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
            when (page) {
                CWPage.Main -> CWPracticeScreen(
                    onBackClick = onBack,
                    onFreePracticeClick = { page = CWPage.FreeSettings },
                    onTutorialClick = { page = CWPage.Tutorial },
                    onMorseCodeClick = { page = CWPage.MorseCode },
                    contentPadding = innerPadding
                )

                CWPage.FreeSettings -> FreePracticeSettingsScreen(
                    settings = settings,
                    onSettingsChange = cwViewModel::updateSettings,
                    onStartPractice = {
                        // 生成练习文本并开始播放；此处仅停留在设置页，播放进度由设置项控制
                        cwViewModel.generatePracticeText(settings.characterSet, settings.practiceLength)
                        cwViewModel.startPractice()
                    },
                    contentPadding = innerPadding
                )

                CWPage.Tutorial -> TutorialListScreen(
                    onLessonClick = { lessonId ->
                        // 教程入口暂未实现完整课程数据，点击后切回自由练习设置页
                        page = CWPage.FreeSettings
                    },
                    contentPadding = innerPadding
                )

                CWPage.MorseCode -> MorseCodeScreen(
                    bgPage = colorScheme.surface,
                    bgCard = colorScheme.surface,
                    primaryColor = colorScheme.primary,
                    textPrimary = colorScheme.onSurface,
                    textSecondary = colorScheme.onSurfaceSecondary,
                    contentPadding = innerPadding
                )
            }
        }
    }
}

private enum class CWPage { Main, FreeSettings, Tutorial, MorseCode }
