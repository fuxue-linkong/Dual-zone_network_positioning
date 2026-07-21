package com.example.radioarealocator

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.amap.api.maps.MapsInitializer
import com.example.radioarealocator.data.crypto.SecretManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
 * 启动关键路径（主线程同步执行）：
 * 1. 敏感信息解密：从 assets/secrets.dat 解密 API Key（三碎片密钥组装 + AES-GCM）
 * 2. 高德地图 SDK 隐私合规初始化
 * 3. 地图 SDK Key 运行时注入（不依赖 manifestPlaceholders，CI 无需 Secrets）
 *
 * 后台初始化（IO 线程异步执行，不阻塞首帧）：
 * 4. Android 14+ 启用 OnBackInvokedCallback（依赖 hiddenapibypass）
 * 5. OkHttpClient 单例（带 10MB 缓存 + User-Agent 拦截器）
 *
 * 拆分原则：只把不阻塞 UI 显示、且无严格时序依赖的初始化放到后台线程，
 * 避免与 Compose 首帧、高德 SDK native 库加载等抢占主线程资源。
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

    /**
     * OkHttpClient 单例。
     *
     * 使用 [lazy] 在首次访问时构建，避免在 [onCreate] 主线程上同步创建 10MB 缓存目录。
     * 首次访问通常发生在 IO 线程发起网络请求时，构建开销不会阻塞 UI。
     * 构建失败时回退到无缓存的最简客户端，保证网络功能可用。
     */
    val okhttpClient: OkHttpClient by lazy {
        try {
            OkHttpClient.Builder()
                .cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "RadioAreaLocator/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag())
                            .build()
                    )
                }.build()
        } catch (_: Throwable) {
            OkHttpClient.Builder().build()
        }
    }

    /** 后台初始化用的协程作用域，[SupervisorJob] 保证单个子协程失败不影响其他。 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val appViewModelStore by lazy { ViewModelStore() }

    override fun onCreate() {
        super.onCreate()
        radioApp = this

        // ── 启动关键路径（主线程同步）──────────────────────────────────
        // 1. 解密敏感信息（天气 API Key、地图 SDK Key）
        SecretManager.init(this)

        // 2. 高德 SDK 隐私合规：在 SDK 任何接口调用前必须先调用这两个接口
        // 用 try/catch 保护，避免 SDK 内部异常导致整个应用启动失败
        try {
            MapsInitializer.updatePrivacyShow(this, true, true)
            MapsInitializer.updatePrivacyAgree(this, true)
        } catch (_: Throwable) {
            // 高德 SDK 初始化失败不应阻塞应用启动
        }

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

        // ── 后台初始化（IO 线程异步，不阻塞首帧）──────────────────────
        // 4. Android 14+ 启用 OnBackInvokedCallback（依赖 hiddenapibypass）
        //    包含 SharedPreferences 读取 + hidden API 反射调用，约 10-30ms，
        //    且 OnBackInvokedCallback 仅在用户触发返回手势时才需要，
        //    放后台线程不会影响冷启动关键路径。
        //    即使 back gesture 在初始化完成前到达，最坏情况是降级到 legacy back behavior（不崩溃）。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            appScope.launch {
                try {
                    val prefs = this@RadioAreaLocatorApplication
                        .getSharedPreferences("settings", MODE_PRIVATE)
                    val enable = prefs.getBoolean("enable_predictive_back", false)
                    HiddenApiBypass.addHiddenApiExemptions(
                        "Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback"
                    )
                    setEnableOnBackInvokedCallback(applicationInfo, enable)
                } catch (_: Throwable) {
                    // hidden API 绕过失败不影响应用启动
                }
            }
        }

        // 5. OkHttpClient 单例改为按需 lazy 构建（见 [okhttpClient]），
        //    避免在 onCreate 主线程上同步创建 10MB 缓存目录。
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
