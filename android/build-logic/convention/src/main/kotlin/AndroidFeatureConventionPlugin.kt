// In the name of God, the Most Gracious, the Most Merciful
import com.raghim.herz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.kotlin.dsl.dependencies

/**
 * A feature sees only the shared core modules. Extra core modules allowed by spec D3
 * (`:core:video`, `:core:security`) are added in the feature's own build file.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("herz.android.library.compose")
        pluginManager.apply("herz.hilt")
        pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            "implementation"(project(":core:model"))
            "implementation"(project(":core:common"))
            "implementation"(project(":core:domain"))
            "implementation"(project(":core:designsystem"))
            "implementation"(project(":core:ui"))
            "implementation"(libs.findLibrary("androidx-lifecycle-runtime-compose").get())
            "implementation"(libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
            "implementation"(libs.findLibrary("androidx-navigation-compose").get())
            "implementation"(libs.findLibrary("androidx-hilt-navigation-compose").get())
            "implementation"(libs.findLibrary("kotlinx-serialization-json").get())
            "testImplementation"(project(":core:testing"))
        }
        forbidFeatureDependencies()
    }

    private fun Project.forbidFeatureDependencies() {
        val self = path
        configurations.configureEach {
            withDependencies {
                filterIsInstance<ProjectDependency>()
                    .firstOrNull { it.path.startsWith(":feature:") && it.path != self }
                    ?.let { error("$self must not depend on feature module ${it.path} (spec D2)") }
            }
        }
    }
}
