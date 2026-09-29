package media.whitewhale.iduo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AboutTest {
    @Test fun parsesReleasesBulletsAndParagraphs() {
        val releases = parseChangelog("""
            # Changelog

            ## Unreleased

            - Add **search**, see [the guide](docs/user-guide.md).
            - Rename `media.whitewhale.iduo`
              across the app.

            ## 1.0.0

            First release.

            ### Settings
            - New look.
        """.trimIndent())
        assertEquals(listOf("Unreleased", "1.0.0"), releases.map { it.title })
        assertEquals(listOf(
            ChangelogEntry("Add search, see the guide.", bullet = true),
            ChangelogEntry("Rename media.whitewhale.iduo across the app.", bullet = true),
        ), releases[0].entries)
        assertEquals(listOf(ChangelogEntry("First release.", bullet = false),
            ChangelogEntry("Settings", bullet = false, heading = true),
            ChangelogEntry("New look.", bullet = true)), releases[1].entries)
    }

    /** The app shows bundled copies; they must match the files published with the source. */
    @Test fun bundledCopiesMatchTheProjectFiles() {
        fun text(path: String) = File(path).readText().replace("\r\n", "\n")
        assertEquals(text("../CHANGELOG.md"), text("src/main/assets/CHANGELOG.md"))
        assertEquals(text("../LICENSE"), text("src/main/assets/licenses/MIT-DuoLauncher.txt"))
        assertEquals(text("../THIRD_PARTY_NOTICES.md"), text("src/main/assets/licenses/THIRD-PARTY-NOTICES.txt"))
    }

    /** The changelog opens with this version's section, or with the work still to be released. */
    @Test fun realChangelogStartsWithTheCurrentVersion() {
        val version = Regex("versionName = \"([^\"]+)\"").find(File("build.gradle.kts").readText())!!.groupValues[1]
        val releases = parseChangelog(File("src/main/assets/CHANGELOG.md").readText())
        assertTrue(releases.first().title in listOf(version, "Unreleased"))
        assertTrue(releases.any { it.title == version && it.entries.isNotEmpty() })
    }
}
