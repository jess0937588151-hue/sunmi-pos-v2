# 保留 Sunmi 列印 library
-keep class com.sunmi.peripheral.printer.** { *; }
-keep class woyou.aidlservice.jiuiv5.** { *; }

# 保留 JS Bridge（WebView 調用）
-keep class com.pos.sunmiprinter.web.PrintJsBridge { *; }
-keepclassmembers class com.pos.sunmiprinter.web.PrintJsBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# 保留 AppSettings
-keep class com.pos.sunmiprinter.AppSettings { *; }
