package com.hydro.reedance.data

import android.app.Application
import android.content.ComponentName
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

class AppModel(app: Application): AndroidViewModel(app) {
    private val database = LearningDatabase.get(app)
    val dao = database.learning()
    private val content = LocalContentRepository(app, dao)
    val preferences = SettingsRepository(app)
    val settings = preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val ready = MutableStateFlow(false)
    val bookmarks = dao.bookmarks().distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviews = dao.reviews().distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val steps = dao.starter().distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val resultCount = dao.resultCount().distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val catalog = MutableStateFlow<List<JsonObject>>(emptyList())
    val starters = MutableStateFlow(JsonObject())
    val lesson = MutableStateFlow<JsonObject?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
    val playing = MutableStateFlow(false)
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
                coroutineScope {
                    // Independent reads run concurrently; I/O and JSON CPU work have separate dispatchers.
                    val catalogTask = async { content.catalog() }
                    val startersTask = async { content.starters() }
                    val lessonTask = async { content.restoreDemo() }
                    preferences.flow.first()
                    catalog.value = catalogTask.await()
                    starters.value = startersTask.await()
                    lesson.value = lessonTask.await()
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
        if(lesson.value == null) return@withLock null
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
        if(player.mediaItemCount == 0) {
            val saved = dao.playback("midnight-avenue")?.positionMs ?: 0L
            player.setMediaItem(MediaItem.Builder().setUri(File(content.demoDir, "audio.wav").toURI().toString())
                .setMediaMetadata(MediaMetadata.Builder().setTitle("Midnight Avenue").setArtist("Reedance Originals").build()).build(), saved)
            player.prepare(); updateProgress()
        }
        player
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
        if(checkpointPolicy.shouldSave(ms, SystemClock.elapsedRealtime(), force)) checkpoints.trySend(SongPlayback("midnight-avenue", ms))
    }
    fun onBackground() { updateProgress(); checkpoint(force = true) }
    fun installDemo() {
        if(!BuildConfig.DEBUG || busy.value) return
        busy.value = true
        operation("نصب دمو ناموفق بود / Demo installation failed") {
            try { lesson.value = content.installDemo() } finally { busy.value = false }
        }
    }
    fun togglePlay() {
        playCommand?.cancel()
        playCommand = operation("پخش ناموفق / Playback failed") {
            prepareMedia()?.let { if(it.isPlaying) it.pause() else { started.value = true; it.play() } }
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
    fun toggleBookmark(type: String, id: String) { mutation {
        val old = dao.bookmark("midnight-avenue", type, id)
        if(old != null) dao.deleteBookmark(old) else {
            dao.saveBookmark(Bookmark("midnight-avenue", type, id))
            if(type == "vocabulary" && dao.review(id) == null) dao.saveReview(ReviewCard(id))
        }
    } }
    fun step(lang: String, step: Int) { mutation {
        if(dao.hasStep(lang, step)) dao.deleteStep(lang, step) else dao.saveStep(StarterProgress(lang, step))
    } }
    fun review(card: ReviewCard, remembered: Boolean) { mutation {
        val current = dao.review(card.lexemeId) ?: return@mutation
        if(current.stage != card.stage || current.dueAt != card.dueAt) return@mutation
        val next = schedule(current.stage, remembered, System.currentTimeMillis())
        dao.saveReview(current.copy(stage = next.stage, dueAt = next.dueAt))
    } }
    suspend fun dueReviewCards() = dao.dueReviews(System.currentTimeMillis())
    fun record(q: JsonObject, answer: List<String>, grade: Grade) { mutation {
        val encoded = withContext(Dispatchers.Default) { Gson().toJson(answer) }
        dao.saveResult(QuizResult("midnight-avenue", q.s("id"), 1, encoded, grade.correct))
        grade.missed.forEach { id -> val old = dao.review(id); dao.saveReview(ReviewCard(id, 1, System.currentTimeMillis(), old?.savedAt ?: System.currentTimeMillis())) }
    } }
    fun deleteDemo() {
        if(busy.value) return
        busy.value = true
        operation("حذف فایل ناموفق / Could not remove song files") {
            try {
                pause(); prepareJob?.cancelAndJoin()
                mediaMutex.withLock { controller?.stop(); controller?.clearMediaItems(); started.value = false; content.deleteDemo(); lesson.value = null }
            } finally { busy.value = false }
        }
    }
    override fun onCleared() {
        speech?.close()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        checkpoints.close()
        // Room is application-lifetime storage; do not synchronously close/join its executors on the UI thread.
        super.onCleared()
    }
}
