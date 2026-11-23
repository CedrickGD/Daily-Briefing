package com.cedrickgd.morgenbriefing.ui.news

import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.model.AppSettings
import com.cedrickgd.morgenbriefing.data.model.ArticleImage
import com.cedrickgd.morgenbriefing.data.model.ArticleStatus
import com.cedrickgd.morgenbriefing.data.model.NewsAge
import com.cedrickgd.morgenbriefing.data.model.NewsEdition
import com.cedrickgd.morgenbriefing.data.model.NewsItem
import com.cedrickgd.morgenbriefing.data.model.SourceStatus
import com.cedrickgd.morgenbriefing.data.model.TopicNews
import com.cedrickgd.morgenbriefing.i18n.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/** The endless timeline behind "Neueste" and below the "Für dich" cards. */
class FeedTimelineTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 11, 5, 9, 0, 0, 0, ZoneId.of("Europe/Berlin"))
    private val instant: Instant = now.toInstant()

    private fun hoursAgo(hours: Long): Instant = instant.minus(Duration.ofHours(hours))

    /** [kind]: "rss" a publisher's feed, "gn" the topic's Google News first page, "page" an older page of it. */
    private fun item(id: String, topic: String, hoursAgo: Long?, kind: String = "rss") = NewsItem(
        id = id,
        headline = "H$id",
        url = "https://example.org/$id",
        source = "S",
        sourceId = when (kind) {
            "gn" -> "gn:$topic@DE:de"
            "page" -> "gn:$topic@DE:de${NewsItem.PAGE_SUFFIX}"
            else -> "s"
        },
        topicId = topic,
        publishedAt = hoursAgo?.let { now.minusHours(it).toOffsetDateTime().toString() },
        aggregator = if (kind == "rss") null else "google",
    )

    private fun news(topic: String, vararg items: NewsItem) =
        topic to TopicNews(topic, now.toOffsetDateTime().toString(), items.toList(), listOf(SourceStatus("s", "S", ok = true)))

    private fun prefs(vararg followed: String, age: NewsAge = NewsAge.DAY, size: Int = 2, readableOnly: Boolean = false) = FeedPrefs(
        AppSettings(
            uiLanguage = AppLanguage.DE, edition = NewsEdition.GERMANY, followed = followed.toList(),
            sectionSize = size, newsAge = age, readableOnly = readableOnly,
        ),
    )

    /** "welt" reaches back two days, "sport" (paged before) more than a week. */
    private val repo = mapOf(
        news(
            "welt",
            item("w1", "welt", 1), item("w2", "welt", 5), item("w3", "welt", 30, "gn"), item("w4", "welt", 50, "gn"),
        ),
        news(
            "sport",
            item("s1", "sport", 2), item("s2", "sport", 40, "gn"), item("s3", "sport", 100, "gn"), item("s4", "sport", 200, "page"),
        ),
    )

    @Test
    fun `a topic's list is complete back to its oldest page, else to the bulk of its first page`() {
        val welt = repo.getValue("welt").items
        assertEquals(hoursAgo(50), pagedDepth(welt, instant))
        assertEquals(hoursAgo(200), pagedDepth(repo.getValue("sport").items, instant))
        // A stray old hit on the first page is not how far the list reaches.
        val stray = welt + item("old", "welt", 24L * 200, "gn")
        assertEquals(hoursAgo(50), pagedDepth(stray, instant))
        // Only publisher feeds so far: nothing older is known to be complete.
        assertEquals(instant, pagedDepth(listOf(item("r1", "welt", 3), item("r2", "welt", 90)), instant))
        assertNull(pagedDepth(emptyList(), instant))
        assertNull(pagedDepth(listOf(item("u", "welt", null)), instant))
    }

    @Test
    fun `the age setting is no dead end - the timeline goes on with everything older`() {
        val feed = buildNewsFeed(repo, prefs("welt", "sport"), now)
        // "Neueste" as the age setting shapes it (a quiet topic keeps its three newest) …
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "s3"), feed.latest.map { it.item.id })
        // … and the endless list behind the tab: everything, newest first.
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4", "s3", "s4"), feed.timeline.map { it.item.id })
        assertEquals(8, feed.timelineCandidates)
        // Nothing can page: nothing to ask for.
        assertTrue(feed.olderNext.isEmpty())
    }

    @Test
    fun `stories older than the shallowest topic's reach wait, so older pages only append`() {
        val prefs = prefs("welt", "sport")
        val feed = buildNewsFeed(repo, prefs, now, open = setOf("welt", "sport"))
        // "welt" is read back 50 hours: what "sport" holds beyond that would end up above its next page.
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4"), feed.timeline.map { it.item.id })
        assertEquals(listOf("welt"), feed.olderNext)

        // "welt" paged three more days: its stories land below everything shown, and the list moves on.
        val paged = repo + news(
            "welt",
            *repo.getValue("welt").items.toTypedArray(), item("w5", "welt", 70, "page"), item("w6", "welt", 120, "page"),
        )
        val next = buildNewsFeed(paged, prefs, now, open = setOf("welt", "sport"))
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4", "w5", "s3", "w6"), next.timeline.map { it.item.id })
        assertEquals(feed.timeline.map { it.item.id }, next.timeline.map { it.item.id }.take(feed.timeline.size))
        // Now "welt" reaches back 120 hours, "sport" 200: "welt" is still the one to page.
        assertEquals(listOf("welt"), next.olderNext)

        // "welt" at its end: only "sport" holds the list back, and everything it has shows.
        val ended = buildNewsFeed(paged, prefs, now, open = setOf("sport"))
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4", "w5", "s3", "w6", "s4"), ended.timeline.map { it.item.id })
        assertEquals(listOf("sport"), ended.olderNext)
    }

    @Test
    fun `the last day always shows, also when a topic has not been read back at all`() {
        // "welt" has only its publisher feeds: complete up to now, it holds everything older back.
        val shallow = mapOf(
            news("welt", item("w1", "welt", 1), item("w2", "welt", 30)),
            news("sport", item("s1", "sport", 2), item("s2", "sport", 40, "gn"), item("u", "sport", null)),
        )
        val feed = buildNewsFeed(shallow, prefs("welt", "sport"), now, open = setOf("welt", "sport"))
        // Undated stories count as a day old: they show, after the dated ones of the day.
        assertEquals(listOf("w1", "s1", "u"), feed.timeline.map { it.item.id })
        assertEquals("welt", feed.olderNext.first())
    }

    @Test
    fun `topics that end within a day of the shallowest are paged with it, a few at a time`() {
        val depths = linkedMapOf(
            "a" to hoursAgo(50), "b" to hoursAgo(40), "c" to hoursAgo(60), "d" to hoursAgo(300),
            "e" to hoursAgo(41), "f" to hoursAgo(45), "g" to hoursAgo(64),
        )
        assertEquals(listOf("b", "e", "f", "a"), nextToPage(depths))
        assertEquals(listOf("b", "e"), nextToPage(depths, max = 2))
        assertEquals(listOf("d"), nextToPage(mapOf("d" to hoursAgo(300))))
        assertTrue(nextToPage(emptyMap()).isEmpty())
    }

    @Test
    fun `Fuer dich goes on below its cards with everything they do not show`() {
        val feed = buildNewsFeed(repo, prefs("welt", "sport", size = 2), now)
        assertEquals(listOf("w1", "w2"), feed.sections[0].top.map { it.id })
        assertEquals(listOf("s1", "s2"), feed.sections[1].top.map { it.id })
        assertEquals(listOf("w3", "w4", "s3", "s4"), feed.more.map { it.item.id })
        assertEquals(listOf("welt", "welt", "sport", "sport"), feed.more.map { it.topic.id })
    }

    @Test
    fun `a story of several topics shows once, under its most specific one`() {
        val shared = item("x", "politik/inland", 30)
        val both = mapOf(news("politik", shared, item("p1", "politik", 1)), news("politik/inland", shared, item("i1", "politik/inland", 2)))
        val feed = buildNewsFeed(both, prefs("politik", "politik/inland"), now)
        assertEquals(listOf("p1", "i1", "x"), feed.timeline.map { it.item.id })
        assertEquals("politik/inland", feed.timeline.last().topic.id)
    }

    @Test
    fun `with nur vollstaendige Artikel the timeline shows verified stories and names the next to check`() {
        val verdicts = mapOf("w1" to ArticleStatus.READABLE, "s1" to ArticleStatus.READABLE, "w2" to ArticleStatus.PAYWALL, "w3" to ArticleStatus.READABLE)
        val feed = buildNewsFeed(repo, prefs("welt", "sport", readableOnly = true), now, verification = Verification(true, verdicts))
        // w2 is a teaser (never shows), s2 is not judged yet: the list stops there.
        assertEquals(listOf("w1", "s1", "w3"), feed.timeline.map { it.item.id })
        assertEquals(listOf("s2", "w4", "s3", "s4"), feed.timelineWaiting.map { it.id })
        // What a list showed stays in it until the next build.
        val kept = keptAfter(emptyMap(), feed)
        assertEquals(setOf("w1", "s1", "w3"), kept[TIMELINE_LIST])
    }

    // --- Older pages only append (emulator run 15.11.2026: cards moved by 250…1460 px without a touch) ---

    /** A topic as "Schlagzeilen" and "Politik" were: 25 stories of the last 25 hours, and what the publishers' feeds drag along. */
    private val recent = (1..25).map { item("n$it", "welt", it.toLong(), "gn") }
    private val tail = listOf(item("t9", "welt", 24L * 9), item("t40", "welt", 24L * 40))

    private fun topic(vararg more: NewsItem, fetched: ZonedDateTime = now) = mapOf(
        "welt" to TopicNews(
            "welt", fetched.toOffsetDateTime().toString(),
            NewsRepository.newestFirst(recent + tail + more),
            listOf(SourceStatus("s", "S", ok = true)),
        ),
    )

    private fun ids(feed: NewsFeed) = feed.sections.single().all.map { it.id }

    @Test
    fun `a topic that can page lists its stories as far back as it has been read`() {
        val prefs = prefs("welt")
        val all = (recent + tail).map { it.id }
        // Nothing older can come (own feeds only, or the end of its history): everything shows.
        assertEquals(all, ids(buildNewsFeed(topic(), prefs, now)))
        // It can page: what lies beyond the days read so far waits – its older pages are the days in between.
        val open = buildNewsFeed(topic(), prefs, now, open = setOf("welt"))
        assertEquals(recent.map { it.id }, ids(open))
        assertEquals(25, open.sections.single().candidates)
        // The load of older articles failed (offline): the list shows all the phone holds.
        assertEquals(all, ids(buildNewsFeed(topic(), prefs, now, open = setOf("welt"), released = setOf("welt"))))
        // The newest stories always show, however little has been read back.
        val quiet = (30..60).map { item("q$it", "welt", it.toLong()) }
        assertEquals(quiet.take(ALWAYS_LISTED).map { it.id }, readSoFar(quiet, depth = instant, floor = hoursAgo(24)).map { it.id })
        // Undated stories never wait: their place is the feed's.
        val undated = quiet + item("u", "welt", null)
        assertEquals("u", readSoFar(undated, depth = instant, floor = hoursAgo(24)).last().id)
    }

    @Test
    fun `a topic's older pages are appended, never sorted in above the stories it shows`() {
        val prefs = prefs("welt")
        val first = buildFeed(topic(), prefs, now, open = setOf("welt"))
        val shown = recent.map { it.id }
        assertEquals(shown, ids(first.feed))

        // The next page: its window starts on the day the list ends, so p20 is as new as the sixth story from the end.
        val page = arrayOf(item("p20", "welt", 20, "page"), item("p60", "welt", 60, "page"), item("p120", "welt", 120, "page"))
        val second = buildFeed(topic(*page), prefs, now, open = setOf("welt"), order = first.order)
        assertEquals(shown + listOf("p20", "p60", "p120"), ids(second.feed))
        // Sorted in by date it would stand above n21 … n25 and push them down.
        assertEquals("p20", ids(buildNewsFeed(topic(*page), prefs, now, open = setOf("welt")))[20])

        // The page after that reaches past the nine-day-old story of a publisher's feed: it shows now, below everything.
        val further = page + item("p230", "welt", 230, "page")
        val third = buildFeed(topic(*further), prefs, now, open = setOf("welt"), order = second.order)
        assertEquals(shown + listOf("p20", "p60", "p120", "t9", "p230"), ids(third.feed))

        // The end of the topic's history: what still waited closes the list.
        val ended = buildFeed(topic(*further), prefs, now, order = third.order)
        assertEquals(shown + listOf("p20", "p60", "p120", "t9", "p230", "t40"), ids(ended.feed))

        // Asking again after a failed load holds nothing back that was on screen.
        val released = buildFeed(topic(), prefs, now, open = setOf("welt"), released = setOf("welt"))
        val again = buildFeed(topic(item("p60", "welt", 60, "page")), prefs, now, open = setOf("welt"), order = released.order)
        assertEquals(shown + listOf("t9", "t40", "p60"), ids(again.feed))

        // A refresh is a new list: sorted afresh, newest first.
        val refreshed = buildFeed(topic(*further, fetched = now.plusMinutes(20)), prefs, now, open = setOf("welt"), order = third.order)
        assertEquals("p20", ids(refreshed.feed)[20])
        assertEquals(listOf("n25", "p60", "p120", "t9", "p230"), ids(refreshed.feed).takeLast(5))
    }

    @Test
    fun `with nur vollstaendige Artikel a page's articles show below the list as they are verified`() {
        val prefs = prefs("welt", readableOnly = true)
        val verified = recent.associate { it.id to ArticleStatus.READABLE }
        val first = buildFeed(topic(), prefs, now, verification = Verification(true, verified), open = setOf("welt"))
        assertEquals(recent.map { it.id }, ids(first.feed))
        assertTrue(first.feed.sections.single().waiting.isEmpty())

        // The page arrives unverified: the list waits (it shows "Weitere Artikel werden geprüft …") and nothing moves.
        val page = arrayOf(item("p20", "welt", 20, "page"), item("p60", "welt", 60, "page"))
        val kept = keptAfter(emptyMap(), first.feed)
        val pending = buildFeed(topic(*page), prefs, now, verification = Verification(true, verified, kept), open = setOf("welt"), order = first.order)
        assertEquals(recent.map { it.id }, ids(pending.feed))
        assertEquals(listOf("p20", "p60"), pending.feed.sections.single().waiting.map { it.id })

        // Verified, p20 shows at the end – not between n19 and n20, where its date would put it.
        val done = Verification(true, verified + ("p20" to ArticleStatus.READABLE) + ("p60" to ArticleStatus.PAYWALL), kept)
        val after = buildFeed(topic(*page), prefs, now, verification = done, open = setOf("welt"), order = pending.order)
        assertEquals(recent.map { it.id } + "p20", ids(after.feed))
        assertTrue(after.feed.sections.single().waiting.isEmpty())
    }

    @Test
    fun `the timeline goes on from what it showed - a page that overlaps it is appended`() {
        val prefs = prefs("welt", "sport")
        val first = buildFeed(repo, prefs, now, open = setOf("welt", "sport"))
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4"), first.feed.timeline.map { it.item.id })
        // "welt" pages: w5 is from the hours the timeline already shows (45 hours ago, before w4), w6 is older.
        val paged = repo + news(
            "welt",
            *repo.getValue("welt").items.toTypedArray(), item("w5", "welt", 45, "page"), item("w6", "welt", 120, "page"),
        )
        val next = buildFeed(paged, prefs, now, open = setOf("welt", "sport"), order = first.order)
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w4", "w5", "s3", "w6"), next.feed.timeline.map { it.item.id })
        // Without the order it had, w5 would stand above w4.
        val sorted = buildNewsFeed(paged, prefs, now, open = setOf("welt", "sport")).timeline.map { it.item.id }
        assertEquals(listOf("w1", "s1", "w2", "w3", "s2", "w5", "w4", "s3", "w6"), sorted)
        // The list below the "Für dich" cards is that timeline: stable too.
        assertEquals(first.feed.more.map { it.item.id }, next.feed.more.map { it.item.id }.take(first.feed.more.size))
    }

    @Test
    fun `what a list showed keeps its order, new stories follow`() {
        val id: (String) -> String = { it }
        assertEquals(listOf("a", "b", "c"), appendOnly(listOf("a", "b", "c"), emptyList(), id))
        assertEquals(listOf("c", "a", "x", "b"), appendOnly(listOf("x", "a", "b", "c"), listOf("c", "a"), id))
        // What is gone (a source switched off) leaves no gap.
        assertEquals(listOf("a", "c", "d"), appendOnly(listOf("a", "c", "d"), listOf("a", "b", "c"), id))
        // Nothing of the old list is left: a new list.
        assertEquals(listOf("x", "y"), appendOnly(listOf("x", "y"), listOf("a", "b"), id))
        // The order is only good for the fetch it was listed from.
        val order = FeedOrder.Builder().apply { put("welt", "07:00", listOf("a", "b")) }.build()
        assertEquals(listOf("a", "b"), order.of("welt", "07:00"))
        assertTrue(order.of("welt", "07:15").isEmpty())
        assertTrue(order.of("sport", "07:00").isEmpty())
    }

    // --- The same article through two feeds of one publisher shows once ---

    private fun story(
        id: String, topic: String, minutesAgo: Long, headline: String, site: String, source: String,
        google: Boolean = false, picture: Boolean = false,
    ) = NewsItem(
        id = id,
        headline = headline,
        url = if (google) "https://news.google.com/rss/articles/CBMi$id" else "https://www.$site/$id",
        source = source,
        sourceId = if (google) "gn:$topic@DE:de" else "feed-$source",
        topicId = topic,
        publishedAt = now.minusMinutes(minutesAgo).toOffsetDateTime().toString(),
        image = if (picture) ArticleImage("https://img.$site/$id.jpg") else null,
        publisherDomain = site,
        aggregator = if (google) "google" else null,
    )

    private val tazFeed = story("taz-feed", "politik", 180, "Republikaner nach den Midterms: Angst und Gehorsam", "taz.de", "taz Politik", picture = true)
    private val tazGoogle = story("taz-gn", "top", 176, "Republikaner nach den Midterms: Angst und Gehorsam", "taz.de", "taz.de", google = true)
    private val ntvFeed = story("ntv-feed", "top", 300, "Rekruten aus aller Welt: Russland lockt Ausländer mit Fake-Jobs in den Krieg", "n-tv.de", "n-tv", picture = true)
    private val ntvGoogle = story("ntv-gn", "politik", 290, "Russland lockt Ausländer mit Fake-Jobs in den Krieg", "n-tv.de", "n-tv.de", google = true)
    private val heiseTop = story("heise-top", "top", 400, "Daten-GAU in Berlin: Forensiker über den Rhysida-Leak", "heise.de", "heise online")
    private val heiseIt = story("heise-it", "politik", 400, "Daten-GAU in Berlin: Forensiker über den Rhysida-Leak", "heise.de", "heise IT", picture = true)
    private val other = story("other", "top", 60, "Estland schließt Grenzübergänge zu Russland", "tagesschau.de", "tagesschau")

    @Test
    fun `one publisher's article from two feeds shows once in the timeline, with the picture one of them has`() {
        val twins = mapOf(news("top", other, tazGoogle, ntvFeed, heiseTop), news("politik", tazFeed, ntvGoogle, heiseIt))
        val feed = buildNewsFeed(twins, prefs("top", "politik", size = 1), now)
        // taz.de / taz Politik, n-tv / n-tv.de (Google News dropped the kicker), heise online / heise IT: one card each.
        assertEquals(listOf("other", "taz-feed", "ntv-feed", "heise-it"), feed.timeline.map { it.item.id })
        // The publisher's own article stands for the pair, under its own topic, with its picture.
        assertEquals(listOf("top", "politik", "top", "politik"), feed.timeline.map { it.topic.id })
        assertTrue(feed.timeline.drop(1).all { it.item.image != null })
        // The topics' own lists are untouched: each holds its article.
        assertEquals(listOf("other", "taz-gn", "ntv-feed", "heise-top"), feed.sections[0].all.map { it.id })

        // The "top" card opens with another story; "politik" shows the taz article – its twin is not repeated below the cards.
        assertEquals(listOf("other"), feed.sections[0].top.map { it.id })
        assertEquals(listOf("taz-feed"), feed.sections[1].top.map { it.id })
        assertEquals(listOf("ntv-feed", "heise-it"), feed.more.map { it.item.id })
        // The other way round: the card shows Google News' copy, the list below does not show the publisher's.
        val swapped = mapOf(news("top", tazGoogle, ntvFeed), news("politik", story("p", "politik", 5, "Koalition findet Kompromisse für den Etat 2027", "spiegel.de", "SPIEGEL"), tazFeed))
        val below = buildNewsFeed(swapped, prefs("top", "politik", size = 1), now)
        assertEquals(listOf("taz-gn"), below.sections[0].top.map { it.id })
        assertEquals(listOf("ntv-feed"), below.more.map { it.item.id })
    }

    @Test
    fun `the survivor of two twins takes the picture it lacks, and a card on screen stays the one`() {
        val topic = prefs("top").topics.single()
        fun articles(vararg items: NewsItem) = items.map { FeedArticle(it, topic) }
        // The better article wins and stands where the first of the two stood.
        val folded = storiesOnce(articles(tazGoogle, other, tazFeed))
        assertEquals(listOf("taz-feed", "other"), folded.articles.map { it.item.id })
        assertEquals(mapOf("taz-gn" to "taz-feed"), folded.twins)
        // The winner has no picture, its twin has one: the card shows the twin's.
        val bare = heiseTop.copy(summary = "Ein Teaser, den nur dieser Feed mitbringt.")
        val pictured = heiseIt.copy(aggregator = "google")
        val survivor = storiesOnce(articles(bare, pictured)).articles.single().item
        assertEquals("heise-top", survivor.id)
        assertEquals(pictured.image, survivor.image)
        // Google News' copy was on screen before the publisher's own arrived with a page: it stays (and gets the picture).
        val stays = storiesOnce(articles(tazGoogle, tazFeed), shown = setOf("taz-gn"))
        assertEquals(listOf("taz-gn"), stays.articles.map { it.item.id })
        assertEquals(tazFeed.image, stays.articles.single().item.image)
        assertEquals(mapOf("taz-feed" to "taz-gn"), stays.twins)
        // Different stories of one publisher stay two cards.
        val reports = articles(
            story("m1", "top", 30, "Marktbericht: DAX schwächelt nach Allzeithoch", "tagesschau.de", "tagesschau"),
            story("m2", "top", 20, "Marktbericht: DAX steigt nach Allzeithoch", "tagesschau.de", "tagesschau"),
        )
        assertEquals(2, storiesOnce(reports).articles.size)
        // The same agency headline from two publishers is one story (as within a topic).
        val agency = articles(
            story("a1", "top", 30, "Koalition findet Kompromisse für Etat – Stromsteuer ungelöst", "handelsblatt.com", "Handelsblatt"),
            story("a2", "top", 25, "Koalition findet Kompromisse für Etat – Stromsteuer ungelöst", "radiobochum.de", "Radio Bochum"),
        )
        assertEquals(listOf("a1"), storiesOnce(agency).articles.map { it.item.id })
    }

    @Test
    fun `a refresh another screen made keeps the order, the screen's own sorts afresh`() {
        val prefs = prefs("welt")
        val own = now.toOffsetDateTime().toString()
        val first = buildFeed(topic(), prefs, now, adopted = mapOf("welt" to own))
        val shown = ids(first.feed)
        val top = first.feed.sections.single().top.map { it.id }

        // The search (or "Entdecken", the morning notification) fetched the topic again: x0 is the newest story.
        val later = now.plusMinutes(20)
        val foreign = topic(item("x0", "welt", 0), fetched = later)
        val kept = buildFeed(foreign, prefs, now, order = first.order, adopted = mapOf("welt" to own))
        // Nothing moves: the cards on screen stay, the new story follows them.
        assertEquals(shown + "x0", ids(kept.feed))
        assertEquals(top, kept.feed.sections.single().top.map { it.id })
        assertEquals("x0", kept.feed.timeline.last().item.id)
        // And it stays so for the builds after it (a verdict, the reading history).
        val again = buildFeed(foreign, prefs, now, order = kept.order, adopted = mapOf("welt" to own))
        assertEquals(shown + "x0", ids(again.feed))

        // The screen's own refresh takes the fetch: sorted afresh, the newest story on top.
        val adopted = mapOf("welt" to later.toOffsetDateTime().toString())
        val refreshed = buildFeed(foreign, prefs, now, order = again.order, adopted = adopted)
        assertEquals("x0", ids(refreshed.feed).first())
        assertEquals("x0", refreshed.feed.timeline.first().item.id)

        // Paging still appends while a foreign fetch is held back.
        val paged = buildFeed(topic(item("x0", "welt", 0), item("p300", "welt", 300, "page"), fetched = later), prefs, now, order = kept.order, adopted = mapOf("welt" to own))
        assertEquals(shown + listOf("x0", "p300"), ids(paged.feed))
    }

    @Test
    fun `a list takes a new fetch only when it may`() {
        val order = FeedOrder.Builder().apply { put("welt", "07:00", listOf("a", "b")) }.build()
        // Never built: whatever the repository holds.
        assertEquals("07:30", order.stampFor("sport", "07:30", adopt = false))
        // Same fetch, or one the screen made itself.
        assertEquals("07:00", order.stampFor("welt", "07:00", adopt = false))
        assertEquals("07:30", order.stampFor("welt", "07:30", adopt = true))
        // Another screen's fetch: the list goes on from the one it was listed from.
        assertEquals("07:00", order.stampFor("welt", "07:30", adopt = false))
        assertEquals(listOf("a", "b"), order.of("welt", order.stampFor("welt", "07:30", adopt = false)))
    }
}
