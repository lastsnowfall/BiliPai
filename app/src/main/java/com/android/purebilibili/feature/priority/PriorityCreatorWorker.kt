package com.android.purebilibili.feature.priority

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.android.purebilibili.EXTRA_PENDING_NAVIGATION_ROUTE
import com.android.purebilibili.MainActivity
import com.android.purebilibili.R
import com.android.purebilibili.data.repository.BlockedUpRepository
import com.android.purebilibili.navigation.ScreenRoutes
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PriorityCreatorWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        if (PriorityCreatorStore.read(applicationContext).creators.isEmpty()) {
            Result.success()
        } else {
            val refreshed = PriorityCreatorRepository.refresh(applicationContext)
            PriorityCreatorNotifications.postPending(applicationContext)
            if (refreshed) Result.success() else Result.retry()
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "priority_creator_updates"

        suspend fun syncSchedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            if (PriorityCreatorStore.read(context).creators.isEmpty()) {
                manager.cancelUniqueWork(WORK_NAME)
            } else {
                val request = PeriodicWorkRequestBuilder<PriorityCreatorWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
            }
        }
    }
}

object PriorityCreatorNotifications {
    const val CHANNEL_ID = "priority_creator_updates"
    private val mutex = Mutex()

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "重点关注 UP 更新", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "重点关注的 UP 发布新视频时提醒"
            }
        )
    }

    fun canPost(context: Context): Boolean {
        ensureChannel(context)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java)
                .getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    suspend fun postPending(context: Context) = mutex.withLock {
        if (!canPost(context)) return@withLock
        val state = PriorityCreatorStore.read(context)
        val blocked = BlockedUpRepository(context).getAllBlockedUps().first().map { it.mid }.toSet()
        val enabled = state.creators.filter { it.notifyNewVideos && it.mid !in blocked }.map { it.mid }.toSet()
        val pending = state.unseenVideos.filter { it.owner.mid in enabled && it.bvid !in state.notifiedIds }
        // One notification per UP aggregates a burst without flooding the notification shade.
        for ((mid, videos) in pending.groupBy { it.owner.mid }) {
            val latest = PriorityCreatorStore.read(context)
            if (latest.creators.none { it.mid == mid && it.notifyNewVideos }) continue
            val current = videos.filter { video -> latest.unseenVideos.any { it.bvid == video.bvid } }
            val video = current.maxByOrNull { it.pubdate } ?: continue
            val tag = "priority-creator:$mid"
            val intent = Intent(context, MainActivity::class.java).apply {
                data = Uri.Builder().scheme("bilipai-notification").authority("priority")
                    .appendPath(video.bvid).build()
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_PENDING_NAVIGATION_ROUTE, ScreenRoutes.VideoPlayer.createRoute(video.bvid))
            }
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("重点关注 · ${video.owner.name} 更新了${current.size}个视频")
                .setContentText(video.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(current.joinToString("\n") { it.title }))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SOCIAL)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(context, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .build()
            try {
                NotificationManagerCompat.from(context).notify(tag, 1, notification)
                PriorityCreatorStore.markNotified(context, current.map { it.bvid })
            } catch (_: SecurityException) {
                // Keep unread items when permission is revoked between checking and posting.
            }
        }
    }
}
