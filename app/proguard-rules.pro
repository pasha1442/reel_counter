# kotlinx.serialization, Room, Hilt, WorkManager and Glance ship their own consumer rules.
# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotation referenced by Play Core review-ktx but not shipped with it; nothing uses it at runtime.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
