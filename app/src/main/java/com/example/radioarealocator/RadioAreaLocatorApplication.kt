package com.example.radioarealocator

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.UserManager
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.amap.api.maps.MapsInitializer
import com.example.radioarealocator.data.crypto.SecretManager
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.Locale

/**
 * 全局 Application 实例。供 SettingsRepository 等不持有 Context 的代码访问应用上下文。
 */
lateinit var radioApp: RadioAreaLocatorApplication

/**
 * 应用入口。
 *
 * 在此处完成：
 * 1. 敏感信息解密：从 assets/secrets.dat 解密 API Key（三碎片密钥组装 + AES-GCM）
 * 2. 高德地图 SDK 隐私合规初始化
 * 3. 地图 SDK Key 运行时注入（不依赖 manifestPlaceholders，CI 无需 Secrets）
 * 4. OkHttpClient 单例（带 10MB 缓存 + User-Agent 拦截器）
 * 5. Android 14+ 启用 OnBackInvokedCallback（依赖 hiddenapibypass）
 *
 * 未调用隐私合规接口会导致 errorCode 555570（隐私合规校验失败）及 native library 加载失败。
 */
class RadioAreaLocatorApplication : Application(), ViewModelStoreOwner {

    companion object {
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod(
                    "setEnableOnBackInvokedCallback",
                    Boolean::class.javaPrimitiveType
                )
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }
    }

    lateinit var okhttpClient: OkHttpClient
        private set

    private val appViewModelStore by lazy { ViewModelStore() }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    override fun onCreate() {
        super.onCreate()
        radioApp = this

        // 1. 解密敏感信息（天气 API Key、地图 SDK Key）
        SecretManager.init(this)

        // 2. 高德 SDK 隐私合规：在 SDK 任何接口调用前必须先调用这两个接口
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)

        // 3. 地图 SDK Key 运行时注入
        // 本地构建时 manifestPlaceholders 已从 local.properties 填充 Key，
        // CI 构建时 manifest 为空，此处通过解密后的 Key 覆盖设置。
        val sdkKey = SecretManager.getSecret("amap.sdk.key")
        if (sdkKey.isNotEmpty()) {
            try {
                MapsInitializer::class.java
                    .getMethod("setApiKey", String::class.java)
                    .invoke(null, sdkKey)
            } catch (_: NoSuchMethodException) {
                // 当前 SDK 版本不支持 setApiKey，依赖 manifest meta-data（本地构建已有）
            } catch (_: Exception) {
                // 设置失败不影响其他功能，地图可能无法正常显示
            }
        }

        if (!isUserUnlocked()) {
            return
        }

        // 4. Android 14+ 启用 OnBackInvokedCallback（依赖 hiddenapibypass）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val prefs = this.getSharedPreferences("settings", MODE_PRIVATE)
            val enable = prefs.getBoolean("enable_predictive_back", false)
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }

        // 5. OkHttpClient 单例（10MB 缓存 + 默认 UA 拦截器）
        okhttpClient = OkHttpClient.Builder()
            .cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
            .addInterceptor { block ->
                block.proceed(
                    block.request().newBuilder()
                        .header("User-Agent", "RadioAreaLocator/${BuildConfig.VERSION_CODE}")
                        .header("Accept-Language", Locale.getDefault().toLanguageTag())
                        .build()
                )
            }.build()
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
