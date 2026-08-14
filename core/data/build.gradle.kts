plugins {
    id("netlens.android.library")
    id("netlens.hilt")
    id("netlens.android.robolectric")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.ventouxlabs.netlens.core.data"

    // Exposes the exported schema JSONs to Robolectric-backed tests via MigrationTestHelper.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    ksp(libs.room.compiler)

    // Robolectric-backed real Room DAO/migration tests — see netlens.android.robolectric.
    testImplementation(libs.room.testing)
}
