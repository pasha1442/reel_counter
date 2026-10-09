import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

object AndroidSdk {
    const val MIN = 28 // Android 9: first release with scroll deltas on accessibility events.
    const val TARGET = 36
    const val COMPILE = 37
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).orElseThrow { IllegalArgumentException("No library '$alias' in libs.versions.toml") }

/** ":core:data" -> "com.reelcounter.core.data" */
internal val Project.derivedNamespace: String
    get() = "com.reelcounter." + path.removePrefix(":").replace(':', '.').replace('-', '_')

internal val CoroutineOptIns = listOf(
    "kotlinx.coroutines.ExperimentalCoroutinesApi",
    "kotlinx.coroutines.FlowPreview",
)

internal fun Project.configureKotlin(optIns: List<String> = emptyList()) {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            optIn.addAll(optIns)
        }
    }
}
