package com.example.radioarealocator.data.aprs

import android.location.Location

object AprsPacket {
    private val QRG_RE = Regex(".*?(\\d{2,3}[.,]\\d{3,4}).*?")

    fun passcode(callsign: String): Int {
        val call = callsign.split("-")[0].uppercase() + "\u0000"
        var hash = 0x73e2
        for (i in 0 until call.length - 1 step 2) {
            hash = hash xor (call[i].code shl 8)
            hash = hash xor call[i + 1].code
        }
        return hash and 0x7fff
    }

    fun formatCallSsid(callsign: String, ssid: String?): String {
        return if (!ssid.isNullOrEmpty()) "$callsign-$ssid" else callsign
    }

    private fun m2ft(meter: Double): Int = (meter * 3.2808399).toInt()

    private fun mps2kt(mps: Float): Int = (mps * 1.94384449f).toInt()

    fun formatAltitude(location: Location): String {
        return if (location.hasAltitude()) {
            "/A=%06d".format(m2ft(location.altitude))
        } else ""
    }

    fun formatCourseSpeed(location: Location): String {
        return if (location.hasSpeed() && location.hasBearing()) {
            "%03d/%03d".format(
                location.bearing.toInt(),
                mps2kt(location.speed)
            )
        } else ""
    }

    fun formatLogin(callsign: String, ssid: String?, passcode: String, version: String): String {
        return "user ${formatCallSsid(callsign, ssid)} pass $passcode vers $version"
    }

    fun formatPosition(
        latitude: Double,
        longitude: Double,
        symbolTable: Char,
        symbolCode: Char,
        comment: String,
        compressed: Boolean = false
    ): String {
        return if (compressed) {
            formatCompressedPosition(latitude, longitude, symbolTable, symbolCode, comment)
        } else {
            formatUncompressedPosition(latitude, longitude, symbolTable, symbolCode, comment)
        }
    }

    private fun formatUncompressedPosition(
        latitude: Double,
        longitude: Double,
        symbolTable: Char,
        symbolCode: Char,
        comment: String
    ): String {
        val latStr = formatLat(latitude)
        val lonStr = formatLon(longitude)
        return "!$latStr$symbolTable$lonStr$symbolCode$comment"
    }

    private fun formatCompressedPosition(
        latitude: Double,
        longitude: Double,
        symbolTable: Char,
        symbolCode: Char,
        comment: String
    ): String {
        val latVal = (90.0 - latitude) * 380926.0
        val lonVal = (180.0 + longitude) * 190463.0

        val latBytes = ByteArray(4) { i ->
            ((latVal / Math.pow(91.0, (3 - i).toDouble())) % 91 + 33).toInt().toByte()
        }
        val lonBytes = ByteArray(4) { i ->
            ((lonVal / Math.pow(91.0, (3 - i).toDouble())) % 91 + 33).toInt().toByte()
        }

        val compressed = String(latBytes, Charsets.US_ASCII) +
                String(lonBytes, Charsets.US_ASCII) +
                symbolCode + comment
        return "!$symbolTable$compressed"
    }

    private fun formatLat(lat: Double): String {
        val isNorth = lat >= 0
        val absLat = kotlin.math.abs(lat)
        val degrees = absLat.toInt()
        val minutes = (absLat - degrees) * 60.0
        return "%02d%05.2f%s".format(degrees, minutes, if (isNorth) "N" else "S")
    }

    private fun formatLon(lon: Double): String {
        val isEast = lon >= 0
        val absLon = kotlin.math.abs(lon)
        val degrees = absLon.toInt()
        val minutes = (absLon - degrees) * 60.0
        return "%03d%05.2f%s".format(degrees, minutes, if (isEast) "E" else "W")
    }

    fun formatMessage(
        source: String,
        destination: String,
        message: String,
        msgNumber: String? = null
    ): String {
        val base = ":${destination.padEnd(9, ' ')}:$message"
        return if (msgNumber != null) {
            "$base{$msgNumber}"
        } else {
            base
        }
    }

    fun parseHostPort(hostport: String, defaultport: Int): Pair<String, Int> {
        val splits = hostport.trim().split(":")
        return try {
            Pair(splits[0], splits[1].toInt())
        } catch (_: Throwable) {
            Pair(splits[0], defaultport)
        }
    }

    fun parseQrg(comment: String): String? {
        return QRG_RE.find(comment)?.groupValues?.getOrNull(1)
    }
}
