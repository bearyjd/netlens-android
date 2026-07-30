plugins {
    id("netlens.android.library")
}

android {
    namespace = "com.ventouxlabs.netlens.core.data.testing"
}

// DAO doubles for :core:data. Same rationale and same shape as :core:scan-testing — see that
// module's build file for why this is a module rather than a `testFixtures` source set.
//
// Consumers depend on this with `testImplementation`, so nothing here ships in the APK.
dependencies {
    // `api`: the DAO interfaces and their entity types appear in these fakes' public signatures.
    api(project(":core:data"))
    api(libs.kotlinx.coroutines.core)
}
