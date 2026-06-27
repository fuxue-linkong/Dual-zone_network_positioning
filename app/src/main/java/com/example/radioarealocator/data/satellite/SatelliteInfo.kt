package com.example.radioarealocator.data.satellite

import java.time.Instant

/**
 * 卫星过境信息，用于界面展示。
 */
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
    val status: String = ""
)
