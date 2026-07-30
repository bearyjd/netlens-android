plugins {
    id("netlens.android.library")
}

android {
    namespace = "com.ventouxlabs.netlens.core.scan.testing"
}

// Test doubles for :core:scan's engine seams, in a module of their own rather than a
// `testFixtures` source set: AGP registers the testFixtures variant, but Kotlin 2.1.0 does not
// add a Kotlin compilation for it — there is a compileDebugTestFixturesJavaWithJavac task and no
// Kotlin equivalent — so Kotlin fixtures silently never compile. A plain library module is the
// alternative the roadmap already sanctioned, and it works on every consumer today.
//
// Consumers depend on this with `testImplementation`, so nothing here ships in the APK.
dependencies {
    // `api`: LanDevice, SsdpDevice, NetBiosInfo, OuiLookup and Flow all appear in these fakes'
    // public signatures, so a consumer cannot use them without these on its compile classpath.
    api(project(":core:scan"))
    api(project(":core:oui"))
    api(libs.kotlinx.coroutines.core)
}
