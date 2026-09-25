package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AppLanguageTest {
    @Test fun `system default comes first and every offered language is declared and translated`() {
        assertEquals("", AppLanguage.tags.first())
        val declared = Regex("android:name=\"([a-z]+)\"")
            .findAll(File("src/main/res/xml/locales_config.xml").readText()).map { it.groupValues[1] }.toList()
        assertEquals(AppLanguage.tags.drop(1), declared)
        declared.filter { it != "en" }.forEach { assertTrue(it, File("src/main/res/values-$it/strings.xml").isFile) }
    }

    @Test fun `languages are listed by their own names`() {
        assertEquals("Čeština", AppLanguage.nativeName("cs"))
        assertEquals("Slovenčina", AppLanguage.nativeName("sk"))
        assertEquals("Polski", AppLanguage.nativeName("pl"))
        assertEquals("Deutsch", AppLanguage.nativeName("de"))
        assertEquals("English", AppLanguage.nativeName("en"))
    }
}
