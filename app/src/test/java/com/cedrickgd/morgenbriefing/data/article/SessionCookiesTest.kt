package com.cedrickgd.morgenbriefing.data.article

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionCookiesTest {

    @Test
    fun `cookies come back to their host and path, parent-domain cookies to subdomains`() {
        val jar = SessionCookies()
        jar.save("https://www.example.de/aktuell/artikel.html".toHttpUrl(), listOf("session=abc; Path=/; HttpOnly", "consent=1; Domain=.example.de; Path=/", "list=x"))
        assertEquals("session=abc; consent=1; list=x", jar.header("https://www.example.de/aktuell/other.html".toHttpUrl()))
        assertEquals("consent=1", jar.header("https://m.example.de/".toHttpUrl()))
        assertEquals("session=abc; consent=1", jar.header("https://www.example.de/sport/".toHttpUrl()))
        assertNull(jar.header("https://www.other.de/".toHttpUrl()))
    }

    @Test
    fun `foreign and top-level domains are refused, Max-Age 0 deletes, a newer value replaces`() {
        val jar = SessionCookies()
        val url = "https://www.example.de/".toHttpUrl()
        jar.save(url, listOf("a=1; Domain=other.de", "b=2; Domain=de", "c=3"))
        assertEquals("c=3", jar.header(url))
        jar.save(url, listOf("c=4"))
        assertEquals("c=4", jar.header(url))
        jar.save(url, listOf("c=; Max-Age=0"))
        assertNull(jar.header(url))
    }
}
