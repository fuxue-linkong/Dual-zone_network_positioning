package com.example.radioarealocator.data.satellite

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * 卫星过境信息，用于界面展示。
 */
@Immutable
data class SatelliteInfo(
    val name: String,
    val catalogNumber: Int,
    val modes: List<String>,
    val aosTime: Instant,
    val losTime: Instant,
    val maxElevation: Double,
    val aosAzimuth: Int,
    val losAzimuth: Int,
    /** 当前是否正在境内（仰角 > 0） */
    val isCurrentlyVisible: Boolean = false,
    /** 数据来源标签：CT / SNOGS / ALL */
    val source: String = "",
    /** AMSAT 状态报告：Heard / Telemetry Only / Not Heard / Crew Active */
    val status: String = "",
    /** 是否为 GEO/深空卫星（对地面站恒定可见或永不可见，由预测器特判） */
    val isGeo: Boolean = false,
    /** 是否为白天日照过境（过境期间地面站处于白天） */
    val isDaylightPass: Boolean = false
)
