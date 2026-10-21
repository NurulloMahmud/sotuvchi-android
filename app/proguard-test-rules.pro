# The QA build is minified, and so is the instrumented test APK that runs against it.
-dontwarn javax.lang.model.**
-dontwarn com.google.errorprone.annotations.**

# JUnit finds tests by reflection, so the shrinker must keep them.
-keepclasseswithmembers class * { @org.junit.Test <methods>; }
-keep @org.junit.runner.RunWith class * { *; }
