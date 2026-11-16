package com.cedrickgd.morgenbriefing

import android.content.Context
import android.util.Xml
import com.cedrickgd.morgenbriefing.data.AppJson
import com.cedrickgd.morgenbriefing.data.NewsRepository
import com.cedrickgd.morgenbriefing.data.SettingsRepository
import com.cedrickgd.morgenbriefing.data.WeatherRepository
import com.cedrickgd.morgenbriefing.data.article.AndroidNetworkMeter
import com.cedrickgd.morgenbriefing.data.article.ArticlePrefetcher
import com.cedrickgd.morgenbriefing.data.article.ArticleRepository
import com.cedrickgd.morgenbriefing.data.article.CoverLookup
import com.cedrickgd.morgenbriefing.data.article.CoverResolver
import com.cedrickgd.morgenbriefing.data.article.CoverStore
import com.cedrickgd.morgenbriefing.data.article.LinkResolver
import com.cedrickgd.morgenbriefing.data.article.ReadabilityStore
import com.cedrickgd.morgenbriefing.data.article.VerdictStore
import com.cedrickgd.morgenbriefing.data.local.NewsCache
import com.cedrickgd.morgenbriefing.data.local.ReadHistoryStore
import com.cedrickgd.morgenbriefing.data.local.WeatherStore
import com.cedrickgd.morgenbriefing.data.news.RssParser
import com.cedrickgd.morgenbriefing.data.remote.GoogleNewsDecoder
import com.cedrickgd.morgenbriefing.data.remote.OpenMeteoClient
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import com.cedrickgd.morgenbriefing.location.LocationFinder
import com.cedrickgd.morgenbriefing.perf.BackgroundWork
import com.cedrickgd.morgenbriefing.perf.Performance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Manual dependency graph; one instance per process, owned by [MorgenbriefingApp]. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val json = AppJson

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val storeDir = File(app.filesDir, "store")

    val settings = SettingsRepository(app)

    /** Device tier (probed off the main thread, battery saver watched) and the effects in use. */
    val performance = Performance(app, settings.settings.map { it.effects }, CoroutineScope(SupervisorJob() + Dispatchers.Default))
    val weatherStore = WeatherStore(storeDir, json)
    val newsCache = NewsCache(storeDir, json)
    val openMeteo = OpenMeteoClient(http, json)
    val weather = WeatherRepository(openMeteo, weatherStore)
    val rss = RssClient(http, RssParser { Xml.newPullParser() })

    /** Google News links → publisher URLs (for the reader); results cached in `store/google-news-links.json`. */
    val googleNewsDecoder = GoogleNewsDecoder(http, File(storeDir, "google-news-links.json"), json)
    val linkResolver: LinkResolver = googleNewsDecoder

    /** Which sites the reader can show; seeded with [com.cedrickgd.morgenbriefing.data.article.KnownWalls], learns on the phone. */
    val readability = ReadabilityStore(File(storeDir, "readability.json"), json)

    val news = NewsRepository(
        rss,
        newsCache,
        blockedDomains = readability::blockedDomains,
        resolvedUrl = googleNewsDecoder::peek,
        // Both caches are read from disk once, here, off the main thread: everything after the
        // warm-up (staleness checks, paging) finds them in memory.
        warmUp = {
            googleNewsDecoder.warm()
            withContext(Dispatchers.IO) { readability.blockedDomains() }
        },
    )

    /** Pages read for checks and preloading are parsed on two low-priority threads with light effects. */
    val articles = ArticleRepository(
        http, storeDir, json, linkResolver = linkResolver, readability = readability,
        backgroundCpu = { if (performance.light) BackgroundWork.parsing else Dispatchers.Default },
    )

    /** Per article: verified complete, teaser, wall or no article – kept across starts (`store/verdicts.json`). */
    val verdicts = VerdictStore(File(storeDir, "verdicts.json"), json, CoroutineScope(SupervisorJob() + Dispatchers.IO))
    val network = AndroidNetworkMeter(app)
    /** Checks 3 articles at a time, 2 with light effects. */
    val prefetcher = ArticlePrefetcher(articles, network, verdicts, light = performance::light)

    /**
     * Cover pictures for cards of articles whose feed carried none (Google News): looked up ahead
     * of the lists on any connection, kept across starts (`store/covers.json`); what the reader
     * and the prefetcher find counts too.
     */
    private val coverLookup = CoverLookup(http, linkResolver)
    val covers = CoverResolver(
        source = { item, language -> coverLookup.find(item, language) },
        network = network,
        store = CoverStore(File(storeDir, "covers.json"), json, CoroutineScope(SupervisorJob() + Dispatchers.IO)),
        light = performance::light,
        resolved = googleNewsDecoder::peek,
        cached = { item -> articles.cachedOrNull(item)?.leadImage },
    ).also { it.follow(prefetcher.images) }
    val location = LocationFinder(app)

    /**
     * The stories the user read (opened, listened to) or hid by hand – "Gelesen & ausgeblendet"
     * (`store/read-history.json`). Google News links count with the publisher's link behind them.
     */
    val history = ReadHistoryStore(
        File(storeDir, "read-history.json"), json, CoroutineScope(SupervisorJob() + Dispatchers.IO),
        resolved = googleNewsDecoder::peek,
    )
}

val Context.container: AppContainer
    get() = (applicationContext as MorgenbriefingApp).container

val Context.listen: com.cedrickgd.morgenbriefing.listen.ListenController
    get() = (applicationContext as MorgenbriefingApp).listen
