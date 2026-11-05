package com.cedrickgd.morgenbriefing.data.article

/** Turns an aggregator link (Google News) into the publisher's article URL; returns [url] unchanged when it cannot. */
fun interface LinkResolver {
    suspend fun resolve(url: String): String

    companion object {
        /** Resolves nothing: every URL stays as it is. */
        val Identity = LinkResolver { it }
    }
}
