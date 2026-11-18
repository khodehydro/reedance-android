package com.hydro.reedance.data

import androidx.room.*
import android.content.Context
import kotlinx.coroutines.flow.Flow

@Entity(primaryKeys = ["songId", "itemType", "itemId"])
data class Bookmark(val songId: String, val itemType: String, val itemId: String, val createdAt: Long = System.currentTimeMillis())
@Entity(primaryKeys = ["songId", "lexemeId"]) data class ReviewCard(val lexemeId: String, val stage: Int = 1, val dueAt: Long = System.currentTimeMillis(), val savedAt: Long = System.currentTimeMillis(), val songId: String = "")
@Entity(primaryKeys = ["targetLanguage", "stepId"])
data class StarterProgress(val targetLanguage: String, val stepId: Int, val completedAt: Long = System.currentTimeMillis())
@Entity data class SongPlayback(@PrimaryKey val songId: String, val positionMs: Long)
@Entity(primaryKeys = ["songId", "questionId", "versionCode", "gradedAt"])
data class QuizResult(val songId: String, val questionId: String, val versionCode: Int, val answer: String, val correct: Boolean, val gradedAt: Long = System.currentTimeMillis())
@Entity data class InstalledPackage(@PrimaryKey val songId: String, val activeVersionCode: Int, val signedPayloadSha256: String, val activePath: String, val state: String, val lastCheckedAt: Long)
@Entity data class SeenManifest(@PrimaryKey val songId: String, val highestVersionCode: Int, val packageSha256: String)
@Entity(primaryKeys = ["targetLanguage", "songId", "lexemeId"])
data class GameWordProgress(
    val targetLanguage: String,
    val songId: String,
    val lexemeId: String,
    val levelIndex: Int? = null,
    val positionInLevel: Int = 0,
    val solved: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)
@Dao interface LearningDao {
    @Query("SELECT * FROM Bookmark ORDER BY createdAt DESC") fun bookmarks(): Flow<List<Bookmark>>
    @Query("SELECT * FROM Bookmark WHERE songId = :song AND itemType = :type AND itemId = :id") suspend fun bookmark(song: String, type: String, id: String): Bookmark?
    @Query("SELECT COUNT(*) FROM Bookmark WHERE itemType = :type") suspend fun bookmarkCount(type: String): Int
    @Upsert suspend fun saveBookmark(value: Bookmark)
    @Delete suspend fun deleteBookmark(value: Bookmark)
    @Query("SELECT * FROM ReviewCard ORDER BY dueAt") fun reviews(): Flow<List<ReviewCard>>
    @Upsert suspend fun saveReview(value: ReviewCard)
    @Query("SELECT * FROM ReviewCard WHERE lexemeId = :id AND songId = :song") suspend fun review(id: String, song: String): ReviewCard?
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
    @Query("SELECT * FROM GameWordProgress WHERE targetLanguage = :language ORDER BY CASE WHEN levelIndex IS NULL THEN 1 ELSE 0 END, levelIndex, positionInLevel, addedAt")
    fun gameWords(language: String): Flow<List<GameWordProgress>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addGameWords(words: List<GameWordProgress>)
    @Query("SELECT * FROM GameWordProgress WHERE targetLanguage = :language AND levelIndex IS NULL ORDER BY addedAt, songId, lexemeId")
    suspend fun pendingGameWords(language: String): List<GameWordProgress>
    @Query("SELECT MAX(levelIndex) FROM GameWordProgress WHERE targetLanguage = :language") suspend fun maxGameLevel(language: String): Int?
    @Query("UPDATE GameWordProgress SET levelIndex = :level, positionInLevel = :position WHERE targetLanguage = :language AND songId = :song AND lexemeId = :lexeme AND levelIndex IS NULL")
    suspend fun placeGameWord(language: String, song: String, lexeme: String, level: Int, position: Int)
    @Query("UPDATE GameWordProgress SET solved = 1 WHERE targetLanguage = :language AND songId = :song AND lexemeId = :lexeme")
    suspend fun solveGameWord(language: String, song: String, lexeme: String)
}
@Database(entities = [Bookmark::class, ReviewCard::class, StarterProgress::class, SongPlayback::class, QuizResult::class, InstalledPackage::class, SeenManifest::class, GameWordProgress::class], version = 3, exportSchema = false)
abstract class LearningDatabase: RoomDatabase() {
    abstract fun learning(): LearningDao
    companion object {
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE ReviewCard_new (lexemeId TEXT NOT NULL, stage INTEGER NOT NULL, dueAt INTEGER NOT NULL, savedAt INTEGER NOT NULL, songId TEXT NOT NULL, PRIMARY KEY(songId, lexemeId))")
                db.execSQL("INSERT INTO ReviewCard_new SELECT r.lexemeId, r.stage, r.dueAt, r.savedAt, b.songId FROM ReviewCard r JOIN Bookmark b ON b.itemId = r.lexemeId AND b.itemType = 'vocabulary' GROUP BY b.songId, r.lexemeId")
                // Preserve unassignable historical progress rather than guessing its language.
                db.execSQL("INSERT OR IGNORE INTO ReviewCard_new SELECT lexemeId, stage, dueAt, savedAt, '' FROM ReviewCard WHERE lexemeId NOT IN (SELECT lexemeId FROM ReviewCard_new)")
                db.execSQL("DROP TABLE ReviewCard")
                db.execSQL("ALTER TABLE ReviewCard_new RENAME TO ReviewCard")
            }
        }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS GameWordProgress (targetLanguage TEXT NOT NULL, songId TEXT NOT NULL, lexemeId TEXT NOT NULL, levelIndex INTEGER, positionInLevel INTEGER NOT NULL, solved INTEGER NOT NULL, addedAt INTEGER NOT NULL, PRIMARY KEY(targetLanguage, songId, lexemeId))")
            }
        }
        @Volatile private var instance: LearningDatabase? = null
        fun get(context: Context): LearningDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, LearningDatabase::class.java, "learning.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
        }
    }
}
