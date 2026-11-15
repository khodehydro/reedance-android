package com.hydro.reedance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hydro.reedance.data.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LibraryContentLoaderTest {
    @Test fun concurrentCardsShareIndexAndPackageUpdatesInvalidateIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LearningDatabase::class.java).build()
        val directory = File(context.cacheDir, "library-loader-${System.nanoTime()}").apply { mkdirs() }
        try {
            val dao = db.learning()
            val loader = LibraryContentLoader(dao, LocalContentRepository(context, dao))
            val lesson = File(directory, "lesson.json")
            lesson.writeText("""{"songId":"test-song","title":"First","vocabulary":[{"lexemeId":"word","lemma":"one"}]}""")
            dao.saveInstalled(InstalledPackage("test-song", 1, "first-hash", directory.path, "VERIFIED", 0))
            val mark = Bookmark("test-song", "vocabulary", "word")
            val results = coroutineScope { (1..20).map { async { loader.load(mark) } }.awaitAll() }
            val first = results.first()!!
            results.forEach { assertSame(first.item, it!!.item) }
            assertEquals("First", first.title)
            assertSame(first.item, loader.cached(mark)!!.item)
            lesson.writeText("""{"songId":"test-song","title":"Updated","vocabulary":[{"lexemeId":"word","lemma":"two"}]}""")
            dao.saveInstalled(InstalledPackage("test-song", 2, "second-hash", directory.path, "VERIFIED", 0))
            val updated = loader.load(mark)!!
            assertEquals("Updated", updated.title)
            assertNotSame(first.item, updated.item)
            dao.deleteInstalled("test-song")
            assertNull(loader.load(mark))
            assertNull(loader.cached(mark))
        } finally {
            db.close()
            directory.deleteRecursively()
        }
    }
}
