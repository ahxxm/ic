# Coroutines debug artifact - not needed in release
-assumenosideeffects class kotlinx.coroutines.DebugKt {
    boolean getDEBUG();
    boolean getASSERTIONS_ENABLED();
}
-dontwarn kotlinx.coroutines.debug.**

# Remove kotlin metadata not needed at runtime
-dontwarn kotlin.reflect.jvm.internal.**
