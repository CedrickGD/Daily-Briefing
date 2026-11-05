package com.cedrickgd.morgenbriefing.data.article

import okhttp3.HttpUrl
import java.util.Locale

/**
 * The cookies of one article load – like a private browser tab that is closed right after:
 * a site that sets a cookie and redirects to itself (a common "first visit" dance) gets it back,
 * nothing is stored, nothing is shared with another load or site.
 *
 * Deliberately tiny and independent of OkHttp's cookie parsing (that needs the public-suffix
 * database, which is an Android asset and missing in JVM tests): name/value, `Domain`, `Path`,
 * `Max-Age` (≤ 0 deletes). A `Domain` must be the host or a parent of it with a dot inside.
 */
internal class SessionCookies {
    private data class Entry(val name: String, val value: String, val domain: String, val hostOnly: Boolean, val path: String)

    private val entries = ArrayList<Entry>()

    /** Takes the `Set-Cookie` headers of a response for [url]. */
    @Synchronized
    fun save(url: HttpUrl, setCookies: List<String>) {
        val host = url.host.lowercase(Locale.ROOT)
        for (header in setCookies) {
            val parts = header.split(';').map { it.trim() }
            val pair = parts.firstOrNull()?.takeIf { '=' in it } ?: continue
            val name = pair.substringBefore('=').trim()
            if (name.isEmpty()) continue
            val value = pair.substringAfter('=').trim()
            var domain = host
            var hostOnly = true
            var path = defaultPath(url)
            var delete = false
            for (attribute in parts.drop(1)) {
                val key = attribute.substringBefore('=').trim().lowercase(Locale.ROOT)
                val arg = attribute.substringAfter('=', "").trim()
                when (key) {
                    "domain" -> {
                        val d = arg.removePrefix(".").lowercase(Locale.ROOT)
                        if (d.isEmpty() || '.' !in d || !(host == d || host.endsWith(".$d"))) {
                            delete = true // another site's cookie, or a bare top-level domain: refused
                        } else {
                            domain = d
                            hostOnly = false
                        }
                    }
                    "path" -> if (arg.startsWith("/")) path = arg
                    "max-age" -> if ((arg.toLongOrNull() ?: 1) <= 0) delete = true
                }
            }
            entries.removeAll { it.name == name && it.domain == domain && it.path == path }
            if (!delete) entries += Entry(name, value, domain, hostOnly, path)
        }
    }

    /** The `Cookie` header for a request to [url], null when there is nothing to send. */
    @Synchronized
    fun header(url: HttpUrl): String? {
        val host = url.host.lowercase(Locale.ROOT)
        val path = url.encodedPath
        val matching = entries.filter { e ->
            val domainOk = if (e.hostOnly) host == e.domain else host == e.domain || host.endsWith(".${e.domain}")
            domainOk && (path == e.path || path.startsWith(e.path.trimEnd('/') + "/") || e.path == "/")
        }
        return matching.takeIf { it.isNotEmpty() }?.joinToString("; ") { "${it.name}=${it.value}" }
    }

    private fun defaultPath(url: HttpUrl): String {
        val path = url.encodedPath
        val slash = path.lastIndexOf('/')
        return if (slash <= 0) "/" else path.substring(0, slash)
    }
}
