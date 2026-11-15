package com.cedrickgd.morgenbriefing.data.article

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/**
 * Is this the same picture? Newsrooms serve one photo in many renditions: the feed's
 * `…/16x9-1280/name-100.jpg`, the page's `og:image` `…/16x9-1920/name-100.jpg`, the article's
 * `…_w488_r1.33_fpx50_fpy50.jpg`, a resizer `…/_next/image?url=…&w=1080`. Two URLs show the same
 * picture when they are equal without size parameters, carry the same file name once sizes are
 * taken out, or share an id: a UUID or a long hex hash in the path. Words shared by many pictures
 * (an account number, a shoot code, a date) are not enough.
 */
internal object ImageMatch {

    private val SPLIT = Regex("[_\\-.~,=+%:]+")
    private val UUID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    private val HEX = Regex("^[0-9a-f]{16,}$")

    /** Parts of a file name that describe the rendition, not the picture. */
    private val RENDITION = Regex(
        "^(\\d+x\\d+|\\d+to\\d+|[whqc]\\d+|\\d+[wh]|fp[xy]\\d+|r\\d+|ci\\d+x\\d+|" +
            "jpe?g|png|webp|avif|gif|heic|auto|crop|cropped|resize|resized|scale|scaled|thumb|thumbnail|" +
            "original|large|medium|small|big|xl|xxl|lg|md|sm|retina|hd|hq|lq|full|wide|square|portrait|landscape|" +
            "hero|teaser|standard|default|maxresdefault|hqdefault|mqdefault|sddefault|download|image|img|bild|foto|photo|v|t)$",
    )
    private val SIZE_QUERY = setOf(
        "w", "h", "width", "height", "q", "quality", "fit", "crop", "auto", "format", "fm", "dpr", "resize", "size",
        "s", "im", "impolicy", "imwidth", "ver", "v", "rect", "tp", "anchor", "class", "branch", "strip", "output-quality",
    )
    private val PROXY_PARAMS = listOf("url", "src", "image", "img", "u")

    /** True when [a] and [b] most likely show the same picture. */
    fun same(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        val ua = unwrap(a) ?: return false
        val ub = unwrap(b) ?: return false
        if (base(ua) == base(ub)) return true
        val sa = stem(ua)
        if (sa.length >= MIN_STEM && sa == stem(ub)) return true
        val ia = ids(ua)
        return ia.isNotEmpty() && ids(ub).any { it in ia }
    }

    /** A resizer URL (`/_next/image?url=…`, `…/resize?src=…`) stands for the picture it wraps. */
    private fun unwrap(raw: String): HttpUrl? {
        val url = raw.trim().toHttpUrlOrNull() ?: return null
        for (name in PROXY_PARAMS) {
            val value = url.queryParameter(name) ?: continue
            val inner = value.toHttpUrlOrNull() ?: url.resolve(value) ?: continue
            if (inner.encodedPath.length > 1) return inner
        }
        return url
    }

    /** Host and path without the size parameters of the query. */
    private fun base(url: HttpUrl): String {
        val kept = (0 until url.querySize)
            .map { url.queryParameterName(it) to url.queryParameterValue(it) }
            .filter { (name, _) -> name.lowercase(Locale.ROOT) !in SIZE_QUERY }
            .sortedBy { it.first }
            .joinToString("&") { (n, v) -> "$n=$v" }
        return url.host.removePrefix("www.") + url.encodedPath.lowercase(Locale.ROOT) + if (kept.isEmpty()) "" else "?$kept"
    }

    /**
     * The file name without extension and rendition words: `tastatur-laptop-100`. The ARD and ZDF
     * newsrooms name the rendition after a tilde (`moritz-mueller-148~_v-gseagaleriexl.jpg` in the
     * feed, `moritz-mueller-148~_v-fullhd.jpg` on the page, `keyvisual-100~384x216`): it goes.
     */
    private fun stem(url: HttpUrl): String {
        val name = url.pathSegments.lastOrNull { it.isNotBlank() }?.lowercase(Locale.ROOT) ?: return ""
        val file = name.substringBefore('~').takeIf { it.length >= MIN_STEM } ?: name
        return SPLIT.split(file).filter { it.isNotEmpty() && !RENDITION.matches(it) }.joinToString(" ")
    }

    private const val MIN_STEM = 4

    /** Ids in the path: UUIDs and long hex hashes (one per picture, unlike account or shoot numbers). */
    private fun ids(url: HttpUrl): Set<String> {
        val path = url.encodedPath.lowercase(Locale.ROOT)
        val out = UUID.findAll(path).map { it.value }.toMutableSet()
        url.pathSegments.forEach { segment ->
            SPLIT.split(segment.lowercase(Locale.ROOT)).filter { HEX.matches(it) && it.any(Char::isLetter) && it.any(Char::isDigit) }.forEach(out::add)
        }
        return out
    }
}
