package com.cedrickgd.morgenbriefing.data.remote

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import java.util.Collections

/** Google News answers as they came on 05.11.2026 (shortened), for the client and repository tests. */
internal object GoogleNewsFixtures {

    fun item(
        title: String,
        publisher: String,
        site: String,
        id: String,
        date: String = "Thu, 05 Nov 2026 11:00:15 GMT",
    ): String = """
        <item><title>$title - $publisher</title><link>https://news.google.com/rss/articles/$id?oc=5</link>
        <guid isPermaLink="false">$id</guid><pubDate>$date</pubDate>
        <description>&lt;a href="https://news.google.com/rss/articles/$id?oc=5" target="_blank"&gt;$title&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;$publisher&lt;/font&gt;</description>
        <source url="$site">$publisher</source></item>
    """.trimIndent()

    fun feed(vararg items: String): String = HEAD + items.joinToString("\n") + "\n</channel></rss>"

    private val HEAD = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel><generator>NFE/5.0</generator>
        <title>"DAX" - Google News</title><link>https://news.google.com/search?q=DAX&amp;hl=de&amp;gl=DE&amp;ceid=DE:de</link>
        <language>de</language><webMaster>news-webmaster@google.com</webMaster><copyright>2026 Google LLC</copyright>
        <lastBuildDate>Thu, 05 Nov 2026 12:50:01 GMT</lastBuildDate><description>Google News</description>
    """.trimIndent() + "\n"

    /** Top stories carry a list of related links as description. */
    val TOP_STORY = """
        <item><title>Zwischenwahlen in den USA: Alle Ergebnisse im Überblick - RND.de</title>
        <link>https://news.google.com/rss/articles/CBMizgFBVV95cUxNcmM2bDhTZk1EaEpYeHFRVHZOOEdUNGZr?oc=5</link>
        <guid isPermaLink="false">CBMizgFBVV95cUxNcmM2bDhTZk1EaEpYeHFRVHZOOEdUNGZr</guid><pubDate>Thu, 05 Nov 2026 05:41:28 GMT</pubDate>
        <description>&lt;ol&gt;&lt;li&gt;&lt;a href="https://news.google.com/rss/articles/CBMizgFBVV95?oc=5" target="_blank"&gt;Zwischenwahlen in den USA: Alle Ergebnisse im Überblick&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;RND.de&lt;/font&gt;&lt;/li&gt;&lt;li&gt;&lt;a href="https://news.google.com/rss/articles/CBMiZ2h0?oc=5" target="_blank"&gt;Blaue Welle bei den Midterms&lt;/a&gt;&amp;nbsp;&amp;nbsp;&lt;font color="#6f6f6f"&gt;DIE ZEIT&lt;/font&gt;&lt;/li&gt;&lt;/ol&gt;</description>
        <source url="https://www.rnd.de">RND.de</source></item>
    """.trimIndent()

    /**
     * A client whose every request goes to [server] (path and query kept), so code that builds
     * news.google.com URLs can run against a MockWebServer. The original URLs are recorded.
     */
    fun redirectingClient(server: MockWebServer, seen: MutableList<String> = Collections.synchronizedList(mutableListOf())): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    val original = chain.request()
                    seen += original.url.toString()
                    val target = original.url.newBuilder().scheme("http").host(server.hostName).port(server.port).build()
                    chain.proceed(original.newBuilder().url(target).build())
                },
            )
            .build()
}
