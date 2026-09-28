package com.android.purebilibili.feature.priority

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.watchedVideoDataStore by preferencesDataStore("watched_video_exclusions")

/** Persist beyond player/ViewModel destruction; finished videos are not evicted from this local set. */
object WatchedVideoStore {
    private val key = stringSetPreferencesKey("nearly_finished_bvids")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    suspend fun read(context: Context): Set<String> = context.applicationContext.watchedVideoDataStore.data.first()[key].orEmpty()

    fun markFinished(context: Context, bvid: String) {
        if (bvid.isBlank()) return
        val app = context.applicationContext
        scope.launch {
            try {
                app.watchedVideoDataStore.edit { prefs ->
                    val before = prefs[key].orEmpty()
                    if (bvid !in before) prefs[key] = before + bvid
                }
                PriorityCreatorStore.markRead(app, bvid)
            } catch (e: java.io.IOException) {
                android.util.Log.w("WatchedVideoStore", "Unable to persist finished video", e)
            }
        }
    }
}
