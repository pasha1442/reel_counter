import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("reelcounter.android.library")
            pluginManager.apply("reelcounter.android.compose")
            pluginManager.apply("reelcounter.hilt")

            dependencies {
                add("implementation", project(":core:model"))
                add("implementation", project(":core:limits"))
                add("implementation", project(":core:data"))
                add("implementation", project(":core:design"))

                add("implementation", libs.lib("androidx-core-ktx"))
                add("implementation", libs.lib("androidx-activity-compose"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            }
        }
    }
}
