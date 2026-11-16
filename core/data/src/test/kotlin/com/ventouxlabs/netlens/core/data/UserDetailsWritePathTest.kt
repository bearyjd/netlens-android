package com.ventouxlabs.netlens.core.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Architecture pin: `KnownDeviceDao.updateUserDetails` is an unguarded write of all seven user
 * fields, so production code may reach it only through the guarded `saveUserDetails` (and the
 * shared fakes that implement it). A UI caller would bring back the stale-editor overwrite.
 */
class UserDetailsWritePathTest {

    private val repoRoot: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    @Test
    fun `only the guard and the shared fakes call updateUserDetails in production sources`() {
        val callers = callersOf(repoRoot)

        assertTrue(callers.isNotEmpty(), "the scan found no sources: wrong root ${repoRoot.path}")
        assertEquals(ALLOWED, callers)
    }

    @Test
    fun `a new caller is flagged however the call is spelled`(@TempDir root: File) {
        fun write(path: String, text: String) = File(root, path).apply { parentFile.mkdirs() }.writeText(text)
        write("feature/x/src/main/kotlin/Spaced.kt", "dao.updateUserDetails (1)")
        write("feature/x/src/main/kotlin/Ticked.kt", "dao.`updateUserDetails`(1)")
        write("feature/x/src/main/kotlin/Wrapped.kt", "dao.updateUserDetails\n    (1)")
        write("feature/x/src/main/kotlin/Mentioned.kt", "// see updateUserDetails")
        write("feature/x/src/test/kotlin/InTest.kt", "dao.updateUserDetails(1)")
        // Same file name as an allowed path, wrong location: still a caller.
        write("feature/x/src/main/kotlin/KnownDeviceDao.kt", "dao.updateUserDetails(1)")

        assertEquals(
            sortedSetOf(
                "feature/x/src/main/kotlin/KnownDeviceDao.kt",
                "feature/x/src/main/kotlin/Spaced.kt",
                "feature/x/src/main/kotlin/Ticked.kt",
                "feature/x/src/main/kotlin/Wrapped.kt",
            ),
            callersOf(root),
        )
    }

    private fun callersOf(root: File): Set<String> = root.walkTopDown()
        .onEnter { it.name != "build" && !it.name.startsWith(".") }
        .filter { it.isFile && it.extension == "kt" }
        .map { it.relativeTo(root).invariantSeparatorsPath to it }
        .filter { (path, _) -> path.contains("/src/main/") }
        .filter { (_, file) -> CALL.containsMatchIn(file.readText()) }
        .mapTo(sortedSetOf()) { it.first }

    private companion object {
        val CALL = Regex("`?updateUserDetails`?\\s*\\(")
        val ALLOWED = sortedSetOf(
            "core/data/src/main/kotlin/com/ventouxlabs/netlens/core/data/dao/KnownDeviceDao.kt",
            "core/data-testing/src/main/kotlin/com/ventouxlabs/netlens/core/data/testing/FakeDeviceIdentityDao.kt",
            "core/data-testing/src/main/kotlin/com/ventouxlabs/netlens/core/data/testing/FakeKnownDeviceDao.kt",
        )
    }
}
