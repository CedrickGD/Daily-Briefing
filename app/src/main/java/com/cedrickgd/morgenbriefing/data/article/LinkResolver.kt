package com.cedrickgd.morgenbriefing.data.article

/**
 * Turns an aggregator link (a `news.google.com/rss/articles/…` URL) into the publisher's own
 * article URL, so the reader can fetch the real page. The news engine provides the Google News
 * implementation (decoding, cache); [Identity] leaves every URL as it is.
 */
fun interface LinkResolver {
    suspend fun resolve(url: String): String

    companion object {
        val Identity: LinkResolver = LinkResolver { it }
    }
}
