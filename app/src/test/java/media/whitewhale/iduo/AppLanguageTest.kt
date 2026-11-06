package media.whitewhale.iduo

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.Locale

class AppLanguageTest {
    @Test fun `system default comes first and every offered language is declared and translated`() {
        assertEquals("", AppLanguage.tags.first())
        val declared = Regex("android:name=\"([A-Za-z-]+)\"")
            .findAll(File("src/main/res/xml/locales_config.xml").readText()).map { it.groupValues[1] }.toList()
        assertEquals(AppLanguage.tags.drop(1), declared)
        declared.filter { it != "en" }.forEach { tag ->
            val folder = if ('-' in tag) "b+" + tag.replace('-', '+') else tag
            assertTrue(tag, File("src/main/res/values-$folder/strings.xml").isFile)
        }
    }

    @Test fun `languages are listed by their own names`() {
        assertEquals("Čeština", AppLanguage.nativeName("cs"))
        assertEquals("Slovenčina", AppLanguage.nativeName("sk"))
        assertEquals("Polski", AppLanguage.nativeName("pl"))
        assertEquals("Deutsch", AppLanguage.nativeName("de"))
        assertEquals("English", AppLanguage.nativeName("en"))
        assertEquals("简体中文", AppLanguage.nativeName("zh-Hans"))
        assertEquals("日本語", AppLanguage.nativeName("ja"))
        assertEquals("한국어", AppLanguage.nativeName("ko"))
    }

    @Test fun `a stored locale maps to the offered language`() {
        assertEquals("zh-Hans", AppLanguage.tagFor(Locale.forLanguageTag("zh-Hans")))
        assertEquals("zh-Hans", AppLanguage.tagFor(Locale.forLanguageTag("zh-Hans-CN")))
        assertEquals("", AppLanguage.tagFor(Locale.forLanguageTag("zh-Hant")))
        assertEquals("ja", AppLanguage.tagFor(Locale.forLanguageTag("ja-JP")))
        assertEquals("", AppLanguage.tagFor(Locale.forLanguageTag("fr")))
    }

    @Test fun `translations use the same strings and arguments as English`() {
        val entry = Regex("""<(string|plurals) name="([^"]+)"""")
        fun names(path: String) = entry.findAll(File(path).readText()).map { it.groupValues[2] }.toSet()
        val english = names("src/main/res/values/strings.xml") - "left_page_google_news"
        listOf("b+zh+Hans", "ja", "ko").forEach { assertEquals(it, english, names("src/main/res/values-$it/strings.xml")) }
    }
}
