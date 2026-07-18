# R8/ProGuard rules for the app. Referenced from the release build type (and inherited by the
# `benchmark` build type via initWith(release), and by the baselineprofile plugin's
# auto-generated minified benchmarkRelease/nonMinifiedRelease variants).
#
# AGP 8.5 runs R8 in FULL MODE by default for the app module (more aggressive than legacy/compat
# mode about inlining, repackaging, and removing classes that look unreachable via static analysis
# alone) -- reflection-based and generics/reified-based lookups need explicit help below.
#
# What is intentionally NOT here: Media3, Room, and Hilt all ship their own consumer ProGuard rules
# inside their AARs (auto-applied by AGP; duplicating them here would just be dead weight and drift
# out of sync with library upgrades). MusicWidgetReceiver, PlaybackService, MainActivity, and RollaApp
# are all manifest-registered components -- AGP auto-extracts a `-keep ... { <init>(); }` rule for
# every one of them from the merged manifest (see app/build/intermediates/aapt_proguard_file/<variant
# >/.../aapt_rules.txt), so no explicit rule is needed for them either. Correctness after
# minification is verified post-build instead (see .claude/skills/release-build/SKILL.md
# verification steps): mapping.txt/usage.txt are checked for survival of the nav route classes,
# MusicWidgetReceiver's original name, and Room entity classes.

# ── kotlinx-serialization: typed Navigation Compose routes (com.rolla.musicplayer.navigation.*) ──
# kotlinx-serialization 1.7.3 already ships bundled consumer R8 rules (see
# META-INF/com.android.tools/r8/kotlinx-serialization-r8.pro inside the kotlinx-serialization-core
# AAR) that keep every @Serializable class from being fully removed and keep the Companion /
# serializer() / INSTANCE members needed for generic serializer lookup. Those bundled rules are
# intentionally generic (`class **`) and still allow obfuscation/optimization of the *members*
# inside. The rules below are the additional, app-scoped safety net the official
# kotlinx.serialization README recommends for R8 full mode: they guarantee the compiler-generated
# `$$serializer` companion classes for THIS app's own @Serializable route types (Routes.kt: Library,
# NowPlaying, Equalizer, Playlists, PlaylistDetail, SmartPlaylist, AlbumDetail, ArtistDetail,
# TagEditor, BatchTagEditor, Search, Settings, About, Licenses, Privacy -- consumed by
# androidx.navigation's typed `composable<T>()` / `toRoute<T>()`) survive intact. This is the
# single highest-risk rule in this file: if it's wrong, routes fail SILENTLY at runtime
# (SerializationException on navigate / back-stack restore), never at compile time.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.rolla.musicplayer.**$$serializer { *; }
-keepclassmembers class com.rolla.musicplayer.** {
    *** Companion;
}
-keepclasseswithmembers class com.rolla.musicplayer.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── jaudiotagger (offline tag-editing fork, com.github.Adonai; :feature:tageditor) ──
# Reflection-heavy (tag-format dispatch, field-key enums resolved by name) and ships no consumer
# ProGuard rules of its own. A broad keep is deliberate here: this is a small, local-only,
# read/write-correctness-critical library (TagReader/TagWriter) where a stripped-or-renamed
# internal class would silently corrupt or fail a user's file write -- size cost is an acceptable
# trade for correctness.
-keep class org.jaudiotagger.** { *; }

# jaudiotagger contains desktop-Java code paths that reference java.awt / javax.imageio /
# javax.swing / sun.* ; they are never executed on Android (see :feature:tageditor's `isAndroid`
# handling), but R8's strict missing-class check fails the build without these. Package-level
# because the broad -keep above retains desktop-only classes R8 would otherwise tree-shake,
# surfacing many such references (Graphics2D, ImageWriter, swing FileFilter, ...).
-dontwarn java.awt.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn sun.security.action.**
