import java.util.Properties

plugins {
    id("reelcounter.android.application")
    id("reelcounter.android.compose")
    id("reelcounter.hilt")
    alias(libs.plugins.kotlin.serialization)
}

// Firebase (sign-in, crash reports, usage stats) switches on only when the project's
// google-services.json is present. Without it the app builds and runs with those features off.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}

/** Keys that never go into git: local.properties (RevenueCat) and keystore.properties (signing). */
fun loadProperties(name: String): Properties? =
    rootProject.file(name).takeIf { it.exists() }?.let { file -> Properties().apply { file.inputStream().use(::load) } }

val localProperties = loadProperties("local.properties")
val keystoreProperties = loadProperties("keystore.properties")

/** Plain project settings anyone may see, from gradle.properties. */
fun setting(name: String): String = (findProperty(name) as String?).orEmpty()

android {
    namespace = "com.reelcounter.app"

    defaultConfig {
        // Working ID: it becomes permanent after the first Play upload, so confirm it before then.
        applicationId = "com.reelcounter.android"
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "REVENUECAT_API_KEY", "\"${localProperties?.getProperty("revenuecat.apiKey").orEmpty()}\"")
        buildConfigField("String", "SUPPORT_EMAIL", "\"${setting("reelcounter.supportEmail")}\"")
        buildConfigField("String", "LEGAL_BASE_URL", "\"${setting("reelcounter.legalBaseUrl")}\"")
        buildConfigField("boolean", "DEMO_MODE", "false")
    }

    buildFeatures {
        buildConfig = true
    }

    // The public legal pages (docs/, ready for GitHub Pages) ship inside the app too, so the
    // in-app links always work and always match this version.
    sourceSets.getByName("main").assets.srcDir(rootProject.file("docs"))

    signingConfigs {
        if (keystoreProperties != null) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Store screenshots (goldie/): a release build that seeds sample data on first launch.
        // Never uploaded; its own app ID so it installs beside the real app.
        create("screenshots") {
            initWith(getByName("release"))
            applicationIdSuffix = ".screenshots"
            versionNameSuffix = "-screenshots"
            matchingFallbacks += "release"
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            buildConfigField("boolean", "DEMO_MODE", "true")
        }
    }
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.core.design)
    implementation(projects.core.limits)
    implementation(projects.service)
    implementation(projects.feature.onboarding)
    implementation(projects.feature.home)
    implementation(projects.feature.history)
    implementation(projects.feature.limits)
    implementation(projects.feature.friends)
    implementation(projects.feature.settings)
    implementation(projects.feature.account)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.revenuecat.purchases)
}
