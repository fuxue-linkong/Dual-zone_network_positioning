package com.example.radioarealocator

import android.app.Application
import com.amap.api.maps.MapsInitializer

/**
 * 应用入口。
 *
 * 在此处完成高德地图 SDK 隐私合规初始化，确保在 SDK 任何接口调用前执行：
 * - [MapsInitializer.updatePrivacyShow]：设置隐私弹窗是否显示
 * - [MapsInitializer.updatePrivacyAgree]：设置用户是否同意隐私协议
 *
 * 未调用会导致 errorCode 555570（隐私合规校验失败）及 native library 加载失败。
 */
class RadioAreaLocatorApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 高德 SDK 隐私合规：在 SDK 任何接口调用前必须先调用这两个接口
        // 参数 true 表示已向用户展示隐私政策且用户已同意
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
    }
}
