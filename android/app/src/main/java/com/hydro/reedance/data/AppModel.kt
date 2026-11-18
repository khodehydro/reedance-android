package com.hydro.reedance.data

import android.app.Application
import android.content.ComponentName
import androidx.work.*
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.gson.*
import com.hydro.reedance.BuildConfig
import com.hydro.reedance.domain.*
import com.hydro.reedance.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ActivePlayback(val songId: String, val title: String, val artist: String, val coverPath: String?)
data class InstallTransferProgress(val phase: String, val bytes: Long, val totalBytes: Long) {
    val fraction: Float? get() = totalBytes.takeIf { it > 0 }?.let { (bytes.toFloat() / it).coerceIn(0f, 1f) }
}

class AppModel @JvmOverloads constructor(app: Application, private val starterOnly: Boolean = false): AndroidViewModel(app) {
    private val preload = if(starterOnly) StarterPreload.snapshot() else null
    private val database = LearningDatabase.get(app)
    val dao = database.learning()
    private val content = LocalContentRepository(app, dao)
    private val libraryContent = LibraryContentLoader(dao, content)
    internal fun cachedLibraryItem(mark: Bookmark) = libraryContent.cached(mark)
    internal suspend fun libraryItem(mark: Bookmark) = libraryContent.load(mark)

    val installedIds = dao.installedIds().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val activity = ActivityRepository(app)
    val activityDays = activity.days.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    private var activityJob: Job? = null
    fun foregroundActivity(active: Boolean) {
        val previous = activityJob
        previous?.cancel()
        if(active) activityJob = viewModelScope.launch {
            previous?.join()
            var stamp = SystemClock.elapsedRealtime()
            var wall = System.currentTimeMillis()
            suspend fun flush() {
                val end = SystemClock.elapsedRealtime()
                activity.add(wall, end - stamp)
                stamp = end
                wall = System.currentTimeMillis()
            }
            try { while(true) { delay(5_000); flush() } } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { message.value = "ذخیره فعالیت ناموفق / Activity save failed" } finally { withContext(NonCancellable) { try { flush() } catch(_: Exception) { message.value = "ذخیره فعالیت ناموفق / Activity save failed" } } }
        }
    }
    private var settingsLoaded = preload != null
    private var stepsLoaded = preload != null
    val preferences = SettingsRepository(app)
    val settings = preferences.flow.onEach { StarterPreload.settings = it; settingsLoaded = true }.stateIn(viewModelScope, SharingStarted.Eagerly, preload?.settings ?: Settings())
    val ready = MutableStateFlow(preload != null)
    val steps = dao.starter().distinctUntilChanged().onEach { StarterPreload.steps = it; stepsLoaded = true }.stateIn(viewModelScope, SharingStarted.Eagerly, preload?.steps ?: emptyList())
    val catalog = MutableStateFlow<List<JsonObject>>(emptyList())
    private val languageSongIds = combine(catalog, settings) { songs, prefs ->
        songs.filter { it.s("language") == prefs.targetLanguage }.map { it.s("id") }.toSet()
    }
    val bookmarks = combine(dao.bookmarks(), languageSongIds) { rows, ids -> rows.filter { it.songId in ids } }
        .flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviews = combine(dao.reviews(), languageSongIds) { rows, ids -> rows.filter { it.songId in ids } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val resultCount = combine(dao.results(), languageSongIds) { rows, ids -> rows.count { it.songId in ids } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val starters = MutableStateFlow(preload?.courses ?: JsonObject())
    val lesson = MutableStateFlow<JsonObject?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val premiumRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playing = MutableStateFlow(false)
    val activePlayback = MutableStateFlow<ActivePlayback?>(null)
    val position = MutableStateFlow(0L)
    val duration = MutableStateFlow(64000L)
    val started = MutableStateFlow(false)
    val repeat = MutableStateFlow(false)
    val muted = MutableStateFlow(false)
    // Song content re-renders only at a lyric boundary, not at every progress tick.
    val activeLineId = lesson.map { it?.a("lyrics").orEmpty() }.combine(position) { lines, ms ->
        lines.lastOrNull { ms >= it.n("startMs") }?.s("id")
    }.distinctUntilChanged().flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var speech: Speech? = null
    private val mediaMutex = Mutex()
    private val mutationMutex = Mutex()
    private var playCommand: Job? = null
    private var prepareJob: Job? = null
    private val checkpointPolicy = PlaybackCheckpoint()
    private val checkpoints = Channel<SongPlayback>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            preferences.flow.map { it.reminders }.distinctUntilChanged().collect { enabled ->
                com.hydro.reedance.notifications.LearningReminders.schedule(app, enabled)
            }
        }
        viewModelScope.launch {
            try {
                if(starterOnly) {
                    // Warm entry is already ready from snapshots. Cold restoration
                    // loads only the Starter prerequisites, not catalog/audio/demo.
                    starters.value = content.starters()
                    settings.first { settingsLoaded }
                    steps.first { stepsLoaded }
                } else coroutineScope {
                    // Independent reads run concurrently; I/O and JSON CPU work have separate dispatchers.
                    val catalogTask = async {
                        com.hydro.reedance.install.PackInstaller(app).recover()
                        content.catalog()
                    }
                    val startersTask = async { content.starters() }
                    val loadedSettings = preferences.flow.first()
                    val loadedSteps = dao.starter().first()
                    StarterPreload.settings = loadedSettings
                    StarterPreload.steps = loadedSteps
                    catalog.value = catalogTask.await()
                    starters.value = startersTask.await()
                }
            } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { message.value = "خطا در بارگذاری / Could not load local data" } finally { ready.value = true }
        }
        // Media3 is main-looper confined. Only sample UI progress while playing AND observed.
        viewModelScope.launch {
            combine(playing, position.subscriptionCount) { active, observers -> active && observers > 0 }
                .distinctUntilChanged().collectLatest { needsProgress ->
                    if(needsProgress) while(isActive) { updateProgress(); delay(500) }
                }
        }
        viewModelScope.launch {
            playing.collectLatest { active ->
                if(active) while(isActive) { delay(5_000); checkpoint(force = false) }
            }
        }
        // A single conflated writer prevents old playback checkpoints overtaking newer ones.
        viewModelScope.launch(Dispatchers.IO) {
            for(value in checkpoints) {
                try { dao.savePlayback(value) } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { message.value = "ذخیرهٔ موقعیت ناموفق / Could not save playback position" }
            }
        }
    }
    private fun operation(error: String, block: suspend () -> Unit): Job = viewModelScope.launch {
        try { block() } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { message.value = error }
    }
    private suspend fun awaitController(future: ListenableFuture<MediaController>): MediaController = suspendCancellableCoroutine { continuation ->
        future.addListener({
            if(continuation.isActive) {
                try { continuation.resume(future.get()) } // Listener runs only after completion; never blocks.
                catch(error: Exception) { continuation.resumeWithException(error) }
            }
        }, ContextCompat.getMainExecutor(getApplication<Application>()))
        // The future belongs to the ViewModel, not one request; onCleared releases even a late result.
    }
    private suspend fun prepareMedia(): MediaController? = mediaMutex.withLock {
        val data = lesson.value ?: return@withLock null
        val coverPath = content.coverPath(data.s("songId"), data)
        val audioUri = content.audioFile(data.s("songId"), data).toURI().toString()
        val player = controller ?: run {
            val app = getApplication<Application>()
            val future = controllerFuture ?: MediaController.Builder(app,
                SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync().also { controllerFuture = it }
            awaitController(future).also { p ->
                controller = p
                if(p.mediaItemCount > 0) {
                    started.value = true; playing.value = p.isPlaying
                    repeat.value = p.repeatMode == Player.REPEAT_MODE_ONE; muted.value = p.volume == 0f
                } else {
                    p.repeatMode = if(repeat.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    p.volume = if(muted.value) 0f else 1f
                }
                p.addListener(object: Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing.value = isPlaying; updateProgress()
                        if(!isPlaying) checkpoint(force = true)
                    }
                    override fun onPlaybackStateChanged(playbackState: Int) { updateProgress() }
                    override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                        updateProgress(); checkpoint(force = true)
                    }
                    override fun onPlayerError(error: PlaybackException) { message.value = "پخش ناموفق / Playback failed" }
                })
            }
        }
        if(player.currentMediaItem?.mediaId != data.s("songId") || player.currentMediaItem?.localConfiguration?.uri.toString() != audioUri) {
            checkpoint(force = true)
            val saved = dao.playback(data.s("songId"))?.positionMs ?: 0L
            player.setMediaItem(MediaItem.Builder().setMediaId(data.s("songId")).setUri(audioUri)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(data.s("title")).setArtist(data.s("artist")).build()).build(), saved)
            player.prepare(); updateProgress()
        }
        activePlayback.value = ActivePlayback(data.s("songId"), data.s("title"), data.s("artist"), coverPath)
        player
    }
    /** Mini controls operate on the current Media3 item, never the browsed lesson. */
    fun toggleActivePlayback() {
        val player = controller ?: return
        if(player.currentMediaItem == null) return
        if(player.isPlaying) player.pause() else { started.value = true; player.play() }
    }
    fun preparePlayback() {
        if(prepareJob?.isActive == true) return
        prepareJob = operation("پلیر در دسترس نیست / Player unavailable") { prepareMedia() }
    }
    private fun updateProgress() {
        controller?.takeIf { it.mediaItemCount > 0 }?.let {
            position.value = it.currentPosition.coerceAtLeast(0)
            duration.value = it.duration.takeIf { ms -> ms > 0 } ?: 64000L
        }
    }
    private fun checkpoint(force: Boolean) {
        val p = controller ?: return
        if(!started.value || p.mediaItemCount == 0) return
        val ms = p.currentPosition.coerceAtLeast(0)
        if(checkpointPolicy.shouldSave(ms, SystemClock.elapsedRealtime(), force)) checkpoints.trySend(SongPlayback(p.currentMediaItem?.mediaId ?: return, ms))
    }
    fun onBackground() {
        updateProgress()
        checkpoint(force = true)
        if(!PremiumEntitlement.active.value) pause()
    }
    private var installRequest: java.util.UUID? = null
    val installTransfer = MutableStateFlow<InstallTransferProgress?>(null)
    fun cancelInstall() { installRequest?.let { WorkManager.getInstance(getApplication()).cancelWorkById(it) } }
    fun cachedLesson(id: String): JsonObject? = content.cachedLesson(id)
    suspend fun openInstalled(id: String) {
        checkpoint(force = true)
        content.cachedLesson(id)?.let { lesson.value = it }
        lesson.value = content.installedLesson(id)
    }
    suspend fun songCoverPath(id: String, data: JsonObject): String? = content.coverPath(id, data)
    fun installBundledPackage(id: String) = enqueuePackWork(workDataOf("bundledSong" to id))
    fun installPackage(id: String) = enqueuePackWork(workDataOf("songId" to id))
    private fun enqueuePackWork(input: Data) {
        if(busy.value) return
        busy.value = true
        val manager = WorkManager.getInstance(getApplication())
        val request = OneTimeWorkRequestBuilder<com.hydro.reedance.install.PackInstallWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if(input.getString("bundledSong") == null) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED).build()).build()
        installRequest = request.id
        val enqueue = manager.enqueueUniqueWork("pack-install", ExistingWorkPolicy.KEEP, request)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { enqueue.result.get() }
                manager.getWorkInfosForUniqueWorkFlow("pack-install").first { infos ->
                    val info = infos.firstOrNull { it.id == request.id } ?: infos.firstOrNull { !it.state.isFinished } ?: return@first false
                    installRequest = info.id
                    val phase = info.progress.getString("phase") ?: info.state.name
                    val bytes = info.progress.getLong("bytes", 0)
                    val totalBytes = info.progress.getLong("totalBytes", 0)
                    installTransfer.value = InstallTransferProgress(phase, bytes, totalBytes)
                    if(info.state.isFinished) {
                        if(info.state == WorkInfo.State.SUCCEEDED) {
                            lesson.value = content.installedLesson(info.outputData.getString("songId") ?: input.getString("songId") ?: "")
                            catalog.value = content.catalog()
                            message.value = "دانلود کامل شد / Download complete"
                        }
                        else if(info.state == WorkInfo.State.FAILED) message.value = info.outputData.getString("error") ?: "Installation failed"
                        true
                    } else false
                }
            } catch(cancelled: CancellationException) { throw cancelled } catch(error: Exception) { message.value = error.message ?: "Installation failed" } finally { busy.value = false; installTransfer.value = null }
        }
    }
    fun togglePlay() {
        playCommand?.cancel()
        playCommand = operation("پخش ناموفق / Playback failed") {
            prepareMedia()?.let { if(it.isPlaying) it.pause() else { started.value = true; it.play() } }
        }
    }
    fun playFromLine(songId: String, startMs: Long) {
        playCommand?.cancel()
        playCommand = operation("پخش خط ناموفق / Could not play this line") {
            require(lesson.value?.s("songId") == songId)
            val player = prepareMedia() ?: return@operation
            require(player.currentMediaItem?.mediaId == songId && lesson.value?.s("songId") == songId)
            val maximum = player.duration.takeIf { it > 0 } ?: lesson.value!!.obj("media").obj("audio").n("durationMs")
            player.seekTo(startMs.coerceIn(0, maximum.coerceAtLeast(0)))
            started.value = true
            player.play()
            updateProgress(); checkpoint(force = true)
        }
    }
    fun pause() { playCommand?.cancel(); controller?.pause(); updateProgress(); checkpoint(force = true) }
    fun seek(ms: Long) { controller?.seekTo(ms.coerceIn(0, duration.value)); position.value = ms.coerceIn(0, duration.value); checkpoint(force = true) }
    fun toggleRepeat() { repeat.value = !repeat.value; controller?.repeatMode = if(repeat.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    fun toggleMute() { muted.value = !muted.value; controller?.volume = if(muted.value) 0f else 1f }
    fun speak(text: String, locale: String = "en-GB") {
        operation("تلفظ در دسترس نیست / Pronunciation unavailable") {
            val engine = speech ?: Speech(getApplication<Application>()).also { speech = it }
            if(!engine.say(text, locale)) message.value = "صدای آفلاین نصب نیست؛ متن را بخوانید / No offline voice installed; use the text"
        }
    }
    fun reminders(enabled: Boolean) { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.reminders(enabled) } }
    fun completePermissionSetup() { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.completeSetup() } }
    fun recordVisit() { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.visited(); com.hydro.reedance.notifications.LearningReminders.schedule(getApplication(), preferences.flow.first().reminders) } }
    fun targetLanguage(value: String) { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.targetLanguage(value) } }
    fun language(value: String) { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.language(value) } }
    fun theme(value: String) { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.theme(value) } }
    fun textSize(value: Int) { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.size(value) } }
    fun motion(value: Boolean) { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.motion(value) } }
    fun rating(value: Int) { operation("ذخیره تنظیمات ناموفق / Settings save failed") { preferences.rating(value) } }
    private fun mutation(block: suspend () -> Unit) = operation("ذخیره پیشرفت ناموفق / Progress save failed") {
        mutationMutex.withLock { withContext(Dispatchers.IO) { database.withTransaction { block() } } }
    }
    fun toggleBookmark(type: String, id: String, songId: String? = lesson.value?.s("songId")) { mutation {
        val song = songId ?: return@mutation
        val old = dao.bookmark(song, type, id)
        if(old != null) {
            dao.deleteBookmark(old)
        } else {
            val subscribed = PremiumEntitlement.active.value
            val isPremiumSong = catalog.value.any { it.s("id") == song && it.s("access") == "paid" }
            val access = LessonAccess(premium = isPremiumSong, subscribed = subscribed)
            if(!access.canBookmarkContent() || !access.canAddBookmark(dao.bookmarkCount(type))) {
                premiumRequired.tryEmit(Unit)
                return@mutation
            }
            dao.saveBookmark(Bookmark(song, type, id))
            if(type == "vocabulary" && dao.review(id, song) == null) dao.saveReview(ReviewCard(id, songId = song))
        }
    } }
    /** Removal is idempotent: rapid taps cannot toggle an item back on before Room emits. */
    fun removeBookmark(mark: Bookmark) { mutation {
        dao.bookmark(mark.songId, mark.itemType, mark.itemId)?.let { dao.deleteBookmark(it) }
    } }
    private fun learningMutation(block: suspend () -> Boolean) = operation("ذخیره پیشرفت ناموفق / Progress save failed") {
        val completed = mutationMutex.withLock { withContext(Dispatchers.IO) { database.withTransaction { block() } } }
        if(completed) activity.complete()
    }
    fun step(lang: String, step: Int) { learningMutation {
        if(dao.hasStep(lang, step)) { dao.deleteStep(lang, step); false }
        else { dao.saveStep(StarterProgress(lang, step)); true }
    } }
    fun review(card: ReviewCard, remembered: Boolean) { learningMutation {
        val current = dao.review(card.lexemeId, card.songId) ?: return@learningMutation false
        if(current.stage != card.stage || current.dueAt != card.dueAt) return@learningMutation false
        val next = schedule(current.stage, remembered, System.currentTimeMillis())
        dao.saveReview(current.copy(stage = next.stage, dueAt = next.dueAt))
        true
    } }
    suspend fun dueReviewCards(): List<ReviewCard> {
        val ids = catalog.value.filter { it.s("language") == settings.value.targetLanguage }.map { it.s("id") }.toSet()
        return dao.dueReviews(System.currentTimeMillis()).filter { it.songId in ids && it.songId == lesson.value?.s("songId") }
    }
    suspend fun lessonForReview(songId: String): JsonObject? = content.installedLesson(songId)

    fun gameWordProgress(targetLanguage: String): Flow<List<GameWordProgress>> = dao.gameWords(targetLanguage)

    fun solveGameWord(targetLanguage: String, songId: String, lexemeId: String, revealDelayMs: Long = 0L) = operation("ذخیرهٔ پیشرفت ناموفق / Could not save game progress") {
        if(revealDelayMs > 0) delay(revealDelayMs)
        withContext(Dispatchers.IO) { dao.solveGameWord(targetLanguage, songId, lexemeId) }
    }

    /** Index new installed-song vocabulary once; assigned levels and solved words survive restarts. */
    suspend fun syncGameWords(targetLanguage: String, subscribed: Boolean): List<GameWordDefinition> {
        val installedIds = withContext(Dispatchers.IO) {
            dao.installedPackages().filter { it.state in listOf("VERIFIED", "BUNDLED", "DIRECT_TEST", "LOCAL_PASSWORD") }
                .map { it.songId }.toSet()
        }
        val sources = catalog.value.filter { song ->
            song.s("id") in installedIds && song.s("language") == targetLanguage &&
                (subscribed || song.s("access") != "paid")
        }.sortedBy { it.s("id") }
        val definitions = buildList {
            sources.forEach { song ->
                val lesson = try { content.installedLesson(song.s("id")) }
                    catch(cancelled: CancellationException) { throw cancelled }
                    catch(_: Exception) { null } ?: return@forEach
                lesson.a("vocabulary").forEach { word ->
                    val lemma = word.s("lemma").trim()
                    val answer = lemma.filter { it.isLetter() }
                    val lexeme = word.s("lexemeId")
                    if(lexeme.isNotBlank() && answer.isNotBlank()) add(GameWordDefinition(
                        targetLanguage = targetLanguage,
                        songId = song.s("id"),
                        lexemeId = lexeme,
                        songTitle = song.s("title"),
                        lemma = lemma,
                        letterAnswer = answer,
                        translationFa = word.s("translationFa"),
                        translationEn = word.s("translationEn")
                    ))
                }
            }
        }
        withContext(Dispatchers.IO) {
            mutationMutex.withLock {
                database.withTransaction {
                    val addedAt = System.currentTimeMillis()
                    dao.addGameWords(definitions.map { GameWordProgress(targetLanguage, it.songId, it.lexemeId, addedAt = addedAt) })
                    val pending = dao.pendingGameWords(targetLanguage)
                    val sizes = balancedGameLevelSizes(pending.size)
                    var offset = 0
                    val firstLevel = (dao.maxGameLevel(targetLanguage) ?: -1) + 1
                    sizes.forEachIndexed { levelOffset, size ->
                        repeat(size) { position ->
                            val pendingWord = pending[offset++]
                            dao.placeGameWord(targetLanguage, pendingWord.songId, pendingWord.lexemeId, firstLevel + levelOffset, position)
                        }
                    }
                }
            }
        }
        return definitions
    }

    fun record(q: JsonObject, answer: List<String>, grade: Grade) {
        val sourceLesson = lesson.value ?: return
        val sourceSong = sourceLesson.s("songId")
        learningMutation {
        val encoded = withContext(Dispatchers.Default) { Gson().toJson(answer) }
        dao.saveResult(QuizResult(sourceSong, q.s("id"), dao.installed(sourceSong)?.activeVersionCode ?: 1, encoded, grade.correct))
        val song = sourceSong
        grade.missed.forEach { id -> val old = dao.review(id, song); dao.saveReview(ReviewCard(id, 1, System.currentTimeMillis(), old?.savedAt ?: System.currentTimeMillis(), song)) }
        true
    } }
    override fun onCleared() {
        speech?.close()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        checkpoints.close()
        // Room is application-lifetime storage; do not synchronously close/join its executors on the UI thread.
        super.onCleared()
    }
}
