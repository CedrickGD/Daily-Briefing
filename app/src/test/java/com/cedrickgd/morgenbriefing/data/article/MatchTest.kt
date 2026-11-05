package com.cedrickgd.morgenbriefing.data.article

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fuzzy text and picture matching, with URLs and texts from live pages (05.11.2026). */
class MatchTest {

    @Test
    fun `texts - quotes, punctuation and case do not hide a repeat, other sentences stay apart`() {
        assertTrue(TextMatch.same("Am ersten Tag der Kunstrad-WM in Ludwigsburg steigt die Eröffnungsfeier.", "am ersten Tag der Kunstrad-WM in Ludwigsburg steigt die Eröffnungsfeier!"))
        assertTrue(TextMatch.same("„Mit Hubert Aiwanger geht ein echtes Mannsbild“, sagte er.", "\"Mit Hubert Aiwanger geht ein echtes Mannsbild\", sagte er."))
        assertFalse(TextMatch.same("Die Regierung plant neue Steuern für Unternehmen im kommenden Jahr.", "Die Opposition lehnt neue Steuern für Unternehmen im kommenden Jahr ab, sagte sie."))
        assertFalse(TextMatch.same("Kurz", "Kurz und knapp"))
        assertTrue(TextMatch.covers("Berlin (dpa) - Der Bundestag hat das Gesetz am Donnerstag mit großer Mehrheit beschlossen und damit den Weg frei gemacht.", "Der Bundestag hat das Gesetz am Donnerstag mit großer Mehrheit beschlossen"))
        assertTrue(TextMatch.startsWithCut("Drei entscheidende Zahlen fehlen im Börsenprospekt von Anthropic. Die Financial Times erklärt alles.", "Drei entscheidende Zahlen fehlen im Börsenprospekt von Anthropic. Die Financ…"))
        assertFalse(TextMatch.covers("Im Wahlkreis 12 gewann die Kandidatin mit 48 Prozent der Stimmen klar.", "Im Wahlkreis 13 gewann die Kandidatin mit 48 Prozent der Stimmen klar."))
    }

    @Test
    fun `pictures - renditions of one photo are the same picture`() {
        // tagesschau: feed 16x9-big ?width=1920, page 16x7 ?width=1280 – same UUID.
        assertTrue(
            ImageMatch.same(
                "https://images.tagesschau.de/image/8e6be664-a867-424d-82e0-a1f8ce29c500/AAABoaTfkVw/AAABnSSvrFg/16x9-big/demokraten-124.jpg?width=1920",
                "https://images.tagesschau.de/image/8e6be664-a867-424d-82e0-a1f8ce29c500/AAABoaTfkVw/AAABnSSvdEs/16x7/demokraten-124.jpg?width=1280",
            ),
        )
        // DLF: 1920x1920 vs 1920x1080.
        assertTrue(
            ImageMatch.same(
                "https://bilder.deutschlandfunk.de/73/cb/28/3f/73cb283f-b56a-4199-b844-d73ddded4d3b/alexander-throm-cdu-100-1920x1920.jpg",
                "https://bilder.deutschlandfunk.de/73/cb/28/3f/73cb283f-b56a-4199-b844-d73ddded4d3b/alexander-throm-cdu-100-1920x1080.jpg",
            ),
        )
        // time.com: same asset, other width and crop in the query.
        assertTrue(
            ImageMatch.same(
                "https://static.time.com/v3/assets/bltea6093859af6183b/blt6b3a39ce17c0ff77/6aec3f9cd52f4907d2036b3f/tehran_demonstration.jpg?branch=production&width=1024&crop=16:9",
                "https://static.time.com/v3/assets/bltea6093859af6183b/blt6b3a39ce17c0ff77/6aec3f9cd52f4907d2036b3f/tehran_demonstration.jpg?branch=production&width=3840&crop=3:2",
            ),
        )
        // hessenschau: same file name, other rendition suffix.
        assertTrue(ImageMatch.same("https://www.hessenschau.de/gesellschaft/genz-zeil-kontrollen-100~_t-1793822996833_v-16to9.jpg", "https://www.hessenschau.de/gesellschaft/genz-zeil-kontrollen-100~_t-1793822996833_v-16to9__retina.jpg"))
        // A Next.js resizer stands for the picture it wraps.
        assertTrue(ImageMatch.same("https://www.aerzteblatt.de/_next/image?url=%2F95e726ab-b0fe-494e-9b4d-cc0ff9ae5af4%2FZervixkarzinom_2178580229.jpg&w=1200&q=75", "https://www.aerzteblatt.de/95e726ab-b0fe-494e-9b4d-cc0ff9ae5af4/Zervixkarzinom_2178580229.jpg"))
    }

    @Test
    fun `pictures - shared account numbers, shoot codes and dates do not make two photos one`() {
        // nbcnews.com: every picture of the page sits under the same account number.
        assertFalse(ImageMatch.same("https://media14.s-nbcnews.com/i/mpx/2704722219/2026_11/vert_thumb.00_00_00_00.Still002-js9nyv.jpg", "https://media14.s-nbcnews.com/i/mpx/2704722219/2026_11/mamdani_mtpn_tn_3-v3z67r.jpg"))
        // arstechnica.com: one photo shoot, two photos.
        assertFalse(ImageMatch.same("https://cdn.arstechnica.net/wp-content/uploads/2026/11/26C0346_013-980x653.jpg", "https://cdn.arstechnica.net/wp-content/uploads/2026/11/26C0346_033-980x653.jpg"))
        // utopia.de: same upload date in the file names.
        assertFalse(ImageMatch.same("https://images.utopia.de/z7MN/rt:fill/w:726/plain/2022/12/bei-schlechter-isolierung-kann-schimmel-221223_download.jpg", "https://images.utopia.de/_KfE/rt:fill/w:726/plain/2022/12/du-solltest-regelmaessig-lueften-221223_download.jpg"))
        assertFalse(ImageMatch.same(null, "https://x.de/a.jpg"))
    }
}
