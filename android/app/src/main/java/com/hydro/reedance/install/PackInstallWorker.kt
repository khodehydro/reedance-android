package com.hydro.reedance.install

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.CancellationException

/** Persistent restart-from-zero work: never resumes an unauthenticated partial file. */
class PackInstallWorker(context: Context, params: WorkerParameters): CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        val id = inputData.getString("songId") ?: error("Missing song identity")
        PackInstaller(applicationContext).install(id) { phase, bytes ->
            setProgress(workDataOf("phase" to phase, "bytes" to bytes))
        }
        Result.success()
    } catch(cancelled: CancellationException) { throw cancelled } catch(error: Exception) { Result.failure(workDataOf("error" to (error.message ?: "Installation failed").take(1000))) }
}
