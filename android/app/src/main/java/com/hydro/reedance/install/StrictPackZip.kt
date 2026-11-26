package com.hydro.reedance.install

import java.io.*
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Contract-v1 ZIP: root files only, exact central/local agreement, no ZIP64/extras/descriptors. */
object StrictPackZip {
    private data class Entry(val name: String, val flags: Int, val method: Int, val crc: Long, val compressed: Long, val size: Long, val offset: Long)
    private fun RandomAccessFile.u16(): Int = readUnsignedByte() or (readUnsignedByte() shl 8)
    private fun RandomAccessFile.u32(): Long = u16().toLong() or (u16().toLong() shl 16)
    suspend fun extract(zip: File, destination: File, reeTalk: Boolean = false) {
        require(zip.length() in 22..268435456L)
        RandomAccessFile(zip, "r").use { input ->
            input.seek(input.length() - 22)
            require(input.u32() == 0x06054b50L) { "ZIP comments/trailing bytes unsupported" }
            require(input.u16() == 0 && input.u16() == 0)
            val count = input.u16(); require((if(reeTalk) count == 3 else count in 2..4) && input.u16() == count)
            val centralSize = input.u32(); val centralOffset = input.u32()
            require(input.u16() == 0 && centralOffset + centralSize == input.length() - 22)
            input.seek(centralOffset)
            val entries = (0 until count).map {
                require(input.u32() == 0x02014b50L)
                val madeBy = input.u16(); require(input.u16() <= 20)
                val flags = input.u16(); val method = input.u16()
                require(flags and 0xf7ff == 0 && method in listOf(0, 8))
                input.skipBytes(4)
                val crc = input.u32(); val compressed = input.u32(); val size = input.u32()
                val nameLength = input.u16(); require(nameLength in 1..16)
                require(input.u16() == 0 && input.u16() == 0 && input.u16() == 0)
                input.skipBytes(2); val external = input.u32(); val offset = input.u32()
                if(madeBy shr 8 == 3) require(((external ushr 16).toInt() and 0xf000) in listOf(0, 0x8000)) { "Nonregular ZIP file" }
                val nameBytes = ByteArray(nameLength).also { input.readFully(it) }
                require(nameBytes.all { b -> b.toInt() in 32..126 })
                val name = nameBytes.toString(Charsets.US_ASCII)
                val limit = when(name) {
                    "lesson.json", "dialogue.json" -> {
                        require(name == if(reeTalk) "dialogue.json" else "lesson.json")
                        4L * 1024 * 1024
                    }
                    "audio.mp3", "audio.m4a", "audio.wav", "story.mp3", "story.m4a", "story.wav" -> 240L * 1024 * 1024
                    "cover.jpg", "cover.png", "cover.webp" -> 8L * 1024 * 1024
                    else -> error("Unexpected ZIP path")
                }
                require(size in 1..limit && compressed in 1..268435456L)
                require(name in setOf("lesson.json", "dialogue.json") || size <= compressed * 100)
                Entry(name, flags, method, crc, compressed, size, offset)
            }
            require(input.filePointer == centralOffset + centralSize)
            require(entries.map { it.name }.toSet().size == count)
            val metadata = if(reeTalk) "dialogue.json" else "lesson.json"
            require(entries.count { it.name == metadata } == 1 && entries.count { it.name.startsWith("audio.") } == 1)
            val covers = entries.count { it.name.startsWith("cover.") }
            val stories = entries.count { it.name.startsWith("story.") }
            require(if(reeTalk) covers == 1 && stories == 0 else covers <= 1 && stories <= 1)
            require(entries.sumOf { it.size } <= 252L * 1024 * 1024)
            var expectedOffset = 0L
            require(destination.mkdirs())
            for(entry in entries.sortedBy { it.offset }) {
                currentCoroutineContext().ensureActive()
                require(entry.offset == expectedOffset)
                input.seek(entry.offset)
                require(input.u32() == 0x04034b50L && input.u16() <= 20)
                require(input.u16() == entry.flags && input.u16() == entry.method)
                input.skipBytes(4)
                require(input.u32() == entry.crc && input.u32() == entry.compressed && input.u32() == entry.size)
                val nameLength = input.u16(); require(nameLength == entry.name.length && input.u16() == 0)
                val name = ByteArray(nameLength).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                require(name == entry.name)
                val start = input.filePointer
                require(start + entry.compressed <= centralOffset)
                val bounded = object : InputStream() {
                    var remaining = entry.compressed
                    override fun read(): Int { if(remaining == 0L) return -1; remaining--; return input.readUnsignedByte() }
                    override fun read(b: ByteArray, off: Int, len: Int): Int {
                        if(remaining == 0L) return -1
                        val n = input.read(b, off, minOf(len.toLong(), remaining).toInt())
                        require(n > 0); remaining -= n; return n
                    }
                }
                val inflater = Inflater(true)
                try {
                    val stream = if(entry.method == 8) InflaterInputStream(bounded, inflater, 65536) else bounded
                    val crc = CRC32(); var size = 0L
                    File(destination, name).outputStream().use { out ->
                        val buffer = ByteArray(65536)
                        while(true) {
                            currentCoroutineContext().ensureActive()
                            val n = stream.read(buffer); if(n < 0) break
                            size += n; require(size <= entry.size)
                            crc.update(buffer, 0, n); out.write(buffer, 0, n)
                        }
                    }
                    require(size == entry.size && crc.value == entry.crc && bounded.remaining == 0L)
                    if(entry.method == 8) require(inflater.finished() && inflater.remaining == 0 && !inflater.needsDictionary())
                } finally { inflater.end() }
                expectedOffset = start + entry.compressed
            }
            require(expectedOffset == centralOffset)
        }
    }
}
