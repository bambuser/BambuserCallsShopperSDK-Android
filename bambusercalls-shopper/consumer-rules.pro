# Keep the JS bridge entry points reachable when host apps run R8/ProGuard.
-keepclassmembers class com.bambuser.callsshopper.internal.JsBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.bambuser.callsshopper.internal.JsBridge { *; }
