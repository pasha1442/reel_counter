plugins {
    id("reelcounter.android.library")
    id("reelcounter.android.compose")
    id("reelcounter.hilt")
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.detection)
    implementation(projects.core.limits)
    implementation(projects.core.data)
    implementation(projects.core.design)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.serialization.json)
}
