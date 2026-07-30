plugins {
    id("netlens.android.library")
}

android {
    namespace = "com.ventouxlabs.netlens.core.data.testing"
}

// DAO doubles for :core:data. Same rationale and same shape as :core:scan-testing — see that
// module's build file for why this is a module rather than a `testFixtures` source set.
//
// This is the home for *every* shared DAO double, not just the one that forced it into existence.
// FakeEndpointDao, FakeWolTargetDao and FakeKnownDeviceDao are still private to :feature:monitor,
// :feature:wol and :feature:devices; they are one copy each today and one edit away from the same
// drift that made FakeNetworkEventDao worth moving. Move them here when you next touch them
// rather than adding a fourth private copy next to the module built to prevent that.
//
// Consumers depend on this with `testImplementation`, so nothing here ships in the APK.
dependencies {
    // `api`: the DAO interfaces and their entity types appear in these fakes' public signatures.
    api(project(":core:data"))
    api(libs.kotlinx.coroutines.core)
}
