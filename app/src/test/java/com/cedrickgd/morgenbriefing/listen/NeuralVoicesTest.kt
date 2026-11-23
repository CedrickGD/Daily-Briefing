package com.cedrickgd.morgenbriefing.listen

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Catalog, archive handling and the download store of "Natürliche Stimmen", with fake archives. */
class NeuralVoicesTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun start() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun stop() {
        scope.cancel()
        server.shutdown()
    }

    /** A .tar.bz2 like k2-fsa's: one top folder, the model, tokens and espeak-ng data. */
    private fun archive(top: String, files: Map<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(bytes)).use { tar ->
            tar.putArchiveEntry(TarArchiveEntry("$top/"))
            tar.closeArchiveEntry()
            files.forEach { (name, content) ->
                val data = content.toByteArray()
                val entry = TarArchiveEntry(if (name.startsWith("/") || name.startsWith("..")) name else "$top/$name")
                entry.size = data.size.toLong()
                tar.putArchiveEntry(entry)
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun voiceFiles(id: String) = mapOf(
        "$id.onnx" to "model",
        "$id.onnx.json" to "{}",
        "tokens.txt" to "_ 0",
        "MODEL_CARD" to "License: CC0",
        "espeak-ng-data/phontab" to "phonemes",
        "espeak-ng-data/de_dict" to "deutsch",
        "espeak-ng-data/en_dict" to "english",
        "espeak-ng-data/ru_dict" to "russian, big",
        "espeak-ng-data/lang/gmw/de" to "lang",
    )

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `a natural voice is stored next to engine voices as neural colon id`() {
        assertEquals("neural:de_DE-thorsten-medium", NeuralVoiceIds.choice("de_DE-thorsten-medium"))
        assertEquals("de_DE-thorsten-medium", NeuralVoiceIds.idOf("neural:de_DE-thorsten-medium"))
        assertNull(NeuralVoiceIds.idOf("de-de-x-deb-local"))
        assertNull(NeuralVoiceIds.idOf("neural:"))
        assertNull(NeuralVoiceIds.idOf(null))
    }

    @Test
    fun `the catalog offers one or two freely licensed voices per language`() {
        val all = NeuralVoiceCatalog.ALL
        assertEquals(all.size, all.map { it.id }.distinct().size)
        listOf("de", "en", "fr", "es", "it").forEach { language ->
            val voices = NeuralVoiceCatalog.forLanguage(language)
            assertTrue("$language: ${voices.size}", voices.size in 1..2)
        }
        all.forEach { voice ->
            assertEquals(64, voice.sha256.length)
            assertTrue(voice.id.startsWith("${voice.language}_${voice.country}-"))
            assertTrue(voice.bytes in 10_000_000..40_000_000)
            assertTrue(voice.licence in setOf("CC0", "CC BY 4.0", "CC BY 3.0", NeuralVoice.PUBLIC_DOMAIN))
            assertEquals("vits-piper-${voice.id}-int8.tar.bz2", voice.archive)
        }
        // Never a voice whose recordings are not free, nor a fan voice.
        val excluded = listOf("lessac", "ryan", "hfc_", "pavoque", "glados", "tom", "amy", "upmc")
        all.forEach { voice -> excluded.forEach { name -> assertFalse("${voice.id} contains $name", voice.id.contains(name)) } }
        assertEquals(1, NeuralVoiceCatalog.byId("es_ES-sharvard-medium")?.speaker)
        assertNull(NeuralVoiceCatalog.byId("en_US-lessac-medium"))
        assertTrue(NeuralVoiceCatalog.forLanguage("nl").isEmpty())
    }

    @Test
    fun `archive paths lose the top folder and never leave the voice folder`() {
        assertEquals("tokens.txt", NeuralArchive.relativePath("vits-piper-x/tokens.txt"))
        assertEquals("espeak-ng-data/de_dict", NeuralArchive.relativePath("vits-piper-x/espeak-ng-data/de_dict"))
        assertEquals("tokens.txt", NeuralArchive.relativePath("./vits-piper-x/./tokens.txt"))
        assertNull(NeuralArchive.relativePath("vits-piper-x/"))
        assertNull(NeuralArchive.relativePath("vits-piper-x/../../evil"))
        assertNull(NeuralArchive.relativePath("/etc/passwd"))
    }

    @Test
    fun `only the voice's own and the English espeak dictionaries are kept`() {
        assertTrue(NeuralArchive.keep("espeak-ng-data/de_dict", "de"))
        assertTrue(NeuralArchive.keep("espeak-ng-data/en_dict", "de"))
        assertFalse(NeuralArchive.keep("espeak-ng-data/ru_dict", "de"))
        assertTrue(NeuralArchive.keep("espeak-ng-data/phontab", "de"))
        assertTrue(NeuralArchive.keep("espeak-ng-data/lang/gmw/de", "fr"))
        assertTrue(NeuralArchive.keep("de_DE-thorsten-medium.onnx", "de"))
    }

    @Test
    fun `an archive unpacks into a complete voice folder`() {
        val id = "de_DE-test-medium"
        val target = temp.newFolder("voice")
        NeuralArchive.unpack(ByteArrayInputStream(archive("vits-piper-$id-int8", voiceFiles(id))), target, "de")
        assertTrue(File(target, "$id.onnx").isFile)
        assertTrue(File(target, "espeak-ng-data/de_dict").isFile)
        assertTrue(File(target, "espeak-ng-data/en_dict").isFile)
        assertFalse(File(target, "espeak-ng-data/ru_dict").exists())
        assertTrue(File(target, "espeak-ng-data/lang/gmw/de").isFile)
        assertNotNull(NeuralVoiceFiles.verified(target, id))
        // Without the phoneme data the voice cannot speak: not complete.
        File(target, "espeak-ng-data/phontab").delete()
        assertNull(NeuralVoiceFiles.verified(target, id))
    }

    @Test
    fun `an archive with an entry outside its folder is refused`() {
        val target = temp.newFolder("evil")
        val bytes = archive("vits-piper-x", mapOf("tokens.txt" to "x", "../../outside.txt" to "boom"))
        try {
            NeuralArchive.unpack(ByteArrayInputStream(bytes), target, "de")
        } catch (e: IOException) {
            // Refused as a whole, or the entry was skipped: either way nothing lands outside.
        }
        assertFalse(File(temp.root, "outside.txt").exists())
        assertFalse(File(target.parentFile, "outside.txt").exists())
    }

    @Test
    fun `a download is checked, unpacked into place and can be deleted`() = runBlocking {
        val id = "de_DE-test-medium"
        val bytes = archive("vits-piper-$id-int8", voiceFiles(id))
        val voice = NeuralVoice(id, "de", "DE", "Test", VoiceGender.MALE, bytes.size.toLong(), sha256(bytes), "Test", "CC0")
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val root = temp.newFolder("neural-voices")
        val store = NeuralVoices(root, OkHttpClient(), scope, release = server.url("/tts-models").toString().trimEnd('/'))
        var installed = false
        store.download(voice) { installed = true }
        withTimeout(10_000) { store.state.first { id in it.installed } }
        assertTrue(installed)
        assertEquals("/tts-models/vits-piper-$id-int8.tar.bz2", server.takeRequest().path)
        assertNotNull(store.files(id))
        assertTrue(File(root, "$id/$id.onnx").isFile)
        assertFalse(File(root, ".partial/${voice.archive}").exists())
        assertTrue(store.state.value.downloads.isEmpty())

        store.delete(id)
        assertNull(store.files(id))
        withTimeout(5_000) { while (File(root, id).exists()) Thread.sleep(10) }
    }

    @Test
    fun `a download with the wrong checksum fails and leaves nothing behind`() = runBlocking {
        val id = "de_DE-test-medium"
        val bytes = archive("vits-piper-$id-int8", voiceFiles(id))
        val voice = NeuralVoice(id, "de", "DE", "Test", VoiceGender.MALE, bytes.size.toLong(), "0".repeat(64), "Test", "CC0")
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)))
        val root = temp.newFolder("neural-voices")
        val store = NeuralVoices(root, OkHttpClient(), scope, release = server.url("/").toString().trimEnd('/'))
        store.download(voice) { fail("must not install") }
        val state = withTimeout(10_000) { store.state.first { id in it.failed } }
        assertFalse(id in state.installed)
        assertTrue(state.downloads.isEmpty())
        assertFalse(File(root, id).exists())
        assertFalse(File(root, ".partial/$id").exists())
    }

    @Test
    fun `installed voices are found again after a restart, half downloads are cleared`() = runBlocking {
        val root = temp.newFolder("neural-voices")
        val thorsten = NeuralVoiceCatalog.byId("de_DE-thorsten-medium")!!
        NeuralArchive.unpack(ByteArrayInputStream(archive("x", voiceFiles(thorsten.id))), File(root, thorsten.id), "de")
        File(root, ".partial/leftover").apply { parentFile!!.mkdirs(); writeText("half") }
        val store = NeuralVoices(root, OkHttpClient(), scope)
        val state = withTimeout(5_000) { store.state.first { it.loaded } }
        assertEquals(setOf(thorsten.id), state.installed)
        assertFalse(File(root, ".partial").exists())
    }

    @Test
    fun `parts are spoken sentence by sentence`() {
        val text = "Erster Satz. Zweiter Satz! Dritter"
        val ranges = NeuralSentences.ranges(text)
        assertEquals(listOf(0, 13, 27), ranges.map { it.first })
        assertEquals(text.length, ranges.last().second)
        assertEquals("Zweiter Satz! ", text.substring(ranges[1].first, ranges[1].second))
        assertTrue(NeuralSentences.ranges("  ").isEmpty())
        assertEquals(listOf(0 to 5), NeuralSentences.ranges("Hallo"))
    }
}
