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
import com.cedrickgd.morgenbriefing.data.article.LinkResolver
import com.cedrickgd.morgenbriefing.data.article.ReadabilityStore
import com.cedrickgd.morgenbriefing.data.local.NewsCache
import com.cedrickgd.morgenbriefing.data.local.WeatherStore
import com.cedrickgd.morgenbriefing.data.news.RssParser
import com.cedrickgd.morgenbriefing.data.remote.GoogleNewsDecoder
import com.cedrickgd.morgenbriefing.data.remote.OpenMeteoClient
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import com.cedrickgd.morgenbriefing.location.LocationFinder
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
        warmUp = googleNewsDecoder::warm,
    )
    val articles = ArticleRepository(http, storeDir, json, linkResolver = linkResolver, readability = readability)
    val prefetcher = ArticlePrefetcher(articles, AndroidNetworkMeter(app))
    val location = LocationFinder(app)
}

val Context.container: AppContainer
    get() = (applicationContext as MorgenbriefingApp).container

val Context.listen: com.cedrickgd.morgenbriefing.listen.ListenController
    get() = (applicationContext as MorgenbriefingApp).listen
