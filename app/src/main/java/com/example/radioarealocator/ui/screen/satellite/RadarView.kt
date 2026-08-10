package com.example.radioarealocator.ui.screen.satellite

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StampedPathEffectStyle
import androidx.compose.ui.graphics.SweepGradientShader
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.example.radioarealocator.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val RADAR_BACKGROUND = Color(0xE60F1722)
private val RING_COLOR = Color(0xFF546E7A)
private val LABEL_COLOR = Color(0xFF90A4AE)
private val CROSSHAIR_COLOR = Color(0xFF455A64)
private val SATELLITE_SUNLIT = Color(0xFF4CAF50)
private val SATELLITE_ECLIPSED = Color(0xFFF44336)
private val COMPASS_COLOR = Color(0xFF00BCD4)
private val SWEEP_COLOR_DEFAULT = Color(0xFF64B5F6)

private const val RING_COUNT = 3
private const val TICK_STEP_DEG = 30
private const val STROKE_WIDTH = 6f
private const val SWEEP_DURATION_MS = 8_000
private val PI_2 = PI / 2.0

/**
 * 卫星雷达图（共享 Compose Canvas 组件，参考 Look4Sat 重构）。
 *
 * 包含功能：
 * - 旋转扫描波束（SweepGradientShader 动画）
 * - 方位角刻度（每 30° 标注 N/E/S/W）
 * - 仰角同心环（0°/30°/60°，圆心天顶 90°）
 * - 卫星轨迹路径（三角箭头标记）
 * - 卫星光点（绿=日照，红=蚀中；脉冲扩散动画）
 * - 日月矢量图标
 * - 罗盘模式（整图旋转）/ 翻转
 * - 瞄准十字线（罗盘模式下在地平环上显示设备朝向）
 *
 * @param azimuthDeg 卫星方位角（度，0=正北，顺时针）
 * @param elevationDeg 卫星仰角（度，<0 时卫星点变暗并贴 0° 环）
 * @param isEclipsed 是否蚀中
 * @param trackPositions 轨迹点列表（方位角/仰角度数对），null 时不绘制
 * @param sunAzimuthDeg 太阳方位角（度）
 * @param sunElevationDeg 太阳仰角（度）
 * @param moonAzimuthDeg 月亮方位角（度）
 * @param moonElevationDeg 月亮仰角（度）
 * @param phoneAzimuthDeg 设备朝向（度，0=正北），null 时隐藏罗盘十字线
 * @param shouldShowSweep 是否显示旋转扫描波束
 * @param shouldUseCompass 是否启用罗盘整图旋转
 * @param shouldFlipRadar 是否 180° 翻转雷达图
 */
@Composable
fun RadarView(
    azimuthDeg: Double?,
    elevationDeg: Double?,
    isEclipsed: Boolean = false,
    trackPositions: List<Pair<Double, Double>>? = null,
    sunAzimuthDeg: Double? = null,
    sunElevationDeg: Double? = null,
    moonAzimuthDeg: Double? = null,
    moonElevationDeg: Double? = null,
    phoneAzimuthDeg: Float? = null,
    shouldShowSweep: Boolean = true,
    shouldUseCompass: Boolean = false,
    shouldFlipRadar: Boolean = false,
    sweepColor: Color = SWEEP_COLOR_DEFAULT,
    modifier: Modifier = Modifier,
) {
    val animTransition = rememberInfiniteTransition(label = "pulseScale")
    val pulseRadius by animTransition.animateFloat(
        initialValue = 16f,
        targetValue = 64f,
        animationSpec = infiniteRepeatable(tween(1000)),
        label = "pulseRadius"
    )
    val sweepTransition = rememberInfiniteTransition(label = "sweep")
    val sweepDegrees by sweepTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(SWEEP_DURATION_MS, easing = LinearEasing)),
        label = "sweepDegrees"
    )
    val textMeasurer = rememberTextMeasurer()
    val sunPainter = painterResource(R.drawable.ic_sun)
    val moonPainter = painterResource(R.drawable.ic_moon)

    // Track path & sweep brush cache
    var cachedRadius by remember { mutableFloatStateOf(0f) }
    var cachedTrackRef by remember { mutableStateOf<List<Pair<Double, Double>>?>(null) }
    var cachedSweepColor by remember { mutableStateOf(Color.Unspecified) }
    var trackPath by remember { mutableStateOf(Path()) }
    var trackEffect by remember { mutableStateOf(PathEffect.cornerPathEffect(0f)) }
    var cachedSweepBrush by remember { mutableStateOf<ShaderBrush?>(null) }

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val radius = size.minDimension / 2f * 0.92f

        // Rebuild cached artifacts when size or data changes
        if (radius != cachedRadius || trackPositions !== cachedTrackRef) {
            val positions = trackPositions
            if (positions != null && positions.size >= 2) {
                trackPath = createTrackPath(positions, radius)
                trackEffect = createTrackEffect(trackPath)
            } else {
                trackPath = Path()
                trackEffect = PathEffect.cornerPathEffect(0f)
            }
            cachedSweepBrush = makeSweepBrush(center, sweepColor)
            cachedRadius = radius
            cachedTrackRef = trackPositions
            cachedSweepColor = sweepColor
        } else if (sweepColor != cachedSweepColor) {
            cachedSweepBrush = makeSweepBrush(center, sweepColor)
            cachedSweepColor = sweepColor
        }

        // Chart rotation
        val baseRotation = if (shouldUseCompass) -(phoneAzimuthDeg ?: 0f) else 0f
        val rotation = if (shouldFlipRadar) baseRotation + 180f else baseRotation

        rotate(rotation) {
            // Sweep animation
            if (shouldShowSweep) cachedSweepBrush?.let { drawSweep(center, sweepDegrees, radius, it) }

            drawCircle(RADAR_BACKGROUND, radius + 4f)
            drawElevationRings(radius, textMeasurer)
            drawAzimuthTicks(radius, textMeasurer)
            drawCrosshair(radius)

            translate(center.x, center.y) {
                // Track path
                drawTrack(trackPath, trackEffect, RING_COLOR, sweepColor)

                // Satellite
                if (azimuthDeg != null && elevationDeg != null) {
                    if (elevationDeg > 0.0) {
                        drawPosition(azimuthDeg, elevationDeg, radius, isEclipsed, pulseRadius, SATELLITE_SUNLIT)
                    }
                }

                // Sun icon
                if (sunAzimuthDeg != null && sunElevationDeg != null && sunElevationDeg > 0.0) {
                    drawBodyIcon(sunAzimuthDeg, sunElevationDeg, radius, SWEEP_COLOR_DEFAULT, sunPainter, 52f)
                }

                // Moon icon
                if (moonAzimuthDeg != null && moonElevationDeg != null && moonElevationDeg > 0.0) {
                    drawBodyIcon(moonAzimuthDeg, moonElevationDeg, radius, LABEL_COLOR, moonPainter, 52f)
                }

                // Compass aim crosshair
                if (shouldUseCompass && phoneAzimuthDeg != null) {
                    drawAim(phoneAzimuthDeg.toDouble(), radius, COMPASS_COLOR)
                }
            }
        }
    }
}

/** 仰角环：外环 0°，向内 30° / 60°，圆心为天顶 90°。 */
private fun DrawScope.drawElevationRings(radius: Float, measurer: TextMeasurer) {
    val style = TextStyle(LABEL_COLOR, 10.sp)
    for (i in 0 until RING_COUNT) {
        val ringRadius = radius * (1f - i.toFloat() / RING_COUNT)
        drawCircle(RING_COLOR, ringRadius, style = Stroke(STROKE_WIDTH))
        val label = "${i * (90 / RING_COUNT)}°"
        val textY = -ringRadius - 18f
        drawText(measurer, label, Offset(4f, textY), style = style)
    }
    drawText(measurer, "90°", Offset(4f, -14f), style = style)
}

/** 方位刻度：每 30° 一个刻度，N/E/S/W 标注。 */
private fun DrawScope.drawAzimuthTicks(radius: Float, measurer: TextMeasurer) {
    val style = TextStyle(LABEL_COLOR, 10.sp)
    val tickLength = radius * 0.06f
    var deg = 0
    while (deg < 360) {
        val rad = Math.toRadians(deg.toDouble())
        val outer = Offset(
            (radius * sin(rad)).toFloat(),
            -(radius * cos(rad)).toFloat()
        )
        val inner = Offset(
            ((radius - tickLength) * sin(rad)).toFloat(),
            -((radius - tickLength) * cos(rad)).toFloat()
        )
        drawLine(RING_COLOR, inner, outer, STROKE_WIDTH * 0.25f)

        val label = when (deg) {
            0 -> "N"; 90 -> "E"; 180 -> "S"; 270 -> "W"
            else -> "$deg°"
        }
        val labelRadius = radius + 14f
        val labelPos = Offset(
            (labelRadius * sin(rad)).toFloat() - 8f,
            -(labelRadius * cos(rad)).toFloat() - 7f
        )
        drawText(measurer, label, labelPos, style = style)
        deg += TICK_STEP_DEG
    }
}

/** 中心十字线（正北-正南、正东-正西虚线）。 */
private fun DrawScope.drawCrosshair(radius: Float) {
    drawLine(
        CROSSHAIR_COLOR, Offset(0f, -radius), Offset(0f, radius),
        STROKE_WIDTH * 0.25f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    )
    drawLine(
        CROSSHAIR_COLOR, Offset(-radius, 0f), Offset(radius, 0f),
        STROKE_WIDTH * 0.25f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
    )
}

/** 卫星轨迹：路径线 + 三角箭头标记。 */
private fun DrawScope.drawTrack(path: Path, effect: PathEffect, trackColor: Color, effectColor: Color) {
    if (path.isEmpty) return
    drawPath(path, trackColor, style = Stroke(STROKE_WIDTH * 0.5f))
    drawPath(path, effectColor, style = Stroke(pathEffect = effect))
}

/** 卫星光点：实心圆 + 脉冲扩散光环（仅日照时显示脉冲环）。 */
private fun DrawScope.drawPosition(
    azimuthDeg: Double,
    elevationDeg: Double,
    radius: Float,
    isEclipsed: Boolean,
    pulseRadius: Float,
    color: Color,
) {
    val pos = sph2Cart(azimuthDeg, elevationDeg, radius)
    val dotColor = if (isEclipsed) SATELLITE_ECLIPSED else color
    drawCircle(dotColor, 16f, pos)
    if (!isEclipsed) drawCircle(dotColor.copy(alpha = 1f - (pulseRadius / 64f)), pulseRadius, pos)
}

/** 瞄准十字线（罗盘模式下，在地平环上指示设备朝向）。 */
private fun DrawScope.drawAim(azimuthDeg: Double, radius: Float, color: Color) {
    val size = 36f
    val azimRad = Math.toRadians(azimuthDeg)
    val pos = sph2CartRad(azimRad, 0.0, radius)
    drawLine(color, Offset(pos.x - size, pos.y), Offset(pos.x + size, pos.y), STROKE_WIDTH)
    drawLine(color, Offset(pos.x, pos.y - size), Offset(pos.x, pos.y + size), STROKE_WIDTH)
    drawCircle(color, size / 2f, pos, style = Stroke(STROKE_WIDTH))
}

// ── 工具函数 ──

/** 扫描波束 ShaderBrush 工厂。 */
private fun makeSweepBrush(center: Offset, color: Color): ShaderBrush {
    val colors = listOf(Color.Transparent, color.copy(alpha = 0.5f), color)
    val colorStops = listOf(0.64f, 0.995f, 1f)
    return ShaderBrush(SweepGradientShader(center, colors, colorStops))
}

/** 绘制旋转扫描波束。 */
private fun DrawScope.drawSweep(center: Offset, degrees: Float, radius: Float, brush: ShaderBrush) {
    rotate(-90f + degrees, center) { drawCircle(brush, radius, style = Fill) }
}

/** 构建卫星轨迹路径。 */
private fun createTrackPath(track: List<Pair<Double, Double>>, radius: Float): Path {
    val path = Path()
    track.forEachIndexed { i, (az, el) ->
        val offset = sph2Cart(az, el, radius)
        if (i == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
    }
    return path
}

/** 构建轨迹三角箭头标记效果。 */
private fun createTrackEffect(trackPath: Path): PathEffect {
    val shapeRadius = 24f
    val angle = 120.0.toRadians()
    val shape = Path().apply {
        moveTo((shapeRadius * cos(angle)).toFloat(), (shapeRadius * sin(angle)).toFloat())
        for (i in 1 until 3) {
            lineTo(
                x = (shapeRadius * cos(angle - angle * i)).toFloat(),
                y = (shapeRadius * sin(angle - angle * i)).toFloat()
            )
        }
        close()
    }
    val trackLength = PathMeasure().apply { setPath(trackPath, false) }.length
    return PathEffect.stampedPathEffect(shape, trackLength / 2f, trackLength / 4f, StampedPathEffectStyle.Rotate)
}

/** 使用画家绘制天体图标（太阳/月亮）。 */
private fun DrawScope.drawBodyIcon(
    azimuthDeg: Double,
    elevationDeg: Double,
    radius: Float,
    color: Color,
    painter: Painter,
    iconSize: Float,
) {
    val azimRad = Math.toRadians(azimuthDeg)
    val elevRad = Math.toRadians(elevationDeg)
    val pos = sph2CartRad(azimRad, elevRad, radius)
    val half = iconSize / 2f
    withTransform({
        translate(pos.x - half, pos.y - half)
    }) {
        with(painter) {
            draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(color))
        }
    }
}

/**
 * 球坐标→笛卡尔坐标（输入为度数）。
 * 仰角 0°→外环，90°→圆心；方位角 0°（正北）→画布上方，顺时针。
 */
private fun sph2Cart(azimuthDeg: Double, elevationDeg: Double, r: Float): Offset {
    return sph2CartRad(Math.toRadians(azimuthDeg), Math.toRadians(elevationDeg), r)
}

/**
 * 球坐标→笛卡尔坐标（输入为弧度）。
 * 与 Look4Sat 一致的公式：径向映射为 (PI_2 - elev) / PI_2。
 */
private fun sph2CartRad(azimRad: Double, elevRad: Double, r: Float): Offset {
    val radius = r * ((PI_2 - elevRad) / PI_2).toFloat()
    return Offset(
        x = (radius * sin(azimRad)).toFloat(),
        y = -(radius * cos(azimRad)).toFloat()
    )
}

/** 扩展：Double → 弧度。 */
private fun Double.toRadians(): Double = Math.toRadians(this)
