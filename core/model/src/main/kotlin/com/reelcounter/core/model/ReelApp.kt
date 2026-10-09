package com.reelcounter.core.model

/** The short-video surfaces we count at launch. Adding an app here also needs a rules pack entry. */
enum class ReelApp(
    val packageName: String,
    val displayName: String,
    val surfaceName: String,
) {
    INSTAGRAM(packageName = "com.instagram.android", displayName = "Instagram", surfaceName = "Reels"),
    YOUTUBE(packageName = "com.google.android.youtube", displayName = "YouTube", surfaceName = "Shorts"),
    ;

    companion object {
        fun fromPackage(packageName: CharSequence?): ReelApp? {
            if (packageName == null) return null
            val name = packageName.toString()
            return entries.firstOrNull { it.packageName == name }
        }

        fun fromName(name: String): ReelApp? = entries.firstOrNull { it.name == name }
    }
}
