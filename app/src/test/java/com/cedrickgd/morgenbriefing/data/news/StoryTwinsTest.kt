package com.cedrickgd.morgenbriefing.data.news

import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The same article from two feeds of one publisher (the pairs seen on the emulator, 15.11.2026). */
class StoryTwinsTest {

    private fun own(id: String, headline: String, site: String, source: String, at: String? = "2026-11-15T10:00:00+01:00") = NewsItem(
        id = id, headline = headline, url = "https://www.$site/artikel/$id", source = source, sourceId = "feed-$source",
        topicId = "politik", publishedAt = at, publisherDomain = site,
    )

    private fun google(id: String, headline: String, site: String, source: String, at: String? = "2026-11-15T10:04:00+01:00") = NewsItem(
        id = id, headline = headline, url = "https://news.google.com/rss/articles/CBMi$id?oc=5", source = source, sourceId = "gn:top@DE:de",
        topicId = "top", publishedAt = at, publisherDomain = site, aggregator = "google",
    )

    private fun same(a: String, b: String) = StoryTwins.sameStory(StoryTwins.headline(a), StoryTwins.headline(b))

    @Test
    fun `a publisher is its site, whatever its feeds are called`() {
        assertEquals("taz.de", StoryTwins.family(own("1", "x", "taz.de", "taz Politik")))
        assertEquals("taz.de", StoryTwins.family(google("2", "x", "taz.de", "taz.de")))
        assertEquals("n-tv.de", StoryTwins.family("www.n-tv.de"))
        assertEquals("heise.de", StoryTwins.family("www.heise.de", "heise IT"))
        // Sub-domains and country domains.
        assertEquals("zeit.de", StoryTwins.family("newsfeed.zeit.de"))
        assertEquals("bbc.com", StoryTwins.family("feeds.bbc.co.uk"))
        assertEquals("abc.net.au", StoryTwins.family("www.abc.net.au"))
        // One newsroom, two sites.
        assertEquals("zdf.de", StoryTwins.family("www.zdfheute.de"))
        // No site known: the name the feed gives.
        assertEquals("name:tonline", StoryTwins.family("", "T-Online"))
        assertEquals("", StoryTwins.family("", " "))
        // A publisher's own item without a stored domain: the link's host; a Google News link names nobody.
        assertEquals("tagesschau.de", StoryTwins.family(own("3", "x", "tagesschau.de", "tagesschau").copy(publisherDomain = "")))
        assertEquals("name:tagesschau", StoryTwins.family(google("4", "x", "", "tagesschau")))
    }

    @Test
    fun `nearly the same headline is the same story, a different one is not`() {
        // Google News drops the kicker now and then.
        assertTrue(same("Rekruten aus aller Welt: Russland lockt Ausländer mit Fake-Jobs in den Krieg", "Russland lockt Ausländer mit Fake-Jobs in den Krieg"))
        // A word changed, quotes, case.
        assertTrue(same("Daten-GAU in Berlin: Forensiker über den Rhysida-Leak", "Daten-GAU in Berlin – Forensiker zum Rhysida-Leak"))
        assertTrue(same("„Der Krieg auf hoher See wird bereits morgen beginnen“", "\"Der Krieg auf hoher See wird bereits morgen beginnen\""))
        // The same format, another story.
        assertFalse(same("Marktbericht: DAX schwächelt nach Allzeithoch", "Marktbericht: DAX steigt nach Allzeithoch"))
        assertFalse(same("+++ Nachrichten im Ukrainekrieg +++: Trump bietet Nato-Mitgliedschaft an", "+++ Nachrichten im Ukrainekrieg +++: Russland meldet Einnahme von Kupjansk"))
        // The same words with another number: another day's edition.
        assertFalse(same("News am Morgen - Nachrichten vom 10. November 2026", "News am Morgen - Nachrichten vom 11. November 2026"))
        // Too short to tell, even word for word.
        assertFalse(same("Die Lage am Morgen", "Die Lage am Morgen"))
        assertFalse(same("Drachen", "Drachen"))
    }

    @Test
    fun `twins are of one publisher and of nearly the same time`() {
        val headline = "Republikaner nach den Midterms: Angst und Gehorsam"
        val finder = StoryTwins.Finder()
        assertNull(finder.find(own("taz", headline, "taz.de", "taz Politik")))
        finder.add(own("taz", headline, "taz.de", "taz Politik"), 7)
        // Google News' copy of it, four minutes later.
        assertEquals(7, finder.find(google("g", "Republikaner nach den Midterms – Angst und Gehorsam", "taz.de", "taz.de")))
        // Another publisher with those words is another article (the lists fold equal headlines themselves).
        assertNull(finder.find(google("g2", "Republikaner nach den Midterms – Angst und Gehorsam", "welt.de", "WELT")))
        // The same publisher three days later: a new story under an old headline.
        assertNull(finder.find(own("later", headline, "taz.de", "taz Politik", at = "2026-11-18T10:00:00+01:00")))
        // Without a date the headline decides.
        assertEquals(7, finder.find(own("undated", headline, "taz.de", "taz Politik", at = null)))
    }

    @Test
    fun `the better twin stays and takes the picture only the other has`() {
        val picture = ArticleImage("https://img.n-tv.de/1.jpg")
        val feed = own("feed", "Reanimation an A63: Mario Basler rettet Unfallopfer das Leben", "n-tv.de", "n-tv")
        val news = google("gn", "Reanimation an A63: Mario Basler rettet Unfallopfer das Leben", "n-tv.de", "n-tv.de")
        // The publisher's own item wins, whichever came first.
        assertSame(feed, StoryTwins.merged(feed, news))
        assertSame(feed, StoryTwins.merged(news, feed))
        // It has no picture, its twin has one: the survivor shows the twin's.
        val survivor = StoryTwins.merged(feed, news.copy(image = picture, summary = "Der Teaser des Zwillings."))
        assertEquals("feed", survivor.id)
        assertEquals(picture, survivor.image)
        assertEquals("Der Teaser des Zwillings.", survivor.summary)
        // Its own picture and teaser stay.
        val own = feed.copy(image = ArticleImage("https://img.n-tv.de/own.jpg"), summary = "Eigener Teaser.")
        assertSame(own, StoryTwins.merged(own, news.copy(image = picture, summary = "Anderer Teaser.")))
        // Two feeds of one newsroom: the one with the picture.
        val it = own("heise-it", "Daten-GAU in Berlin: Forensiker über den Rhysida-Leak", "heise.de", "heise IT").copy(image = picture)
        val top = own("heise-top", "Daten-GAU in Berlin: Forensiker über den Rhysida-Leak", "heise.de", "heise online")
        assertEquals("heise-it", StoryTwins.merged(top, it).id)
    }
}
