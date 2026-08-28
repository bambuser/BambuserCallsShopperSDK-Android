# SDK-side ProGuard rules. Kept minimal — the consumer-rules.pro file
# is what actually ships to host apps (see build.gradle.kts).

# Keep the JS bridge entry points reachable from JavaScript.
-keepclassmembers class com.bambuser.callsshopper.internal.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.bambuser.callsshopper.internal.JsBridge { *; }
