plugins {
    id("reelcounter.android.library")
    id("reelcounter.android.compose")
}

dependencies {
    api(projects.core.model)
    implementation(libs.androidx.core.ktx)
}
