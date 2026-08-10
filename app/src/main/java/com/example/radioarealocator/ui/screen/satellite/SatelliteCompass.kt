package com.example.radioarealocator.ui.screen.satellite

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 手机朝向传感器封装（雷达图罗盘十字线数据源）。
 *
 * 使用 [Sensor.TYPE_ROTATION_VECTOR]（无需任何运行时权限），读取设备朝向
 * 方位角（度，0=正北，顺时针），叠加 [GeomagneticField] 磁偏角修正，并对
 * 0°/360° 回绕做低通平滑（alpha=0.15），避免罗盘抖动。
 *
 * 设备无旋转矢量传感器时 [azimuth] 恒为 null，UI 侧据此禁用罗盘十字线。
 */
class PhoneCompass(context: Context) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val displayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
    private val rotationSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    private var smoothAzimuth = 0f
    private var hasInitialReading = false
    private var magneticDeclination = 0f

    private val _azimuth = MutableStateFlow<Float?>(null)
    /** 平滑后的设备朝向（度，0=正北，顺时针）；无传感器或未启动时为 null */
    val azimuth: StateFlow<Float?> = _azimuth.asStateFlow()

    /** 设备是否具备旋转矢量传感器 */
    val hasRotationSensor: Boolean
        get() = rotationSensor != null

    /**
     * 设置磁偏角（度，由 [GeomagneticField] 按地面站位置计算）。
     * 为 0 时不做修正（模拟器/无定位场景）。
     */
    fun setMagneticDeclination(declinationDeg: Float) {
        magneticDeclination = declinationDeg
    }

    /** 开始监听传感器。无传感器时静默无操作。 */
    fun start() {
        val sensor = rotationSensor ?: return
        hasInitialReading = false
        sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    }

    /** 停止监听。可安全重复调用。 */
    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val azimuth = readAzimuth(event)
        if (!hasInitialReading) {
            smoothAzimuth = azimuth
            hasInitialReading = true
        } else {
            smoothAzimuth = lowPassAngle(smoothAzimuth, azimuth)
        }
        _azimuth.value = Math.round(smoothAzimuth * 10) / 10f
    }

    private fun readAzimuth(event: SensorEvent): Float {
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
        remapForDisplayRotation()
        SensorManager.getOrientation(rotationMatrix, orientationValues)
        val raw = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
        val corrected = raw + magneticDeclination
        return (corrected % 360.0 + 360.0).toFloat() % 360.0f
    }

    /** 根据屏幕旋转方向重映射坐标轴，使方位角与屏幕朝上方向一致。 */
    private fun remapForDisplayRotation() {
        val rotation = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: Surface.ROTATION_0
        val remapped = when (rotation) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_Y, SensorManager.AXIS_MINUS_X, remappedMatrix
            )
            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_X, SensorManager.AXIS_MINUS_Y, remappedMatrix
            )
            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                rotationMatrix, SensorManager.AXIS_MINUS_Y, SensorManager.AXIS_X, remappedMatrix
            )
            else -> false
        }
        if (remapped) {
            System.arraycopy(remappedMatrix, 0, rotationMatrix, 0, 9)
        }
    }

    /** 低通平滑，正确处理 0°/360° 回绕（始终走最短角路径）。 */
    private fun lowPassAngle(previous: Float, current: Float): Float {
        var delta = current - previous
        while (delta > 180f) delta -= 360f
        while (delta <= -180f) delta += 360f
        return (previous + SMOOTHING_FACTOR * delta + 360f) % 360f
    }

    private companion object {
        /** 低通平滑系数：值越小越平滑、响应越慢 */
        const val SMOOTHING_FACTOR = 0.15f
    }
}

/**
 * 创建并持有 [PhoneCompass]，自动 start/stop，返回当前朝向角（度）。
 *
 * @param latitudeDeg 地面站纬度（度），用于磁偏角修正；null 时不修正
 * @param longitudeDeg 地面站经度（度）
 * @param altitudeM 地面站海拔（米）
 * @return 设备朝向方位角（度，0=正北，顺时针）；无传感器时为 null
 */
@Composable
fun rememberPhoneCompassAzimuth(
    latitudeDeg: Double?,
    longitudeDeg: Double?,
    altitudeM: Double = 0.0,
): State<Float?> {
    val context = LocalContext.current.applicationContext
    val compass = remember(context) { PhoneCompass(context) }

    // 磁偏角：按位置变化重新计算（忽略时间变化，分钟级影响可忽略）
    val declination = remember(latitudeDeg, longitudeDeg) {
        if (latitudeDeg != null && longitudeDeg != null) {
            try {
                GeomagneticField(
                    latitudeDeg.toFloat(),
                    longitudeDeg.toFloat(),
                    altitudeM.toFloat(),
                    System.currentTimeMillis()
                ).declination
            } catch (_: Exception) {
                0f
            }
        } else {
            0f
        }
    }
    DisposableEffect(declination) {
        compass.setMagneticDeclination(declination)
        onDispose { }
    }

    DisposableEffect(compass) {
        compass.start()
        onDispose { compass.stop() }
    }

    return compass.azimuth.collectAsStateWithLifecycle()
}
