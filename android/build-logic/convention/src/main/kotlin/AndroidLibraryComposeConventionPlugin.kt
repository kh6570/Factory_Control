// In the name of God, the Most Gracious, the Most Merciful
import com.android.build.api.dsl.LibraryExtension
import com.raghim.herz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("herz.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
        extensions.configure<LibraryExtension> { buildFeatures.compose = true }
        dependencies {
            val bom = libs.findLibrary("androidx-compose-bom").get()
            "implementation"(platform(bom))
            "implementation"(libs.findLibrary("androidx-compose-ui").get())
            "implementation"(libs.findLibrary("androidx-compose-material3").get())
            "implementation"(libs.findLibrary("androidx-compose-material-icons-core").get())
            "implementation"(libs.findLibrary("androidx-compose-ui-tooling-preview").get())
            "debugImplementation"(libs.findLibrary("androidx-compose-ui-tooling").get())
            "androidTestImplementation"(platform(bom))
        }
    }
}
