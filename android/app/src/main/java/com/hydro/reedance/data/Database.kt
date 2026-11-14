package com.hydro.reedance.data

import androidx.room.*
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(primaryKeys = ["songId", "itemType", "itemId"])
data class Bookmark(val songId: String, val itemType: String, val itemId: String, val createdAt: Long = System.currentTimeMillis())
@Entity data class ReviewCard(@PrimaryKey val lexemeId: String, val stage: Int = 1, val dueAt: Long = System.currentTimeMillis(), val savedAt: Long = System.currentTimeMillis())
@Entity(primaryKeys = ["targetLanguage", "stepId"])
data class StarterProgress(val targetLanguage: String, val stepId: Int, val completedAt: Long = System.currentTimeMillis())
@Entity data class SongPlayback(@PrimaryKey val songId: String, val positionMs: Long)
@Entity(primaryKeys = ["songId", "questionId", "versionCode", "gradedAt"])
data class QuizResult(val songId: String, val questionId: String, val versionCode: Int, val answer: String, val correct: Boolean, val gradedAt: Long = System.currentTimeMillis())
@Entity data class InstalledPackage(@PrimaryKey val songId: String, val activeVersionCode: Int, val signedPayloadSha256: String, val activePath: String, val state: String, val lastCheckedAt: Long)
@Entity data class SeenManifest(@PrimaryKey val songId: String, val highestVersionCode: Int, val packageSha256: String)
@Dao interface LearningDao {
    @Query("SELECT * FROM Bookmark ORDER BY createdAt DESC") fun bookmarks(): Flow<List<Bookmark>>
    @Query("SELECT * FROM Bookmark WHERE songId = :song AND itemType = :type AND itemId = :id") suspend fun bookmark(song: String, type: String, id: String): Bookmark?
    @Upsert suspend fun saveBookmark(value: Bookmark)
    @Delete suspend fun deleteBookmark(value: Bookmark)
    @Query("SELECT * FROM ReviewCard ORDER BY dueAt") fun reviews(): Flow<List<ReviewCard>>
    @Upsert suspend fun saveReview(value: ReviewCard)
    @Query("SELECT * FROM ReviewCard WHERE lexemeId = :id") suspend fun review(id: String): ReviewCard?
    @Query("SELECT * FROM StarterProgress") fun starter(): Flow<List<StarterProgress>>
    @Query("SELECT EXISTS(SELECT 1 FROM StarterProgress WHERE targetLanguage = :lang AND stepId = :step)") suspend fun hasStep(lang: String, step: Int): Boolean
    @Upsert suspend fun saveStep(value: StarterProgress)
    @Query("DELETE FROM StarterProgress WHERE targetLanguage = :lang AND stepId = :step") suspend fun deleteStep(lang: String, step: Int)
    @Upsert suspend fun savePlayback(value: SongPlayback)
    @Query("SELECT * FROM SongPlayback WHERE songId = :id") suspend fun playback(id: String): SongPlayback?
    @Insert suspend fun saveResult(value: QuizResult)
    @Query("SELECT COUNT(*) FROM QuizResult") fun resultCount(): Flow<Int>
    @Query("SELECT COUNT(*) FROM ReviewCard WHERE dueAt <= :now") suspend fun dueCount(now: Long): Int
    @Query("SELECT COUNT(*) FROM StarterProgress") suspend fun stepCount(): Int
    @Query("SELECT * FROM ReviewCard WHERE dueAt <= :now ORDER BY dueAt") suspend fun dueReviews(now: Long): List<ReviewCard>
    @Query("SELECT * FROM QuizResult ORDER BY gradedAt DESC") fun results(): Flow<List<QuizResult>>
    @Query("SELECT songId FROM InstalledPackage") fun installedIds(): Flow<List<String>>
    @Query("SELECT * FROM InstalledPackage") suspend fun installedPackages(): List<InstalledPackage>
    @Query("SELECT * FROM InstalledPackage WHERE songId = :id") suspend fun installed(id: String): InstalledPackage?
    @Upsert suspend fun saveInstalled(value: InstalledPackage)
    @Query("DELETE FROM InstalledPackage WHERE songId = :id") suspend fun deleteInstalled(id: String)
    @Query("SELECT * FROM SeenManifest WHERE songId = :id") suspend fun seen(id: String): SeenManifest?
    @Upsert suspend fun saveSeen(value: SeenManifest)
}
@Database(entities = [Bookmark::class, ReviewCard::class, StarterProgress::class, SongPlayback::class, QuizResult::class, InstalledPackage::class, SeenManifest::class], version = 1, exportSchema = false)
abstract class LearningDatabase: RoomDatabase() {
    abstract fun learning(): LearningDao
    companion object {
        @Volatile private var instance: LearningDatabase? = null
        fun get(context: Context): LearningDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LearningDatabase::class.java, "learning.db").build().also { instance = it }
        }
    }
}
