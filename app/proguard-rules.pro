# R8 full mode (android.enableR8.fullMode=true) does the real work: whole-program
# optimisation, class merging, string constant folding, resource name obfuscation.
# The platform keeps manifest-declared components for us, so only the widget
# entry points are pinned explicitly.

-keep class dev.glass.launcher.GlassWidgetProvider { *; }
-keep class dev.glass.launcher.MainActivity { *; }

# The AGSL shader program is a string consumed by the GPU driver; never touch it.
-keepclassmembers class * {
    public static final java.lang.String AGSL;
}

# Strip every log call from release builds.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep line numbers out of the shipped stack traces (smaller dex, less info leak).
-renamesourcefileattribute SourceFile
