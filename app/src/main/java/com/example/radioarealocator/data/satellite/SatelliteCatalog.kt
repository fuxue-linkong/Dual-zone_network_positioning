package com.example.radioarealocator.data.satellite

/**
 * 业余卫星目录，维护 NORAD 编号与工作模式的映射。
 * 供数据源和预测器共享使用。
 */
object SatelliteCatalog {

    /**
     * 业余卫星常见工作模式，按 NORAD 编号维护。
     */
    val MODES_BY_CATALOG_NUMBER: Map<Int, List<String>> = mapOf(
        // FM
        25544 to listOf("FM", "SSTV"),      // ISS
        43017 to listOf("FM"),              // AO-91 (Fox-1B)
        43137 to listOf("FM"),              // AO-92 (Fox-1D)
        27607 to listOf("FM"),              // SO-50
        22825 to listOf("FM"),              // AO-27
        43678 to listOf("FM"),              // PO-101 (Diwata-2)
        40908 to listOf("FM", "SSTV"),      // LilacSat-2
        41909 to listOf("FM"),              // BY70-1
        42684 to listOf("FM"),              // CAS-3H
        // 线性转发器（CW / USB / LSB）
        7530 to listOf("CW", "USB", "LSB"), // AO-7
        24278 to listOf("CW", "USB", "LSB"),// FO-29
        39417 to listOf("CW", "USB", "LSB"),// AO-73 (FUNcube-1)
        42017 to listOf("CW", "USB", "LSB"),// EO-88
        43854 to listOf("CW", "USB", "LSB"),// JO-97
        42761 to listOf("CW", "USB", "LSB"),// CAS-4A
        42759 to listOf("CW", "USB", "LSB"),// CAS-4B
        40903 to listOf("CW", "USB", "LSB"),// XW-2A
        40911 to listOf("CW", "USB", "LSB"),// XW-2B
        40906 to listOf("CW", "USB", "LSB"),// XW-2C
        40907 to listOf("CW", "USB", "LSB"),// XW-2D
        40910 to listOf("CW", "USB", "LSB"),// XW-2F
        // D-Star
        43879 to listOf("DSTAR")            // D-Star ONE
    )

    /** 我们关心的所有 NORAD 编号集合 */
    val catalogNumbers: Set<Int> get() = MODES_BY_CATALOG_NUMBER.keys
}
