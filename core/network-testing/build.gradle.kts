plugins {
    id("netlens.android.library")
}

android {
    namespace = "com.ventouxlabs.netlens.core.network.testing"
}

// HTTP test fixtures. Same rationale and shape as :core:scan-testing and :core:data-testing —
// see :core:scan-testing's build file for why these are modules rather than `testFixtures`
// source sets.
//
// Named for :core:network even though the fixture is built on Ktor and :core:network deliberately
// ships no HTTP client: the invariant under test is SsrfGuard, which lives there. The Ktor
// dependency is the vehicle, not the subject.
//
// Consumers depend on this with `testImplementation`, so nothing here ships in the APK.
dependencies {
    // `api`: MockEngine and HttpClientEngine appear in the fixtures' public signatures.
    api(libs.ktor.client.core)
    api(libs.ktor.client.mock)
    api(libs.kotlinx.coroutines.core)
}
