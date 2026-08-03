package com.example.radioarealocator.data.aprs

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AprsStation(
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val symbolTable: Char = '/',
    val symbolCode: Char = '>',
    val comment: String = "",
    val altitude: Double? = null,
    val course: Int? = null,
    val speed: Int? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val distance: Double? = null,
    val bearing: Double? = null
) : Parcelable

@Parcelize
data class AprsMessage(
    val id: Long = 0,
    val source: String,
    val destination: String,
    val body: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = false,
    val isRead: Boolean = false,
    val msgNumber: String? = null,
    val isAcknowledged: Boolean = false,
    val status: Int = 0,
    val retryCount: Int = 0
) : Parcelable

data class AprsPosition(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val course: Int? = null,
    val speed: Int? = null,
    val symbolTable: Char = '/',
    val symbolCode: Char = '>',
    val comment: String = ""
)

data class AprsConfig(
    val callsign: String = "",
    val ssid: String = "",
    val passcode: String = "",
    val server: String = "china.aprs2.net",
    val port: Int = 14580,
    val useTls: Boolean = false,
    val symbolTable: Char = '/',
    val symbolCode: Char = '>',
    val comment: String = "RadioAreaLocator",
    val transmitInterval: Int = 60,
    val useCompression: Boolean = true,
    val enableTransmit: Boolean = false,
    val enableReceive: Boolean = true,
    val filter: String = ""
) {
    val fullCallsign: String
        get() = if (ssid.isNotEmpty()) "$callsign-$ssid" else callsign

    val computedPasscode: Int
        get() = if (callsign.isNotEmpty()) AprsPacket.passcode(fullCallsign) else -1
}
