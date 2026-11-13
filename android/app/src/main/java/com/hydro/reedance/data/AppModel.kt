package com.hydro.reedance.data

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.media3.common.*
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import com.google.gson.*
import com.hydro.reedance.BuildConfig
import com.hydro.reedance.domain.*
import com.hydro.reedance.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

class AppModel(app: Application): AndroidViewModel(app) {
    private val database = Room.databaseBuilder(app, LearningDatabase::class.java, "learning.db").build()
    val dao = database.learning()
    val preferences = SettingsRepository(app)
    val settings = preferences.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val ready = MutableStateFlow(false)
    val bookmarks = dao.bookmarks().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviews = dao.reviews().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val steps = dao.starter().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val results = dao.results().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
    private var controller: MediaController? = null
    private val controllerFuture = MediaController.Builder(app, SessionToken(app, ComponentName(app, PlaybackService::class.java))).buildAsync()
    val speech = Speech(app)
    private val demoDir get() = File(app.filesDir, "demo/midnight-avenue/1")
    init {
        controllerFuture.addListener({
            runCatching {
                controller = controllerFuture.get().apply {
                    addListener(object: Player.Listener {
                        override fun onIsPlayingChanged(isPlaying: Boolean) { playing.value = isPlaying }
                        override fun onPlayerError(error: PlaybackException) { message.value = "پخش ناموفق / Playback failed" }
                    })
                }
                loadMedia()
            }.onFailure { message.value = "پلیر در دسترس نیست / Player unavailable" }
        }, ContextCompat.getMainExecutor(app))
        viewModelScope.launch {
            try {
                preferences.flow.first()
                withContext(Dispatchers.IO) {
                    catalog.value = JsonParser.parseString(app.assets.open("catalog_preview.json").bufferedReader().use { it.readText() }).asJsonArray.map { it.asJsonObject }
                    starters.value = readAsset("starter.json")
                    if (BuildConfig.DEBUG && File(demoDir, "lesson.json").isFile && File(demoDir, "audio.wav").isFile) {
                        lesson.value = JsonParser.parseString(File(demoDir, "lesson.json").readText()).asJsonObject
                    }
                    File(app.filesDir, "demo-staging").deleteRecursively()
                }
                loadMedia()
            } catch(_: Exception) { message.value = "خطا در بارگذاری / Could not load local data" }
            finally { ready.value = true }
        }
        viewModelScope.launch {
            while(isActive) {
                controller?.let { p ->
                    position.value = p.currentPosition.coerceAtLeast(0)
                    duration.value = p.duration.takeIf { it > 0 } ?: 64000L
                    if (started.value) dao.savePlayback(SongPlayback("midnight-avenue", position.value))
                }
                delay(700)
            }
        }
    }
    private fun readAsset(name: String) = JsonParser.parseString(getApplication<Application>().assets.open(name).bufferedReader().use { it.readText() }).asJsonObject
    private fun loadMedia() {
        if (lesson.value == null || controller?.mediaItemCount != 0) return
        controller?.apply {
            setMediaItem(MediaItem.Builder().setUri(File(demoDir, "audio.wav").toURI().toString()).setMediaMetadata(MediaMetadata.Builder().setTitle("Midnight Avenue").setArtist("Reedance Originals").build()).build())
            prepare()
            viewModelScope.launch { seekTo(dao.playback("midnight-avenue")?.positionMs ?: 0L) }
        }
    }
    fun installDemo() {
        if (!BuildConfig.DEBUG || busy.value) return
        busy.value = true
        viewModelScope.launch {
            runCatching {
                val value = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val staging = File(app.filesDir, "demo-staging").apply { deleteRecursively(); mkdirs() }
                    val data = readAsset("demo_lesson.json")
                    File(staging, "lesson.json").writeText(data.toString())
                    app.assets.open("midnight-avenue-demo.wav").use { input -> File(staging, "audio.wav").outputStream().use { input.copyTo(it) } }
                    check(com.hydro.reedance.contract.PackVerifier.sha256(File(staging, "audio.wav").readBytes()) == data.obj("media").obj("audio").s("sha256"))
                    demoDir.parentFile!!.mkdirs()
                    check(!demoDir.exists() && staging.renameTo(demoDir))
                    dao.saveInstalled(InstalledPackage("midnight-avenue", 1, "LOCAL-DEMO-NOT-A-SIGNED-PACK", demoDir.path, "DEMO", System.currentTimeMillis()))
                    data
                }
                lesson.value = value; loadMedia()
            }.onFailure { message.value = "نصب دمو ناموفق بود / Demo installation failed" }
            busy.value = false
        }
    }
    fun togglePlay() { controller?.let { if(it.isPlaying) it.pause() else { started.value = true; it.play() } } }
    fun pause() { controller?.pause() }
    fun seek(ms: Long) { controller?.seekTo(ms.coerceIn(0, duration.value)); position.value = ms.coerceIn(0, duration.value) }
    fun toggleRepeat() { repeat.value = !repeat.value; controller?.repeatMode = if(repeat.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF }
    fun toggleMute() { muted.value = !muted.value; controller?.volume = if(muted.value) 0f else 1f }
    fun speak(text: String, locale: String = "en-GB") { if (!speech.say(text, locale)) message.value = "صدای آفلاین نصب نیست؛ متن را بخوانید / No offline voice installed; use the text" }
    fun language(value: String) { viewModelScope.launch { preferences.language(value) } }
    fun theme(value: String) { viewModelScope.launch { preferences.theme(value) } }
    fun textSize(value: Int) { viewModelScope.launch { preferences.size(value) } }
    fun motion(value: Boolean) { viewModelScope.launch { preferences.motion(value) } }
    fun rating(value: Int) { viewModelScope.launch { preferences.rating(value) } }
    fun toggleBookmark(type: String, id: String) {
        viewModelScope.launch {
            val old = bookmarks.value.find { it.songId == "midnight-avenue" && it.itemType == type && it.itemId == id }
            if (old != null) dao.deleteBookmark(old) else {
                dao.saveBookmark(Bookmark("midnight-avenue", type, id))
                if(type == "vocabulary" && dao.review(id) == null) dao.saveReview(ReviewCard(id))
            }
        }
    }
    fun step(lang: String, step: Int) { viewModelScope.launch {
        if(steps.value.any { it.targetLanguage == lang && it.stepId == step }) dao.deleteStep(lang, step) else dao.saveStep(StarterProgress(lang, step))
    } }
    fun review(card: ReviewCard, remembered: Boolean) { viewModelScope.launch {
        val next = schedule(card.stage, remembered, System.currentTimeMillis())
        dao.saveReview(card.copy(stage = next.stage, dueAt = next.dueAt))
    } }
    fun record(q: JsonObject, answer: List<String>, grade: Grade) { viewModelScope.launch {
        dao.saveResult(QuizResult("midnight-avenue", q.s("id"), 1, Gson().toJson(answer), grade.correct))
        grade.missed.forEach { id -> val old = dao.review(id); dao.saveReview(ReviewCard(id, 1, System.currentTimeMillis(), old?.savedAt ?: System.currentTimeMillis())) }
    } }
    fun deleteDemo() { viewModelScope.launch {
        controller?.stop(); controller?.clearMediaItems(); started.value = false
        withContext(Dispatchers.IO) { demoDir.deleteRecursively(); dao.deleteInstalled("midnight-avenue") }
        lesson.value = null
    } }
    override fun onCleared() { speech.close(); MediaController.releaseFuture(controllerFuture); database.close(); super.onCleared() }
}
