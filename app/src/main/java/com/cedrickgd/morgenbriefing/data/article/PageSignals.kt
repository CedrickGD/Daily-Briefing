package com.cedrickgd.morgenbriefing.data.article

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * What a page says about itself besides its text: the publisher's own structured data
 * (schema.org JSON-LD – "is this free?", the full `articleBody` some newsrooms publish openly),
 * paywall wording, and the fingerprints of bot checks that keep automated readers out.
 *
 * The reader respects all of it: a page marked `isAccessibleForFree: false` is a paywall, even
 * when its JSON-LD carries the full text for search engines.
 */
internal object PageSignals {

    /** The article object of the page's JSON-LD. */
    data class StructuredArticle(
        val headline: String = "",
        val body: String = "",
        val description: String = "",
        /** `isAccessibleForFree` as the publisher states it (also on `hasPart`); null = not stated. */
        val free: Boolean? = null,
    )

    private val json = Json { isLenient = true; ignoreUnknownKeys = true }

    /** The page's JSON-LD article (NewsArticle, Article, BlogPosting, Report …), or null. */
    fun structuredArticle(document: Document): StructuredArticle? {
        val objects = ArrayList<JsonObject>()
        document.select("script[type=application/ld+json]").forEach { script ->
            val element = try {
                json.parseToJsonElement(script.data().trim())
            } catch (_: Exception) {
                return@forEach
            }
            collect(element, objects)
        }
        if (objects.isEmpty()) return null
        var free: Boolean? = null
        objects.forEach { obj ->
            when (obj.freeFlag()) {
                false -> free = false
                true -> if (free == null) free = true
                null -> Unit
            }
        }
        val article = objects.firstOrNull { it.types().any(::isArticleType) }
        if (article == null && free == null) return null
        return StructuredArticle(
            headline = article?.string("headline").orEmpty(),
            body = article?.string("articleBody").orEmpty(),
            description = article?.string("description").orEmpty(),
            free = free,
        )
    }

    private fun collect(element: JsonElement, into: MutableList<JsonObject>, depth: Int = 0) {
        if (depth > 6) return
        when (element) {
            is JsonArray -> element.forEach { collect(it, into, depth + 1) }
            is JsonObject -> {
                into += element
                element["@graph"]?.let { collect(it, into, depth + 1) }
                element["hasPart"]?.let { collect(it, into, depth + 1) }
                element["mainEntity"]?.let { collect(it, into, depth + 1) }
            }
            else -> Unit
        }
    }

    private fun JsonObject.types(): List<String> = when (val t = this["@type"]) {
        is JsonPrimitive -> listOfNotNull(t.contentOrNull)
        is JsonArray -> t.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        else -> emptyList()
    }

    private fun isArticleType(type: String): Boolean =
        type.endsWith("Article") || type == "BlogPosting" || type == "Report" || type == "LiveBlogPosting"

    private fun JsonObject.freeFlag(): Boolean? {
        val value = this["isAccessibleForFree"] as? JsonPrimitive ?: return null
        value.booleanOrNull?.let { return it }
        return when (value.contentOrNull?.trim()?.lowercase()) {
            "false", "no", "0" -> false
            "true", "yes", "1" -> true
            else -> null
        }
    }

    private fun JsonObject.string(key: String): String = (this[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()

    // ------------------------------------------------------------------------------ paywalls

    /** Wording newsrooms put where the free part ends. */
    private val PAYWALL_PHRASES = Regex(
        "(?i)(jetzt weiterlesen|weiterlesen mit (spiegel\\+|z\\+|f\\+|sz plus|welt ?plus|tagesspiegel plus|abo|einem abo|ihrem abo)|" +
            "zum weiterlesen (bitte )?(anmelden|registrieren|abonnieren|einloggen)|mit (einem|ihrem) abo weiterlesen|" +
            "(nur|exklusiv) für (abonnenten|abonnentinnen|abonnent\\*innen|registrierte|eingeloggte)|" +
            "dieser (artikel|beitrag|inhalt) ist (nur |exklusiv )?(für|abonnenten)|jetzt abonnieren und weiterlesen|" +
            "um (diesen|den) (artikel|beitrag) (weiter|vollständig )?zu lesen|abo abschließen|bereits abonnent|sie haben bereits ein abo|" +
            "testen sie (jetzt )?(spiegel\\+|z\\+|f\\+|sz plus|welt ?plus)|" +
            "subscribe (now )?to (continue|read|keep reading|unlock)|this (article|story|content) is (only )?(for|available to|exclusive to|reserved for) (subscribers|members|paying)|" +
            "already a subscriber\\?? (sign|log) in|sign in to (continue|keep) reading|create a (free )?account to (continue|keep) reading|" +
            "you('ve| have) reached your (limit|free article)|become a (member|subscriber) to (read|continue)|" +
            "start your free trial to (read|continue)|unlock this article|to continue reading,? (please )?(subscribe|sign in|log in))",
    )

    /**
     * Brand marks of paid articles and the bare "subscribe now"; only trusted at the end of the
     * article or inside a paywall element (elsewhere they are links in the page's menus).
     */
    private val PAYWALL_BRANDS = Regex("(?i:jetzt abonnieren|subscribe now|subscribers only|for subscribers)|(SPIEGEL\\+|(?<![\\p{L}\\d])Z\\+|(?<![\\p{L}\\d])F\\+|SZ Plus|WELTplus|Tagesspiegel Plus|Handelsblatt Premium|\\bPremium-Artikel\\b|\\bPLUS-Artikel\\b)")

    /** Elements that hold a paywall notice. */
    private const val PAYWALL_ELEMENTS =
        "[class*=paywall], [id*=paywall], [class*=Paywall], [class*=regwall], [class*=loginwall], [class*=piano], [id*=piano], " +
            "[class*=plus-teaser], [class*=plusTeaser], [data-paywall], [data-area=paywall]"

    /**
     * True when the page announces that the rest is for subscribers: paywall wording anywhere in
     * the visible page, or a paid brand mark ([PAYWALL_BRANDS]) inside the article or a paywall
     * element. Only meaningful for a short text – a long article that mentions "SPIEGEL+" is free.
     */
    fun hasPaywallMarks(document: Document, articleText: String): Boolean {
        if (PAYWALL_PHRASES.containsMatchIn(articleText) || PAYWALL_BRANDS.containsMatchIn(articleText.takeLast(600))) return true
        val body = document.body() ?: return false
        val notices = body.select(PAYWALL_ELEMENTS).filterNot { it.isChrome() }
        if (notices.any { el -> el.text().let { PAYWALL_PHRASES.containsMatchIn(it) || PAYWALL_BRANDS.containsMatchIn(it) } }) return true
        return PAYWALL_PHRASES.containsMatchIn(visibleText(body))
    }

    /** The body's text without navigation, header, footer and menus (where "Jetzt abonnieren" is just a link). */
    private fun visibleText(body: Element): String {
        val copy = body.clone()
        copy.select("nav, header, footer, aside, script, style, noscript, template, [class*=nav], [class*=menu], [class*=footer], [class*=header]").remove()
        return copy.text()
    }

    private fun Element.isChrome(): Boolean = parents().any { it.normalName() in CHROME }

    private val CHROME = setOf("nav", "header", "footer")

    // ------------------------------------------------------------------------------ bot checks

    /** Fingerprints of bot-check pages (Cloudflare, AWS WAF, DataDome, PerimeterX, Akamai, Incapsula, Centinel). */
    private val CHALLENGE = Regex(
        "(?i)(<title>\\s*just a moment|challenges\\.cloudflare\\.com|cf-chl-|attention required! \\| cloudflare|" +
            "awswafcookiedomainlist|gokuprops|captcha-delivery\\.com|px-captcha|_pxcaptcha|perimeterx|" +
            "_incapsula_resource|incapsula incident|centinelanalytica|please enable js and disable any ad blocker|" +
            "<title>\\s*access denied\\s*</title>|are you a robot|bist du ein mensch|verify you are (a )?human)",
    )

    /** True when [html] is a bot check rather than the page (only judged for pages without an article). */
    fun isBotCheck(html: String): Boolean = CHALLENGE.containsMatchIn(html.take(CHALLENGE_SCAN))

    private const val CHALLENGE_SCAN = 200_000

    /** URL words of consent pages a newsroom redirects to (golem.de → /sonstiges/zustimmung/…). */
    private val CONSENT_URL = Regex("(?i)(/zustimmung|/consent|consent\\.|/cmp/|privacy-gate|cookie-?wall|/einwilligung|/datenschutz-einstellungen|myprivacy\\.)")

    /** True when the reader landed on a consent page instead of [requested]. */
    fun isConsentRedirect(requested: String, landed: String): Boolean =
        landed != requested && CONSENT_URL.containsMatchIn(landed) && !CONSENT_URL.containsMatchIn(requested)
}
