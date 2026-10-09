package com.reelcounter.core.data

import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * Brand-specific steps that keep the counter alive. Starting point: dontkillmyapp.com.
 * Menu names change between OS versions; confirm on the test phones.
 */
object DeviceGuide {
    enum class Brand { XIAOMI, SAMSUNG, OPPO, VIVO, PIXEL, OTHER }

    data class Guide(
        val brandName: String,
        val problem: String,
        val steps: List<String>,
        val accessibilityPath: String,
    )

    fun brand(manufacturer: String = Build.MANUFACTURER): Brand = when (manufacturer.lowercase()) {
        "xiaomi", "redmi", "poco" -> Brand.XIAOMI
        "samsung" -> Brand.SAMSUNG
        "oppo", "realme", "oneplus" -> Brand.OPPO
        "vivo", "iqoo" -> Brand.VIVO
        "google" -> Brand.PIXEL
        else -> Brand.OTHER
    }

    fun guide(brand: Brand = brand()): Guide = when (brand) {
        Brand.XIAOMI -> Guide(
            brandName = "Xiaomi, Redmi and POCO",
            problem = "HyperOS / MIUI closes background apps and turns Autostart off by default.",
            steps = listOf(
                "Open Settings > Apps > Manage apps > Reel Counter",
                "Turn on Autostart",
                "Tap Battery saver and choose No restrictions",
            ),
            accessibilityPath = "Settings > Additional settings > Accessibility > Downloaded apps",
        )
        Brand.SAMSUNG -> Guide(
            brandName = "Samsung",
            problem = "One UI puts apps you don't open often to sleep.",
            steps = listOf(
                "Open Settings > Battery > Background usage limits",
                "Add Reel Counter to Never sleeping apps",
                "In Apps > Reel Counter > Battery, choose Unrestricted",
            ),
            accessibilityPath = "Settings > Accessibility > Installed apps",
        )
        Brand.OPPO -> Guide(
            brandName = "Oppo, Realme and OnePlus",
            problem = "ColorOS limits background activity and auto-launch.",
            steps = listOf(
                "Open Settings > Apps > Reel Counter",
                "Allow Auto launch",
                "In Battery, allow background activity",
            ),
            accessibilityPath = "Settings > Additional settings > Accessibility > Downloaded apps",
        )
        Brand.VIVO -> Guide(
            brandName = "Vivo and iQOO",
            problem = "The system blocks apps that run in the background.",
            steps = listOf(
                "Open Settings > Battery > Background power consumption",
                "Allow high background power use for Reel Counter",
            ),
            accessibilityPath = "Settings > Shortcuts & accessibility > Accessibility",
        )
        Brand.PIXEL, Brand.OTHER -> Guide(
            brandName = if (brand == Brand.PIXEL) "Google Pixel" else Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            problem = "Usually reliable. One setting helps.",
            steps = listOf("Open Settings > Apps > Reel Counter > Battery and choose Unrestricted"),
            accessibilityPath = "Settings > Accessibility > Downloaded apps",
        )
    }

    fun isBatteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
}
