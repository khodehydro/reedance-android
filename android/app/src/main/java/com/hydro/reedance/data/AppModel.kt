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

class AppModel @JvmOverloads constructor(app: Application, private val starterOnly: Boolean = false): AndroidViewModel(app) {
    private val preload = if(starterOnly) StarterPreload.snapshot() else null
    private val database = LearningDatabase.get(app)
    val dao = database.learning()
    private val content = LocalContentRepository(app, dao)
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
    val bookmarks = dao.bookmarks().distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviews = dao.reviews().distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val steps = dao.starter().distinctUntilChanged().onEach { StarterPreload.steps = it; stepsLoaded = true }.stateIn(viewModelScope, SharingStarted.Eagerly, preload?.steps ?: emptyList())
    val resultCount = dao.resultCount().distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val catalog = MutableStateFlow<List<JsonObject>>(emptyList())
    val starters = MutableStateFlow(preload?.courses ?: JsonObject())
    val lesson = MutableStateFlow<JsonObject?>(null)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow("")
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
                    val lessonTask = async { content.restoreDemo() }
                    val loadedSettings = preferences.flow.first()
                    val loadedSteps = dao.starter().first()
                    StarterPreload.settings = loadedSettings
                    StarterPreload.steps = loadedSteps
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
    fun onBackground() { updateProgress(); checkpoint(force = true) }
    private var installRequest: java.util.UUID? = null
    val installProgress = MutableStateFlow("")
    fun cancelInstall() { installRequest?.let { WorkManager.getInstance(getApplication()).cancelWorkById(it) } }
    fun openInstalled(id: String) = operation("بارگذاری بسته ناموفق / Package load failed") {
        checkpoint(force = true)
        lesson.value = content.installedLesson(id)
    }
    val savingPackPassword = MutableStateFlow(false)
    fun savePackPassword(password: String) {
        if(savingPackPassword.value || busy.value) return
        savingPackPassword.value = true
        viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { com.hydro.reedance.install.PackKeyStore(getApplication()).save(password) }
            message.value = "رمز فایل ذخیره شد / File password saved"
        } catch(cancelled: CancellationException) { throw cancelled } catch(error: Exception) { message.value = error.message ?: "Could not store file password" }
        finally { savingPackPassword.value = false }
        }
    }
    fun forgetPackPassword() = operation("حذف رمز ناموفق / Could not forget password") {
        withContext(Dispatchers.IO) { com.hydro.reedance.install.PackKeyStore(getApplication()).clear() }
        message.value = "رمز فایل حذف شد / File password forgotten"
    }
    fun importLocalPackage(uri: android.net.Uri) = enqueuePackWork(workDataOf("localUri" to uri.toString()))
    fun installBundledPackage(id: String) = enqueuePackWork(workDataOf("bundledSong" to id))
    fun installPackage(id: String) = enqueuePackWork(workDataOf("songId" to id))
    private fun enqueuePackWork(input: Data) {
        if(busy.value) return
        busy.value = true
        val manager = WorkManager.getInstance(getApplication())
        val request = OneTimeWorkRequestBuilder<com.hydro.reedance.install.PackInstallWorker>()
            .setInputData(input)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if(input.getString("localUri") == null && input.getString("bundledSong") == null) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED).build()).build()
        installRequest = request.id
        val enqueue = manager.enqueueUniqueWork("pack-install", ExistingWorkPolicy.KEEP, request)
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { enqueue.result.get() }
                manager.getWorkInfosForUniqueWorkFlow("pack-install").first { infos ->
                    val info = infos.firstOrNull { it.id == request.id } ?: infos.firstOrNull { !it.state.isFinished } ?: return@first false
                    installRequest = info.id
                    installProgress.value = "${info.progress.getString("phase") ?: info.state.name} · ${info.progress.getLong("bytes", 0) / 1024} KB"
                    if(info.state.isFinished) {
                        if(info.state == WorkInfo.State.SUCCEEDED) {
                            lesson.value = content.installedLesson(info.outputData.getString("songId") ?: input.getString("songId") ?: "")
                            catalog.value = content.catalog()
                            message.value = "بسته نصب شد؛ در فهرست آهنگ‌ها باز کنید / Package installed; open it in the song list"
                        }
                        else if(info.state == WorkInfo.State.FAILED) message.value = info.outputData.getString("error") ?: "Installation failed"
                        true
                    } else false
                }
            } catch(cancelled: CancellationException) { throw cancelled } catch(error: Exception) { message.value = error.message ?: "Installation failed" } finally { busy.value = false; installProgress.value = "" }
        }
    }
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
        val old = dao.bookmark(lesson.value?.s("songId") ?: return@mutation, type, id)
        if(old != null) dao.deleteBookmark(old) else {
            dao.saveBookmark(Bookmark(lesson.value?.s("songId") ?: return@mutation, type, id))
            if(type == "vocabulary" && dao.review(id) == null) dao.saveReview(ReviewCard(id))
        }
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
        val current = dao.review(card.lexemeId) ?: return@learningMutation false
        if(current.stage != card.stage || current.dueAt != card.dueAt) return@learningMutation false
        val next = schedule(current.stage, remembered, System.currentTimeMillis())
        dao.saveReview(current.copy(stage = next.stage, dueAt = next.dueAt))
        true
    } }
    suspend fun dueReviewCards() = dao.dueReviews(System.currentTimeMillis())
    fun record(q: JsonObject, answer: List<String>, grade: Grade) { learningMutation {
        val encoded = withContext(Dispatchers.Default) { Gson().toJson(answer) }
        dao.saveResult(QuizResult(lesson.value?.s("songId") ?: return@learningMutation false, q.s("id"), dao.installed(lesson.value!!.s("songId"))?.activeVersionCode ?: 1, encoded, grade.correct))
        grade.missed.forEach { id -> val old = dao.review(id); dao.saveReview(ReviewCard(id, 1, System.currentTimeMillis(), old?.savedAt ?: System.currentTimeMillis())) }
        true
    } }
    fun deleteDemo() {
        if(busy.value) return
        busy.value = true
        operation("حذف فایل ناموفق / Could not remove song files") {
            try {
                pause(); prepareJob?.cancelAndJoin()
                mediaMutex.withLock { controller?.stop(); controller?.clearMediaItems(); started.value = false; activePlayback.value = null; content.deleteDemo(); lesson.value = null }
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
