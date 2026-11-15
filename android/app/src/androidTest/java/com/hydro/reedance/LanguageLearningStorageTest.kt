package com.hydro.reedance

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hydro.reedance.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LanguageLearningStorageTest {
    @Test fun identicalLexemeIdsInDifferentSongsHaveIndependentSchedules() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), LearningDatabase::class.java).build()
        try {
            val dao = db.learning()
            dao.saveReview(ReviewCard("shared-id", stage = 2, songId = "tr-song"))
            dao.saveReview(ReviewCard("shared-id", stage = 4, songId = "en-song"))
            val turkish = dao.review("shared-id", "tr-song")!!
            dao.saveReview(turkish.copy(stage = 3))
            assertEquals(3, dao.review("shared-id", "tr-song")!!.stage)
            assertEquals(4, dao.review("shared-id", "en-song")!!.stage)
            assertEquals(2, dao.reviews().first().size)
            dao.saveBookmark(Bookmark("tr-song", "vocabulary", "shared-id"))
            dao.saveBookmark(Bookmark("en-song", "vocabulary", "shared-id"))
            dao.deleteBookmark(dao.bookmark("tr-song", "vocabulary", "shared-id")!!)
            assertNotNull(dao.bookmark("en-song", "vocabulary", "shared-id"))
        } finally { db.close() }
    }
}
