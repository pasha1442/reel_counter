plugins {
    id("reelcounter.android.feature")
}

dependencies {
    // Google sign-in through Android's Credential Manager.
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)
}
