plugins {
    id("reelcounter.jvm.library")
}

dependencies {
    api(projects.core.model)
    implementation(libs.javax.inject)
}
