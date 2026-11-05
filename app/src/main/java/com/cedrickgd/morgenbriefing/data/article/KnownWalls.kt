package com.cedrickgd.morgenbriefing.data.article

/**
 * Sites whose articles the in-app reader cannot show (hard paywall, pay-or-consent wall, bot
 * block, video-only). Seed list shared by the news engine (it asks Google News to leave these
 * out: `-site:nytimes.com`) and the reader's readability bookkeeping (which also learns new
 * ones on the phone). Hosts without `www.`; a subdomain matches its parent.
 */
object KnownWalls {
    val DOMAINS: Set<String> = setOf(
        // video / social, never an article
        "youtube.com", "tiktok.com", "instagram.com", "facebook.com", "x.com", "twitter.com",
        // German: bot block, consent or paywall (checked with curl, 30.10.2026; welt.de and wiwo.de
        // read fine on 05.11.2026 – their plus articles are caught by the paywall detection)
        "zeit.de", "kicker.de", "faz.net", "golem.de", "bild.de", "nzz.ch",
        // bot checks seen by the reader's live evaluation (05.11.2026): DataDome on lvz.de, AWS WAF on espn.com
        "lvz.de", "espn.com",
        // English hard paywalls / bot walls
        "nytimes.com", "wsj.com", "washingtonpost.com", "ft.com", "bloomberg.com", "economist.com",
        "barrons.com", "thetimes.co.uk", "telegraph.co.uk", "theathletic.com", "newyorker.com",
        "theatlantic.com", "businessinsider.com", "reuters.com", "seekingalpha.com",
    )

    /** True when [host] is one of [DOMAINS] or a subdomain of one. */
    fun matches(host: String, domains: Set<String> = DOMAINS): Boolean {
        val h = host.lowercase().removePrefix("www.")
        return domains.any { h == it || h.endsWith(".$it") }
    }
}
