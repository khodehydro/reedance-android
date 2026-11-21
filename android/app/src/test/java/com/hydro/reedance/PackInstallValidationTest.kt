package com.hydro.reedance

import com.google.gson.JsonParser
import com.hydro.reedance.contract.PackVerifier as V
import com.hydro.reedance.data.*
import com.hydro.reedance.install.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

class PackInstallValidationTest {
    private fun bytes(name: String) = javaClass.getResourceAsStream("/$name")!!.readBytes()
    private fun reject(block: () -> Unit) { try { block() } catch(_: Exception) { return }; fail("Expected rejection") }
    @Test fun strictExtractorReadsAuthenticatedGoldenZipAndRejectsTrailingBytes() = runBlocking {
        val keys = JsonParser.parseString(String(bytes("keys.test.json"))).asJsonObject
        val manifest = V.manifest(bytes("latest.json"), mapOf("sig-test-v1" to V.base64(keys.s("ed25519PublicKeyBase64url"))), "quiet-step", "en")
        val zip = V.decryptFixture(bytes(manifest.obj("package").s("fileName")), manifest, mapOf("enc-test-v1" to V.base64(keys.s("aes256KeyBase64url"))))
        val root = java.nio.file.Files.createTempDirectory("pack-test").toFile()
        try {
            val input = File(root, "pack.zip").apply { writeBytes(zip) }
            val out = File(root, "content")
            StrictPackZip.extract(input, out)
            assertArrayEquals(bytes("lesson.json"), File(out, "lesson.json").readBytes())
            input.appendBytes(byteArrayOf(0))
            reject { runBlocking { StrictPackZip.extract(input, File(root, "bad")) } }
        } finally { root.deleteRecursively() }
    }
    @Test fun zipAcceptsOneOptionalStoryTrackAndRejectsMultipleTracks() = runBlocking {
        val root = java.nio.file.Files.createTempDirectory("story-pack-test").toFile()
        try {
            fun zip(vararg paths: String): File = File(root, "story.zip").apply {
                java.util.zip.ZipOutputStream(outputStream()).use { output ->
                    paths.forEach { name ->
                        val data = byteArrayOf(1, 2, 3)
                        val entry = java.util.zip.ZipEntry(name).apply {
                            method = java.util.zip.ZipEntry.STORED
                            size = data.size.toLong()
                            crc = java.util.zip.CRC32().apply { update(data) }.value
                        }
                        output.putNextEntry(entry)
                        output.write(data)
                        output.closeEntry()
                    }
                }
            }
            val out = File(root, "ok")
            StrictPackZip.extract(zip("lesson.json", "audio.wav", "cover.jpg", "story.m4a"), out)
            assertArrayEquals(byteArrayOf(1, 2, 3), File(out, "story.m4a").readBytes())
            reject { runBlocking {
                StrictPackZip.extract(zip("lesson.json", "audio.wav", "story.m4a", "story.wav"), File(root, "bad"))
            } }
        } finally { root.deleteRecursively() }
    }

    @Test fun lessonSchemaAcceptsStudioStoryMetadataAndRejectsUnknownStoryPaths() {
        val schema = JsonParser.parseString(File("src/main/assets/lesson.schema.json").readText()).asJsonObject
        val validator = LessonValidation(schema)
        val lesson = V.strictJson(bytes("lesson.json"), 4194304)
        val narration = lesson.obj("media").obj("audio").deepCopy().apply { addProperty("path", "story.wav") }
        lesson.obj("media").add("storyAudio", narration)
        lesson.add("story", JsonParser.parseString("""{"title":"An optional story","text":"A quiet step."}"""))
        validator.validate(lesson)
        narration.addProperty("path", "audio.wav")
        reject { validator.validate(lesson) }
    }

    @Test fun schemaAndSemanticChecksRejectBadReferencesAndTimeline() {
        val schema = JsonParser.parseString(File("src/main/assets/lesson.schema.json").readText()).asJsonObject
        val validator = LessonValidation(schema)
        val lesson = V.strictJson(bytes("lesson.json"), 4194304)
        validator.validate(lesson); validator.semantics(lesson)
        val bad = lesson.deepCopy().apply { addProperty("unknown", true) }
        reject { validator.validate(bad) }
        lesson.a("lyrics")[1].addProperty("startMs", 0)
        reject { validator.semantics(lesson) }
    }
}
