package com.cedrickgd.morgenbriefing.ui.news

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.cedrickgd.morgenbriefing.data.remote.RssClient
import com.cedrickgd.morgenbriefing.perf.DeviceTier
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

private const val DISK_CACHE_BYTES = 120L * 1024 * 1024
private const val DISK_CACHE_DIR = "article_images"

/**
 * Share of the app's memory budget (`ActivityManager.memoryClass`) for decoded pictures: 20 %,
 * 15 % on a budget phone ([DeviceTier.LOW]) – it has the smaller budget and less memory overall.
 */
internal fun memoryCachePercent(tier: DeviceTier): Double = if (tier == DeviceTier.LOW) 0.15 else 0.20

/**
 * The app-wide Coil [ImageLoader] for article pictures. It shares the app's OkHttp connection
 * pool, sends the app's descriptive User-Agent and refuses every request that is not https —
 * also redirect hops — so no picture is ever fetched in the clear. Memory cache
 * [memoryCachePercent] of the app's memory budget, disk cache 120 MB, crossfade on.
 *
 * Pictures are decoded at the size they are shown, never at full resolution: no request sets a
 * size, so `AsyncImage` sizes each one by its layout constraints (a feed card: its width and
 * height; the reader: its width, the height follows) and Coil subsamples a 4000 px photo down to
 * the card while decoding (at most 4096 px a side anyway). Decoding runs on Coil's background
 * dispatchers; from Android 8 the bitmaps live in graphics memory (hardware bitmaps).
 */
internal fun newsImageLoader(context: Context, http: OkHttpClient, tier: DeviceTier = DeviceTier.FULL): ImageLoader {
    val app = context.applicationContext
    val client = http.newBuilder()
        .addInterceptor(UserAgentInterceptor)
        .addNetworkInterceptor(HttpsOnlyInterceptor)
        .build()
    return ImageLoader.Builder(app)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .memoryCache { MemoryCache.Builder().maxSizePercent(app, memoryCachePercent(tier)).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(app.cacheDir.resolve(DISK_CACHE_DIR))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()
}

private object UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", RssClient.FEED_USER_AGENT).build())
}

/** Network interceptor: sees every hop, so a redirect to plain http fails instead of loading. */
private object HttpsOnlyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        if (!isAllowedImageScheme(url.scheme)) {
            throw IOException("Pictures are loaded over https only, not from $url")
        }
        return chain.proceed(chain.request())
    }
}
