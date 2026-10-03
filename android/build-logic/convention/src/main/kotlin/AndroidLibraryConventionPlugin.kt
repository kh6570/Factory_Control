// In the name of God, the Most Gracious, the Most Merciful
import com.android.build.api.dsl.LibraryExtension
import com.raghim.herz.buildlogic.configureAndroidLibrary
import com.raghim.herz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.library")
        extensions.configure<LibraryExtension> { configureAndroidLibrary(this) }
        dependencies {
            "testImplementation"(libs.findLibrary("junit").get())
        }
    }
}
