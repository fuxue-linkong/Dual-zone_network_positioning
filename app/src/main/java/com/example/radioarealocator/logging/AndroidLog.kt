package com.example.radioarealocator.logging

import android.util.Log

/**
 * 基于 Android [android.util.Log] 的 commons-logging [org.apache.commons.logging.Log] 实现。
 *
 * predict4java 依赖 commons-logging，但其默认的 LogFactoryImpl 在 Android 上
 * 发现日志实现时会抛 NullPointerException。本类提供一个直接可用的 Log 实现，
 * 通过 SPI 注册的 [AndroidLogFactory] 返回，绕过 LogFactoryImpl 的发现流程。
 */
class AndroidLog(private val tag: String) : org.apache.commons.logging.Log {

    override fun isFatalEnabled(): Boolean = true
    override fun isErrorEnabled(): Boolean = true
    override fun isWarnEnabled(): Boolean = true
    override fun isInfoEnabled(): Boolean = true
    override fun isDebugEnabled(): Boolean = false
    override fun isTraceEnabled(): Boolean = false

    override fun fatal(message: Any?) {
        Log.e(tag, message?.toString() ?: "null")
    }

    override fun fatal(message: Any?, t: Throwable?) {
        Log.e(tag, message?.toString() ?: "null", t)
    }

    override fun error(message: Any?) {
        Log.e(tag, message?.toString() ?: "null")
    }

    override fun error(message: Any?, t: Throwable?) {
        Log.e(tag, message?.toString() ?: "null", t)
    }

    override fun warn(message: Any?) {
        Log.w(tag, message?.toString() ?: "null")
    }

    override fun warn(message: Any?, t: Throwable?) {
        Log.w(tag, message?.toString() ?: "null", t)
    }

    override fun info(message: Any?) {
        Log.i(tag, message?.toString() ?: "null")
    }

    override fun info(message: Any?, t: Throwable?) {
        Log.i(tag, message?.toString() ?: "null", t)
    }

    override fun debug(message: Any?) {
        Log.d(tag, message?.toString() ?: "null")
    }

    override fun debug(message: Any?, t: Throwable?) {
        Log.d(tag, message?.toString() ?: "null", t)
    }

    override fun trace(message: Any?) {
        Log.v(tag, message?.toString() ?: "null")
    }

    override fun trace(message: Any?, t: Throwable?) {
        Log.v(tag, message?.toString() ?: "null", t)
    }
}
