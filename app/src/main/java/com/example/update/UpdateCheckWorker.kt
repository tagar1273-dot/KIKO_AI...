package com.example.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val repo = UpdateRepository.getInstance(applicationContext)
        val snapshot = repo.settingsFlow.first()
        if (snapshot.serverUrl.isBlank()) {
            return Result.success()
        }

        val checkResult = repo.checkForUpdates(userInitiated = false)
        return if (checkResult.isSuccess) {
            Result.success()
        } else {
            Log.w(TAG, "Background update check failed, scheduling retry", checkResult.exceptionOrNull())
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "UpdateCheckWorker"
        private const val UNIQUE_WORK_NAME = "kiko_periodic_update_check"

        fun schedulePeriodicChecks(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                repeatInterval = 6,
                repeatIntervalTimeUnit = TimeUnit.HOURS,
            )
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
