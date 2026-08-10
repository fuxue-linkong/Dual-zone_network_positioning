package com.example.radioarealocator.data.satellite

/**
 * 卫星转发器（transmitter）频率信息，来自 SatNOGS DB API
 * （https://db.satnogs.org/api/transmitters/）。
 *
 * 对应 API 字段：uuid, norad_cat_id, name, uplink_low/uplink_high/uplink_mode,
 * downlink_low/downlink_high/downlink_mode, inverted, status, description。
 * 频率取各频段的下边带值（low），即静止基频。
 *
 * @param noradCatId 卫星 NORAD 编号
 * @param name 转发器名称（API "name" 字段）
 * @param uplinkHz 上行基频（Hz），无上行（仅信标/下行）时为 null
 * @param downlinkHz 下行基频（Hz），无下行时为 null
 * @param mode 工作模式（如 "FM"、"USB"、"LSB"，优先取上行模式）
 * @param inverted 是否为倒置转发器（上行/下行频带反向，线性转发器常见）
 * @param status 状态（API "status" 字段）：active / inactive / future / unknown
 * @param description 转发器描述（API "description" 字段，如 "FM Voice"）
 */
data class RadioInfo(
    val noradCatId: Int,
    val name: String,
    val uplinkHz: Long? = null,
    val downlinkHz: Long? = null,
    val mode: String = "",
    val inverted: Boolean = false,
    val status: String = STATUS_ACTIVE,
    val description: String = "",
) {
    /** 是否处于活跃状态 */
    val isActive: Boolean
        get() = status == STATUS_ACTIVE

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_INACTIVE = "inactive"
        const val STATUS_FUTURE = "future"
        const val STATUS_UNKNOWN = "unknown"
    }
}
