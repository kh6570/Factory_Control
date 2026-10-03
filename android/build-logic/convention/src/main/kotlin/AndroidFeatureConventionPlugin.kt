// In the name of God, the Most Gracious, the Most Merciful
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
        dependencies {
            "implementation"(project(":core:model"))
            "implementation"(project(":core:common"))
            "implementation"(project(":core:domain"))
            "implementation"(project(":core:designsystem"))
            "implementation"(project(":core:ui"))
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
