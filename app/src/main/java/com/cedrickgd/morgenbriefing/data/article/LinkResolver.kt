package com.cedrickgd.morgenbriefing.data.article

import java.io.IOException

/**
 * Turns an aggregator link (a `news.google.com/rss/articles/…` URL) into the publisher's own
 * article URL, so the reader can fetch the real page. The news engine provides the Google News
 * implementation (decoding, cache); [Identity] leaves every URL as it is.
 */
fun interface LinkResolver {
    /** The publisher's URL for [url]; [url] itself when it is not an aggregator link or cannot be resolved (now). */
    suspend fun resolve(url: String): String

    /**
     * Like [resolve], but says when resolving failed instead of handing [url] back: the reader
     * must tell "Google is unreachable, try again" apart from "this link holds no article".
     * [url] itself still comes back when it is no link this resolver knows how to resolve.
     *
     * @throws LinkResolveException (or another [IOException]) when the link could not be resolved now.
     */
    suspend fun resolveOrThrow(url: String): String = resolve(url)

    companion object {
        val Identity: LinkResolver = LinkResolver { it }
    }
}

/**
 * An aggregator link could not be resolved right now: no connection, a timeout, the
 * aggregator's error status [httpCode] (429 when it rate-limits), or an answer it did not
 * understand. Trying again later may work.
 */
class LinkResolveException(message: String, val httpCode: Int?, cause: Throwable? = null) : IOException(message, cause)
