package com.alertapp.pp

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class CheckWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        Checker.run(applicationContext, deep = false)
        return Result.success()
    }
}

object Scheduler {
    private const val NAME = "alerta_pp_check"

    fun schedule(ctx: Context, replace: Boolean) {
        val minutes = maxOf(15, Prefs(ctx).intervalMinutes).toLong()
        val request = PeriodicWorkRequest.Builder(CheckWorker::class.java, minutes, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx.applicationContext).enqueueUniquePeriodicWork(
            NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
