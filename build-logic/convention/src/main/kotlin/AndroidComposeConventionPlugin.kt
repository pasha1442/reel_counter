import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val app = extensions.findByType(ApplicationExtension::class.java)
            if (app != null) {
                app.buildFeatures.compose = true
            } else {
                extensions.getByType(LibraryExtension::class.java).buildFeatures.compose = true
            }

            configureKotlin(
                listOf(
                    "androidx.compose.material3.ExperimentalMaterial3Api",
                    "androidx.compose.foundation.ExperimentalFoundationApi",
                    "androidx.compose.animation.ExperimentalAnimationApi",
                ),
            )

            dependencies {
                val bom = platform(libs.lib("androidx-compose-bom"))
                add("implementation", bom)
                add("implementation", libs.lib("androidx-compose-ui"))
                add("implementation", libs.lib("androidx-compose-ui-graphics"))
                add("implementation", libs.lib("androidx-compose-foundation"))
                add("implementation", libs.lib("androidx-compose-animation"))
                add("implementation", libs.lib("androidx-compose-material3"))
                add("implementation", libs.lib("androidx-compose-material-icons-extended"))
                add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
                add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
            }
        }
    }
}
