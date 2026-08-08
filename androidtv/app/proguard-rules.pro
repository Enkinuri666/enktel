# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class tv.enktel.app.**$$serializer { *; }
-keepclassmembers class tv.enktel.app.** { *** Companion; }
-keepclasseswithmembers class tv.enktel.app.** { kotlinx.serialization.KSerializer serializer(...); }

# Media3 FFmpeg audio decoder.
#
# DefaultRenderersFactory finds this renderer with Class.forName when the
# extension mode is PREFER, so nothing in the app references it statically and
# R8 has no reason to keep it. Release builds set isMinifyEnabled, so without
# this the decoder works in debug and is stripped from every build that ships —
# the failure being silent AC-3/DTS audio, indistinguishable from not having
# built the extension at all.
#
# The AAR's own consumer rules do not cover this: they keep native method names
# and FfmpegAudioDecoder.growOutputBuffer, not the renderer.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { *; }

# WebView JavaScript bridge for the trailer player.
#
# TrailerScreen hands an anonymous object to addJavascriptInterface and the
# IFrame page calls its methods by name. Nothing in Kotlin calls them, so R8
# sees dead code; renaming or removing them puts the screen straight back to
# the bug this shipped to fix — a grey "Unsupported" panel that never resolves,
# because the callbacks reporting the failure never arrive.
#
# proguard-android-optimize.txt does carry a rule for @JavascriptInterface
# members. This is here because the failure mode is release-only and silent:
# it works in every debug build, so nothing catches it before a user does.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
