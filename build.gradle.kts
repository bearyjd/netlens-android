plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    // Not applied here — `netlens.android.screenshot` applies it per module. It has to be declared
    // at the root so the plugin lands on the build classpath; without this the convention plugin
    // fails at apply time with "Plugin with id 'app.cash.paparazzi' not found", because
    // build-logic's compileOnly dependency gives compile-time access and nothing at runtime.
    alias(libs.plugins.paparazzi) apply false
}
