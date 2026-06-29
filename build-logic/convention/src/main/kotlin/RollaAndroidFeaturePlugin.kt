import org.gradle.api.Plugin
import org.gradle.api.Project

class RollaAndroidFeaturePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("rolla.android.library")
                apply("rolla.android.library.compose")
                apply("rolla.android.hilt")
            }
        }
    }
}
