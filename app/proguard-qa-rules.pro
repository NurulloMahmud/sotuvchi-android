# QA build only. The instrumented test APK reuses classes of these libraries from the app
# instead of carrying its own copies, so the shrinker must not remove them from the app.
# The app's own code, kotlinx.serialization, OkHttp, Coil and the rest are still shrunk and
# renamed exactly as in release, which is what the QA run is for.
-keep class androidx.** { *; }
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlin.**

# The UI tests construct these directly, including the synthetic default-argument constructor.
-keep class ai.sotuvchi.app.app.AppConfig { *; }
-keep class ai.sotuvchi.app.core.store.DataStoreKeyValueStore { *; }
