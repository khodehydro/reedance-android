package com.hydro.reedance.install

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException

/** Persistent restart-from-zero work: never resumes an unauthenticated partial file. */
class PackInstallWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val installer = PackInstaller(applicationContext)
        val progress: suspend (String, Long, Long) -> Unit = { phase, bytes, totalBytes ->
            setProgress(workDataOf("phase" to phase, "bytes" to bytes, "totalBytes" to totalBytes))
        }
        val uri = inputData.getString("localUri")
        val bundled = inputData.getString("bundledSong")
        val lesson = if(bundled != null) installer.installBundled(bundled, progress)
            else if(uri != null) installer.importLocal(android.net.Uri.parse(uri), progress)
            else installer.install(inputData.getString("songId") ?: error("Missing song identity"), progress)
        Result.success(workDataOf("songId" to lesson.get("songId").asString))
    } catch(cancelled: CancellationException) { throw cancelled } catch(error: Exception) { Result.failure(workDataOf("error" to (error.message ?: "Installation failed").take(1000))) }
}
