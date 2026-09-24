package com.alertapp.pp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

object Notifier {
    private const val CHANNEL_ID = "promocoes"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    fun notify(ctx: Context, items: List<Pair<Article, String>>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        for ((article, keyword) in items.take(10)) {
            val id = article.link.hashCode()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.link))
            val pi = PendingIntent.getActivity(
                ctx, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(ctx.getString(R.string.notif_title, keyword))
                .setContentText(article.title)
                .setStyle(Notification.BigTextStyle().bigText(article.title))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            nm.notify(id, n)
        }
    }
}
