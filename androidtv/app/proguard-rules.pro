# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class tv.enktel.app.**$$serializer { *; }
-keepclassmembers class tv.enktel.app.** { *** Companion; }
-keepclasseswithmembers class tv.enktel.app.** { kotlinx.serialization.KSerializer serializer(...); }
