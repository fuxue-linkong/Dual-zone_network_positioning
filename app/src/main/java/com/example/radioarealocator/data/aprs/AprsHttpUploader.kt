package com.example.radioarealocator.data.aprs

import android.util.Log
import com.example.radioarealocator.data.network.HttpClientProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request

class AprsHttpUploader(
    private val apiKey: String? = null
) {
    private val TAG = "AprsHttpUploader"

    suspend fun uploadPosition(
        callsign: String,
        latitude: Double,
        longitude: Double,
        comment: String = "",
        altitude: Double? = null,
        course: Int? = null,
        speed: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val formBody = FormBody.Builder()
                .add("name", callsign)
                .add("lat", "%.6f".format(latitude))
                .add("lng", "%.6f".format(longitude))

            if (comment.isNotEmpty()) formBody.add("comment", comment)
            altitude?.let { formBody.add("altitude", "%.1f".format(it)) }
            course?.let { formBody.add("course", it.toString()) }
            speed?.let { formBody.add("speed", it.toString()) }

            val requestBuilder = Request.Builder()
                .url("https://api.aprs.fi/api/addloc")
                .post(formBody.build())

            apiKey?.let {
                requestBuilder.addHeader("Authorization", "Bearer $it")
            }

            val response = HttpClientProvider.client.newCall(requestBuilder.build()).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Log.d(TAG, "HTTP upload success: $body")
                Result.success(body)
            } else {
                Log.e(TAG, "HTTP upload failed: ${response.code} $body")
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTTP upload error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun sendMessage(
        callsign: String,
        destination: String,
        message: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val packet = AprsPacket.formatMessage(callsign, destination, message)

            val formBody = FormBody.Builder()
                .add("name", callsign)
                .add("message", packet)
                .build()

            val request = Request.Builder()
                .url("https://api.aprs.fi/api/sendmsg")
                .post(formBody)
                .build()

            val response = HttpClientProvider.client.newCall(request).execute()
            val body = response.body?.string() ?: ""

            if (response.isSuccessful) {
                Result.success(body)
            } else {
                Result.failure(Exception("HTTP ${response.code}: $body"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
