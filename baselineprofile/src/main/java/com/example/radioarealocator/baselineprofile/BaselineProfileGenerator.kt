package com.example.radioarealocator.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile 生成器。
 *
 * 跑法（本地，需连真机或 Android 14+ 模拟器）：
 * ```
 * ./gradlew :app:generateReleaseBaselineProfile \
 *     -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=BaselineProfile
 * ```
 * 或直接：
 * ```
 * ./gradlew :baselineprofile:generateBaselineProfile
 * ```
 *
 * 生成结果位于 `app/src/release/generated/baselineProfiles/baseline-prof.txt`，
 * 后续 release 构建会自动嵌入 APK。提交该文件到 git 后，CI 构建也能复用。
 *
 * 覆盖路径：冷启动 → 主页（HomePager，时间卡+卫星列表+CW入口） →
 * 切换到设置页（SettingPager，Miuix Preference） → 切回主页。
 * 这条路径覆盖了 FloatingBottomBar、BasicComponent、Card、TopAppBar、
 * LazyColumn、OverlayDropdownPreference 等 Compose/Miuix 组件首帧分配，
 * 是冷启动期间 JIT 编译最密集的代码路径。
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() = baselineProfileRule.collect(
        packageName = "com.example.radioarealocator"
    ) {
        // 1. 冷启动到主页
        pressHome()
        startActivityAndWait()

        // 2. 等待主页首帧完成（时间卡 + 卫星列表卡渲染）
        device.waitForIdle()

        // 3. 触发主页 LazyColumn 滚动，预编译 SatelliteListCard / CwEntryCard 等
        device.findObject(androidx.test.uiautomator.By.scrollable(true))
            ?.scrollForward()
        device.waitForIdle()

        // 4. 切换到底栏 "设置" 页（触发 SettingPager + OverlayDropdownPreference 渲染）
        // 底栏是 BottomBar 组件，第二个 tab 为设置
        // 通过点击屏幕底部偏中央区域定位底栏第二项
        val display = device.displayWidth to device.displayHeight
        device.click(display.first / 2, display.second - 40)
        device.waitForIdle()

        // 5. 滚动设置页，预编译 SwitchPreference / ArrowPreference 等
        device.findObject(androidx.test.uiautomator.By.scrollable(true))
            ?.scrollForward()
        device.waitForIdle()

        // 6. 切回主页
        device.click(display.first / 4, display.second - 40)
        device.waitForIdle()
    }
}
