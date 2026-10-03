// In the name of God, the Most Gracious, the Most Merciful
import androidx.room.gradle.RoomExtension
import com.raghim.herz.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.google.devtools.ksp")
        pluginManager.apply("androidx.room")
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            "implementation"(libs.findLibrary("room-runtime").get())
            "implementation"(libs.findLibrary("room-ktx").get())
            "ksp"(libs.findLibrary("room-compiler").get())
        }
    }
}
