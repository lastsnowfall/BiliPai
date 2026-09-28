package com.android.purebilibili.feature.priority

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.android.purebilibili.core.store.isNewPriorityCreatorVideo
import com.android.purebilibili.data.model.response.VideoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.priorityCreatorDataStore by preferencesDataStore("priority_creators")

@Serializable
data class PriorityCreator(
    val mid: Long,
    val name: String,
    val face: String = "",
    val selectedAtSeconds: Long,
    val notifyNewVideos: Boolean = true,
)

@Serializable
data class PriorityCreatorState(
    val creators: List<PriorityCreator> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
    val knownIds: Map<String, List<String>> = emptyMap(),
    val lastCheckedMs: Map<String, Long> = emptyMap(),
    val unseenVideos: List<VideoItem> = emptyList(),
    val notifiedIds: List<String> = emptyList(),
)

/** Device-local preferences; changing these does not change Bilibili's server-side follow groups. */
object PriorityCreatorStore {
    private val key = stringPreferencesKey("state")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private fun decode(raw: String?): PriorityCreatorState = raw?.let {
        runCatching { json.decodeFromString<PriorityCreatorState>(it) }.getOrNull()
    } ?: PriorityCreatorState()

    fun observe(context: Context): Flow<PriorityCreatorState> =
        context.applicationContext.priorityCreatorDataStore.data.map { decode(it[key]) }

    suspend fun read(context: Context): PriorityCreatorState = observe(context).first()

    private suspend fun update(context: Context, transform: (PriorityCreatorState) -> PriorityCreatorState) {
        context.applicationContext.priorityCreatorDataStore.edit { prefs ->
            val before = decode(prefs[key])
            val after = transform(before)
            if (before != after) prefs[key] = json.encodeToString(after)
        }
    }

    suspend fun select(context: Context, mid: Long, name: String, face: String) {
        require(mid > 0)
        update(context) { state ->
            if (state.creators.any { it.mid == mid }) state else state.copy(
                creators = state.creators + PriorityCreator(mid, name, face, System.currentTimeMillis() / 1000)
            )
        }
    }

    suspend fun remove(context: Context, mid: Long) = update(context) { state ->
        state.copy(
            creators = state.creators.filterNot { it.mid == mid },
            videos = state.videos.filterNot { it.owner.mid == mid },
            unseenVideos = state.unseenVideos.filterNot { it.owner.mid == mid },
            knownIds = state.knownIds - mid.toString(),
            lastCheckedMs = state.lastCheckedMs - mid.toString(),
        )
    }

    suspend fun setNotify(context: Context, mid: Long, enabled: Boolean) = update(context) { state ->
        state.copy(creators = state.creators.map { if (it.mid == mid) it.copy(notifyNewVideos = enabled) else it })
    }

    suspend fun recordVideos(context: Context, selection: PriorityCreator, videos: List<VideoItem>) = update(context) { state ->
        // A removed/re-added creator is a new subscription; discard the old in-flight result.
        val creator = state.creators.firstOrNull { it.mid == selection.mid && it.selectedAtSeconds == selection.selectedAtSeconds }
            ?: return@update state
        val ownerKey = creator.mid.toString()
        val known = state.knownIds[ownerKey].orEmpty().toSet()
        val valid = videos.filter { it.bvid.isNotBlank() && it.owner.mid == creator.mid }.distinctBy { it.bvid }
        val fresh = valid.filter { isNewPriorityCreatorVideo(it.bvid, it.pubdate, creator.selectedAtSeconds, known) }
        state.copy(
            videos = (state.videos.filterNot { it.owner.mid == creator.mid } + valid.take(20)),
            knownIds = state.knownIds + (ownerKey to (valid.map { it.bvid } + known).distinct().take(200)),
            lastCheckedMs = state.lastCheckedMs + (ownerKey to System.currentTimeMillis()),
            unseenVideos = (fresh + state.unseenVideos).distinctBy { it.bvid }.sortedByDescending { it.pubdate }.take(100),
        )
    }

    suspend fun markRead(context: Context, bvid: String) = update(context) { state ->
        state.copy(unseenVideos = state.unseenVideos.filterNot { it.bvid == bvid })
    }

    suspend fun markNotified(context: Context, ids: List<String>) = update(context) { state ->
        state.copy(notifiedIds = (ids + state.notifiedIds).distinct().take(300))
    }
}
