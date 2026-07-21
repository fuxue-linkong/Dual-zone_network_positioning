package com.example.radioarealocator.ui.screen.home

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Immutable
data class AppVersion(
    val versionName: String,
    val versionCode: Long
)

@Immutable
data class SystemInfo(
    val appVersion: String,
)

fun getAppVersion(context: Context): AppVersion {
    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)!!
    val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
    return AppVersion(
        versionName = packageInfo.versionName!!,
        versionCode = versionCode
    )
}

// ---- 主页时间排布（HomeHeader）相关常量与组件 ----

/** 本地时间字号（sp） */
internal const val LOCAL_TIME_FONT_SIZE = 44
/** UTC 时间字号相对本地时间的缩放比例 */
internal const val UTC_FONT_SIZE_SCALE = 0.4f
/** 日期普通部分字号（sp） */
internal const val DATE_FONT_SIZE = 14
/** 日期中"日"数字放大字号（sp） */
internal const val DATE_DAY_FONT_SIZE = 20
/** 每日一言水平滚动速度（px/秒） */
internal const val QUOTE_SCROLL_SPEED_PX_PER_SEC = 12f
/** 每日一言滚到端点后暂停时长（毫秒） */
internal const val QUOTE_PAUSE_MS = 2000L

internal val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
internal val weekdayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("E")

/**
 * 每日一言水平滚动组件（来自 main 分支 MainScreen.kt）。
 *
 * 当文本宽度超过容器宽度时，以 [QUOTE_SCROLL_SPEED_PX_PER_SEC] px/秒 速度水平滚动，
 * 到达端点后暂停 [QUOTE_PAUSE_MS] 再反向滚动，循环往复；文本不超宽时静态居左显示。
 * 使用 [BasicText] 渲染，不依赖 Miuix/Material 主题，可在双主题下共用。
 */
@Composable
internal fun DailyQuoteScroller(
    quote: String,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    if (quote.isBlank()) return
    var textWidthPx by remember { mutableIntStateOf(0) }
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
        BasicText(
            text = quote,
            maxLines = 1,
            softWrap = false,
            style = TextStyle(fontSize = 13.sp, color = contentColor),
            modifier = Modifier
                .offset { IntOffset(offsetAnim.value.roundToInt(), 0) }
                .onSizeChanged { if (it.width != textWidthPx) textWidthPx = it.width }
        )
    }
}
