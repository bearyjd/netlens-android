/**
 * Precompiled [gradle-plugins.gradle.kts][Gradle_plugins_gradle] script plugin.
 *
 * @see Gradle_plugins_gradle
 */
public
class GradlePluginsPlugin : org.gradle.api.Plugin<org.gradle.api.Project> {
    override fun apply(target: org.gradle.api.Project) {
        try {
            Class
                .forName("Gradle_plugins_gradle")
                .getDeclaredConstructor(org.gradle.api.Project::class.java, org.gradle.api.Project::class.java)
                .newInstance(target, target)
        } catch (e: java.lang.reflect.InvocationTargetException) {
            throw e.targetException
        }
    }
}
