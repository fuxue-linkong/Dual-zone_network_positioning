package com.example.radioarealocator

import android.app.Application
import com.amap.api.maps.MapsInitializer
import com.amap.api.services.core.ServiceSettings
import com.example.radioarealocator.data.crypto.SecretManager

/**
 * 应用入口。
 *
 * 在此处完成：
 * 1. 敏感信息解密：从 assets/secrets.dat 解密 API Key（三碎片密钥组装 + AES-GCM）
 * 2. 高德 SDK 隐私合规初始化（地图 SDK + 搜索 SDK）
 * 3. SDK Key 运行时注入（不依赖 manifestPlaceholders，CI 无需 Secrets）
 *
 * 未调用隐私合规接口会导致 errorCode 555570（隐私合规校验失败）及 native library 加载失败。
 * 搜索 SDK（ServiceSettings）用于天气查询（WeatherSearch）和逆地理编码（GeocodeSearch）。
 */
class RadioAreaLocatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 1. 解密敏感信息（地图/搜索 SDK Key）
        SecretManager.init(this)

        // 2. 高德 SDK 隐私合规：在 SDK 任何接口调用前必须先调用这两个接口
        //    地图 SDK（MapsInitializer）与搜索 SDK（ServiceSettings）均需初始化
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
        ServiceSettings.updatePrivacyShow(this, true, true)
        ServiceSettings.updatePrivacyAgree(this, true)

        // 3. SDK Key 运行时注入
        // 本地构建时 manifestPlaceholders 已从 local.properties 填充 Key，
        // CI 构建时 manifest 为空，此处通过解密后的 Key 覆盖设置。
        val sdkKey = SecretManager.getSecret("amap.sdk.key")
        if (sdkKey.isNotEmpty()) {
            // 地图 SDK Key
            try {
                MapsInitializer::class.java
                    .getMethod("setApiKey", String::class.java)
                    .invoke(null, sdkKey)
            } catch (_: NoSuchMethodException) {
                // 当前 SDK 版本不支持 setApiKey，依赖 manifest meta-data（本地构建已有）
            } catch (_: Exception) {
                // 设置失败不影响其他功能，地图可能无法正常显示
            }
            // 搜索 SDK Key（用于天气查询和逆地理编码）
            try {
                ServiceSettings::class.java
                    .getMethod("setApiKey", String::class.java)
                    .invoke(null, sdkKey)
            } catch (_: NoSuchMethodException) {
                // 当前 SDK 版本不支持 setApiKey，依赖 manifest meta-data
            } catch (_: Exception) {
                // 设置失败不影响其他功能，天气查询可能失败
            }
        }
    }
}
