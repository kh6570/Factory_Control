// In the name of God, the Most Gracious, the Most Merciful
import com.raghim.herz.buildlogic.HerzSdk
import com.raghim.herz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Pure Kotlin module: no Android classes allowed (spec D2, domain layer). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("org.jetbrains.kotlin.jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = HerzSdk.JAVA
            targetCompatibility = HerzSdk.JAVA
        }
        extensions.configure<KotlinJvmProjectExtension> {
            compilerOptions.jvmTarget.set(JvmTarget.fromTarget(HerzSdk.JAVA.toString()))
        }
        dependencies {
            "testImplementation"(libs.findLibrary("junit").get())
        }
    }
}
