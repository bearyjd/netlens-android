plugins {
    id("netlens.android.library")
}

android {
    namespace = "com.ventouxlabs.netlens.core.data.testing"
}

// Test doubles for :core:data — DAOs, and the storage seams UserPreferencesRepository is built
// on. Same rationale and same shape as :core:scan-testing — see that module's build file for why
// this is a module rather than a `testFixtures` source set.
//
// This is the home for *every* shared :core:data double, not just the one that forced it into
// existence. FakeDataStore (2 copies) and FakeKeyValueStore (3 copies) moved here on 2026-08-05;
// none had drifted yet, which is the only reason the move was free.
//
// FakeEndpointDao, FakeWolTargetDao and FakeKnownDeviceDao are still private to :feature:monitor,
// :feature:wol and :feature:devices; they are one copy each today and one edit away from the same
// drift that made FakeNetworkEventDao worth moving. Move them here when you next touch them
// rather than adding a fourth private copy next to the module built to prevent that.
//
// Consumers depend on this with `testImplementation`, so nothing here ships in the APK.
dependencies {
    // `api`: the DAO interfaces and their entity types appear in these fakes' public signatures,
    // as do DataStore<Preferences> and KeyValueStore.
    api(project(":core:data"))
    api(libs.kotlinx.coroutines.core)
    api(libs.datastore.preferences)
}
