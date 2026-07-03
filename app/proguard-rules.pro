# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# 保留行号信息，便于 release 崩溃堆栈调试
-keepattributes SourceFile,LineNumberTable

# 隐藏原始源文件名
-renamesourcefileattribute SourceFile

# 在 release 构建中剥离 debug 和 trace 日志调用，避免泄漏内部实现
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# predict4java and dependencies
-keep class com.github.amsacode.predict4java.** { *; }
-keep class org.apache.commons.lang.** { *; }
-keep class com.github.davidmoten.guavamini.** { *; }
-keep class org.apache.commons.logging.** { *; }
-keep class org.apache.commons.logging.impl.** { *; }
-dontwarn org.apache.commons.logging.**

# 自定义 commons-logging 工厂（通过 SPI 注册，类名必须保留）
-keep class com.example.radioarealocator.logging.** { *; }
-keep class com.example.radioarealocator.logging.AndroidLogFactory { *; }
-keep class com.example.radioarealocator.logging.AndroidLog { *; }
