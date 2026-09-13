package tv.enktel.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.enktel.app.data.repo.SportsRepository
import tv.enktel.app.data.repo.Subscribe
import tv.enktel.app.i18n.AllStrings
import tv.enktel.app.i18n.EnglishStrings
import tv.enktel.app.i18n.Lang
import tv.enktel.app.i18n.SerboCroatianStrings
import tv.enktel.app.i18n.stringsFor
import tv.enktel.app.ui.components.FULL_ITEMS
import tv.enktel.app.ui.components.MORE_ITEM
import tv.enktel.app.ui.components.hiddenInSimpleMenu
import java.lang.reflect.Modifier

/**
 * The half-translated screen, and how to stop shipping one.
 *
 * A second language fails quietly. Nothing throws when a string is missed —
 * the app simply shows one English word in the middle of a Serbo-Croatian
 * menu, and the only person who finds out is a subscriber who cannot read the
 * English and now cannot tell whether the app is broken or they are. So the
 * checks here are about *absence*: a menu entry with no translation, a tile
 * with no description, a field left as a copy of the English.
 *
 * The last of those needs care. Some words are genuinely identical in both —
 * "Sport" is "Sport" — so an exact-match rule would either fail on correct
 * translations or have to be switched off. [thingsThatAreTheSameInBoth] names
 * them individually, which turns "this one is untranslated" into a deliberate
 * one-line claim rather than a blanket exemption.
 */
class StringsTest {

    /**
     * Fields where English and Serbo-Croatian are correctly the same string.
     *
     * Each is a loanword or an international abbreviation, not an oversight.
     * Anything not on this list that matches its English is a field somebody
     * copied and did not come back to.
     */
    private val thingsThatAreTheSameInBoth = setOf(
        // "Sport" is the word in both. The menu entry beside it is not:
        // English pluralises it ("Sports") and Serbo-Croatian does not.
        "railSport",
    )

    /**
     * Sports whose name is the same word in both. All loanwords.
     *
     * Listed one at a time for the same reason as the field exemptions above:
     * a blanket skip would hide the next twenty that were never translated.
     */
    private val sportsThatAreTheSameInBoth = setOf("Golf")

    private fun fieldsOf(o: Any): Map<String, String> =
        o.javaClass.declaredFields
            .filterNot { it.isSynthetic || Modifier.isStatic(it.modifiers) }
            .filter { it.type == String::class.java }
            .associate { f -> f.isAccessible = true; f.name to (f.get(o) as String) }

    @Test
    fun `every catalogue fills every field`() {
        for (lang in AllStrings) {
            val blank = fieldsOf(lang).filterValues { it.isBlank() }.keys
            assertTrue("${lang.id} leaves these empty: $blank", blank.isEmpty())
        }
    }

    @Test
    fun `the catalogues carry the same fields`() {
        // A data class makes this true by construction today. It is asserted
        // anyway because the failure mode if it ever stops being one — a
        // language built from a map or a copy() with a default — is a field
        // that silently reads English forever.
        assertEquals(fieldsOf(EnglishStrings).keys, fieldsOf(SerboCroatianStrings).keys)
    }

    @Test
    fun `nothing was left as a copy of the English`() {
        val en = fieldsOf(EnglishStrings)
        val sh = fieldsOf(SerboCroatianStrings)
        val untranslated = en.keys
            .filter { it != "id" && it != "endonym" }
            .filterNot { it in thingsThatAreTheSameInBoth }
            .filter { en[it] == sh[it] }
        assertTrue(
            "these read identically in both languages — translate them, or say " +
                "why in thingsThatAreTheSameInBoth: $untranslated",
            untranslated.isEmpty(),
        )
    }

    @Test
    fun `no sport was left as a copy of the English`() {
        val untranslated = SportsRepository.SPORT_NAMES
            .filterNot { it in sportsThatAreTheSameInBoth }
            .filter { EnglishStrings.sport(it) == SerboCroatianStrings.sport(it) }
        assertTrue(
            "these sports read identically in both — translate them, or say why " +
                "in sportsThatAreTheSameInBoth: $untranslated",
            untranslated.isEmpty(),
        )
    }

    @Test
    fun `every menu entry has a label in every language`() {
        // Walks the real menu, so a destination added to the rail and
        // forgotten in the catalogue fails here rather than appearing in
        // English inside a translated column.
        for (lang in AllStrings) {
            for (item in FULL_ITEMS + MORE_ITEM) {
                assertNotNull(
                    "${lang.id} has no label for the '${item.id}' menu entry",
                    lang.navLabel(item.id),
                )
            }
        }
    }

    @Test
    fun `every More tile has a description in every language`() {
        for (lang in AllStrings) {
            for (item in hiddenInSimpleMenu()) {
                assertNotNull(
                    "${lang.id} has no More blurb for '${item.id}'",
                    lang.moreBlurb(item.id),
                )
            }
        }
    }

    @Test
    fun `every sport the classifier can emit has a name in every language`() {
        // Walks SportsRepository's own tag list rather than a copy of it, so
        // adding a sport to the classifier and forgetting the catalogue fails
        // here instead of printing "Wrestling" inside a Serbo-Croatian card.
        for (lang in AllStrings) {
            val missing = SportsRepository.SPORT_NAMES.filterNot { it in lang.sports }
            assertTrue("${lang.id} has no name for: $missing", missing.isEmpty())
        }
    }

    @Test
    fun `an unknown sport falls back to its English tag rather than to nothing`() {
        assertEquals("Kabaddi", SerboCroatianStrings.sport("Kabaddi"))
        assertEquals("Fudbal", SerboCroatianStrings.sport("Football"))
    }

    @Test
    fun `dates follow the app's language, not the device's`() {
        // A fixture card reading "Sun 7:11 AM · Fudbal" is the half-and-half
        // that makes a translation feel unfinished. hr is Latin-only, so the
        // day names come back Latin whatever the device's ICU data prefers.
        assertEquals("hr", SerboCroatianStrings.locale.language)
        assertEquals("EEE HH:mm", SerboCroatianStrings.dayTimeFormat)
        assertEquals("EEE h:mm a", EnglishStrings.dayTimeFormat)
    }

    @Test
    fun `an unknown id resolves to nothing rather than to something wrong`() {
        assertNull(EnglishStrings.navLabel("nope"))
        assertNull(EnglishStrings.moreBlurb("nope"))
    }

    // ---- picking a language ------------------------------------------------

    @Test
    fun `an explicit choice beats the device`() {
        assertEquals(Lang.EN, Lang.resolve(Lang.EN, listOf("hr-HR")))
        assertEquals(Lang.SH, Lang.resolve(Lang.SH, listOf("en-GB")))
    }

    @Test
    fun `the four standards all land on one translation`() {
        // Croatian, Serbian, Bosnian and Montenegrin are mutually intelligible
        // and share one Latin-script catalogue. A phone set to any of them
        // should not need anyone to find the setting.
        for (tag in listOf("hr-HR", "sr-RS", "bs-BA", "cnr-ME", "sr-Latn-RS", "sh")) {
            assertEquals("$tag should resolve to Serbo-Croatian", Lang.SH, Lang.resolve(Lang.SYSTEM, listOf(tag)))
        }
    }

    @Test
    fun `anything else falls back to English`() {
        assertEquals(Lang.EN, Lang.resolve(Lang.SYSTEM, listOf("en-AU")))
        assertEquals(Lang.EN, Lang.resolve(Lang.SYSTEM, listOf("de-DE", "fr-FR")))
        assertEquals(Lang.EN, Lang.resolve(Lang.SYSTEM, emptyList()))
    }

    @Test
    fun `the device's first preference wins`() {
        // A device listing Hrvatski above English gets Serbo-Croatian, and one
        // listing them the other way round gets English. Scanning the whole
        // list without honouring its order is the bug this pins.
        assertEquals(Lang.SH, Lang.resolve(Lang.SYSTEM, listOf("hr-HR", "en-GB")))
        assertEquals(Lang.EN, Lang.resolve(Lang.SYSTEM, listOf("en-GB", "hr-HR")))
    }

    @Test
    fun `an unrecognised stored setting does not strand anyone`() {
        // A build that once wrote a language id this one no longer has must
        // fall back rather than render blanks.
        assertEquals(Lang.EN, Lang.resolve("klingon", listOf("de-DE")))
        assertEquals(EnglishStrings, stringsFor("klingon"))
    }

    // ---- counting days in a Slavic language --------------------------------

    @Test
    fun `Serbo-Croatian agrees three ways with the day count`() {
        val t = SerboCroatianStrings
        assertEquals("1 dan", t.days(1))
        assertEquals("2 dana", t.days(2))
        assertEquals("4 dana", t.days(4))
        assertEquals("5 dana", t.days(5))
        // The teens are the exception that catches a naive n % 10 rule: 11, 12
        // and 14 all take "dana" despite ending in 1, 2 and 4.
        assertEquals("11 dana", t.days(11))
        assertEquals("12 dana", t.days(12))
        assertEquals("14 dana", t.days(14))
        // And then it starts again.
        assertEquals("21 dan", t.days(21))
        assertEquals("22 dana", t.days(22))
    }

    @Test
    fun `English keeps its two forms`() {
        assertEquals("1 day", EnglishStrings.days(1))
        assertEquals("2 days", EnglishStrings.days(2))
        assertEquals("21 days", EnglishStrings.days(21))
    }

    @Test
    fun `the expiry notice speaks whichever language it is handed`() {
        assertEquals(
            "This line expires in 3 days.",
            Subscribe.expiryNotice(3, expired = false, t = EnglishStrings),
        )
        assertEquals(
            "Pretplata ističe za 3 dana.",
            Subscribe.expiryNotice(3, expired = false, t = SerboCroatianStrings),
        )
        // 21 rather than 20: the notice window is 14 days, so this is above
        // it and returns null — which is the point of the next assertion,
        // not of this one. The teens case is covered by days() above.
        assertNull(Subscribe.expiryNotice(21, expired = false, t = SerboCroatianStrings))
    }

    @Test
    fun `the expiry notice still defaults to English for callers that have no language`() {
        assertTrue(Subscribe.expiryNotice(3, expired = false)!!.contains("3 days"))
    }

    // ---- format strings ----------------------------------------------------

    @Test
    fun `placeholders survive translation`() {
        // A %s dropped in translation throws at runtime on the screen that
        // uses it, which for connectingTo is the sign-in screen — the very
        // first thing a new subscriber sees.
        val oneS = listOf("connectingTo", "noAccountYet", "errSignedInNoChannels", "noMatchesFor", "lineExpiresIn")
        val oneD = listOf("alsoOnMore")
        for (lang in AllStrings) {
            val f = fieldsOf(lang)
            for (name in oneS) {
                assertEquals("${lang.id}.$name must carry exactly one %s", 1, f.getValue(name).count("%s"))
            }
            for (name in oneD) {
                assertEquals("${lang.id}.$name must carry exactly one %d", 1, f.getValue(name).count("%d"))
            }
        }
    }

    private fun String.count(needle: String): Int {
        var i = indexOf(needle)
        var n = 0
        while (i >= 0) { n++; i = indexOf(needle, i + needle.length) }
        return n
    }
}
