package com.cedrickgd.morgenbriefing.data

import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.FeedSource
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.Place
import com.cedrickgd.morgenbriefing.data.model.Topic
import com.cedrickgd.morgenbriefing.data.model.TopicIcon
import com.cedrickgd.morgenbriefing.data.model.Topics
import com.cedrickgd.morgenbriefing.data.news.Taxonomy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SettingsMigrationTest {

    private val known: (String) -> Boolean = { Taxonomy.isBuiltIn(it) || it == "custom:ab" }

    @Test
    fun `existing installs keep the German news, fresh ones start with the phone's country`() {
        assertEquals(NewsEdition.GERMANY, SettingsMigration.edition(null, onboardingDone = true, Locale.US))
        assertEquals(NewsEdition.USA, SettingsMigration.edition(null, onboardingDone = false, Locale.US))
        assertEquals(NewsEdition("FR", "fr"), SettingsMigration.edition(null, onboardingDone = false, Locale.FRANCE))
        assertEquals(NewsEdition("GB", "en"), SettingsMigration.edition("GB:en", onboardingDone = true, Locale.GERMANY))
        assertEquals(NewsEdition.GERMANY, SettingsMigration.edition("XX:yy", onboardingDone = true, Locale.US))
    }

    @Test
    fun `stored editions keep their meaning, pairs chosen independently are read back`() {
        // Whatever an older version stored is the same edition today.
        for (old in listOf("DE:de", "US:en", "CH:fr", "MX:es", "JP:ja")) {
            val read = SettingsMigration.edition(old, onboardingDone = true, Locale.US)
            assertEquals(old, read.key)
            assertTrue(old, read.isNative)
        }
        // Region and language chosen independently: stored as country:language like before.
        assertEquals(NewsEdition("US", "de"), SettingsMigration.edition("US:de", onboardingDone = true, Locale.US))
        assertEquals(NewsEdition("DE", "en"), SettingsMigration.edition("DE:en", onboardingDone = true, Locale.GERMANY))
        assertEquals(NewsEdition("JP", "it"), SettingsMigration.edition("JP:it", onboardingDone = false, Locale.GERMANY))
    }

    @Test
    fun `a fresh install starts with the phone's country in the phone's language`() {
        fun fresh(tag: String) = SettingsMigration.edition(null, onboardingDone = false, Locale.forLanguageTag(tag))
        assertEquals(NewsEdition.GERMANY, fresh("de-DE"))
        // An English phone in Germany, a German phone in the USA: both the phone's.
        assertEquals(NewsEdition("DE", "en"), fresh("en-DE"))
        assertEquals(NewsEdition("US", "de"), fresh("de-US"))
        assertEquals(NewsEdition("US", "es"), fresh("es-US"))
        // A language the app has no news in: the country's main language.
        assertEquals(NewsEdition("IN", "en"), fresh("hi-IN"))
        // A country it does not offer: the home edition of the language.
        assertEquals(NewsEdition.GERMANY, fresh("de-LU"))
        assertEquals(NewsEdition.USA, fresh("en-NZ"))
        // Neither: the US edition.
        assertEquals(NewsEdition.USA, fresh("nb-NO"))
    }

    @Test
    fun `the old order minus the hidden topics becomes the followed list`() {
        val followed = SettingsMigration.followed(
            stored = null,
            legacyOrder = listOf("sport", "custom:ab", "ki", "gibts-nicht"),
            legacyHidden = setOf("ki", "klima"),
            onboardingDone = true,
            edition = NewsEdition.GERMANY,
            customIds = listOf("custom:ab"),
            known = known,
        )
        assertEquals(listOf("sport", "custom:ab", "wirtschaft", "politik", "welt", "region", "wissenschaft", "gesundheit", "entertainment"), followed)
        // Existing users do not get the new categories pushed into their feed.
        assertFalse(Taxonomy.TOP in followed || "auto" in followed)
    }

    @Test
    fun `an install that never stored an order showed all ten topics`() {
        val followed = SettingsMigration.followed(null, null, emptySet(), onboardingDone = true, NewsEdition.GERMANY, emptyList(), known)
        assertEquals(Topics.LEGACY_ORDER, followed)
    }

    @Test
    fun `fresh installs get the defaults of their edition, stored lists win`() {
        assertEquals(
            Taxonomy.defaultFollowed(NewsEdition.USA),
            SettingsMigration.followed(null, null, emptySet(), onboardingDone = false, NewsEdition.USA, emptyList(), known),
        )
        assertEquals(
            listOf("wirtschaft/boerse", "custom:ab"),
            SettingsMigration.followed(listOf("wirtschaft/boerse", "custom:weg", "custom:ab", "wirtschaft/boerse"), listOf("ki"), emptySet(), true, NewsEdition.GERMANY, emptyList(), known),
        )
        // Everything hidden: the defaults rather than an empty feed.
        assertEquals(
            Taxonomy.defaultFollowed(NewsEdition.GERMANY),
            SettingsMigration.followed(null, Topics.LEGACY_ORDER, Topics.LEGACY_ORDER.toSet(), true, NewsEdition.GERMANY, emptyList(), known),
        )
    }
}

class SettingsOpsTest {

    private val base = AppSettings(followed = listOf("top", "politik", "wirtschaft"))

    @Test
    fun `follow, unfollow and reorder`() {
        assertEquals(listOf("top", "politik", "wirtschaft", "wirtschaft/boerse"), SettingsOps.follow(base, "wirtschaft/boerse").followed)
        assertEquals(listOf("ki", "top", "politik", "wirtschaft"), SettingsOps.follow(base, "ki", index = 0).followed)
        assertEquals(base.followed, SettingsOps.follow(base, "politik").followed)
        assertEquals(listOf("top", "wirtschaft"), SettingsOps.unfollow(base, "politik").followed)
        val single = AppSettings(followed = listOf("top"))
        assertFalse(SettingsOps.canUnfollow(single, "top"))
        assertEquals(listOf("top"), SettingsOps.unfollow(single, "top").followed)
        assertEquals(listOf("wirtschaft", "top", "politik"), SettingsOps.reorder(base, listOf("wirtschaft", "sport", "top")).followed)
        assertEquals(listOf("politik"), SettingsOps.setFollowed(AppSettings(followed = listOf("politik", "ki")), "ki", false).followed)
    }

    @Test
    fun `places are followed once and leave cleanly`() {
        val dc = Place("Washington", 38.895, -77.036, "District of Columbia, Vereinigte Staaten", "US")
        val (added, id) = SettingsOps.addLocalPlace(base, dc)
        assertEquals(listOf(dc), added.localPlaces)
        assertEquals(id, added.followed.last())
        val (again, sameId) = SettingsOps.addLocalPlace(added, dc)
        assertEquals(id, sameId)
        assertEquals(1, again.localPlaces.size)
        assertEquals(added.followed, again.followed)
        val removed = SettingsOps.removeLocalPlace(again, id)
        assertTrue(removed.localPlaces.isEmpty())
        assertFalse(id in removed.followed)
    }

    @Test
    fun `following a search makes an own topic with the query`() {
        val (followed, id) = SettingsOps.followSearch(base, "  Midterms  ")
        val topic = followed.customTopics.single()
        assertEquals(id, topic.id)
        assertTrue(Topics.isCustomId(id))
        assertEquals("Midterms", topic.title)
        assertEquals("Midterms", topic.query)
        assertEquals(TopicIcon.SEARCH.name, topic.icon)
        assertTrue(topic.custom)
        assertEquals(id, followed.followed.last())
        // The same search again follows the existing topic.
        val (twice, sameId) = SettingsOps.followSearch(SettingsOps.unfollow(followed, id), "midterms")
        assertEquals(id, sameId)
        assertEquals(1, twice.customTopics.size)
        assertTrue(id in twice.followed)
        assertEquals("Midterms", Topics.resolve(id, twice)?.title)
    }

    @Test
    fun `own topics come and go with their feeds`() {
        val topic = Topic("custom:x", "Blog", "🦉", 0xFF2F6FDE)
        val added = SettingsOps.addCustomTopic(base, topic)
        assertTrue(added.customTopics.single().custom)
        assertEquals("custom:x", added.followed.last())
        val feed = FeedSource("custom:f", "Blog", "https://blog.example/feed", "custom:x", builtIn = false)
        val deleted = SettingsOps.deleteCustomTopic(added.copy(customFeeds = listOf(feed)), "custom:x")
        assertTrue(deleted.customTopics.isEmpty() && deleted.customFeeds.isEmpty())
        assertFalse("custom:x" in deleted.followed)
        assertEquals(Topics.DEFAULT_ORDER, SettingsOps.followAll(AppSettings(followed = listOf("ki"))).followed)
    }

    @Test
    fun `recent searches stay short, newest first, without repeats`() {
        var s = AppSettings()
        listOf("Midterms", "DAX", " midterms ", "", "Bitcoin").forEach { s = SettingsOps.addRecentSearch(s, it) }
        assertEquals(listOf("Bitcoin", "midterms", "DAX"), s.recentSearches)
        repeat(20) { s = SettingsOps.addRecentSearch(s, "q$it") }
        assertEquals(SettingsOps.MAX_RECENT_SEARCHES, s.recentSearches.size)
        assertEquals("q19", s.recentSearches.first())
        assertNotEquals(s.recentSearches, SettingsOps.removeRecentSearch(s, "Q19").recentSearches)
        assertTrue(SettingsOps.clearRecentSearches(s).recentSearches.isEmpty())
    }
}
