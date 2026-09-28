package com.android.purebilibili.feature.priority

import android.content.Context
import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.core.network.WbiUtils
import com.android.purebilibili.data.model.response.Owner
import com.android.purebilibili.data.model.response.Stat
import com.android.purebilibili.data.model.response.VideoItem
import com.android.purebilibili.data.model.response.VideoRights
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull

object PriorityCreatorRepository {
    private val refreshMutex = Mutex()
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    /** Foreground and scheduled refresh share one gate and bounded concurrent requests. */
    suspend fun refresh(context: Context): Boolean {
        if (!refreshMutex.tryLock()) return false
        try {
            val state = PriorityCreatorStore.read(context)
            val now = System.currentTimeMillis()
            val due = state.creators.filter { now - (state.lastCheckedMs[it.mid.toString()] ?: 0) >= CACHE_TTL_MS }
            if (due.isEmpty()) return true
            val keys = withTimeoutOrNull(10_000) {
                NetworkModule.api.getNavInfo().data?.wbi_img?.let {
                    it.img_url.substringAfterLast('/').substringBefore('.') to
                        it.sub_url.substringAfterLast('/').substringBefore('.')
                }
            } ?: return false
            val semaphore = Semaphore(3)
            return supervisorScope {
                due.map { creator -> async {
                    semaphore.withPermit {
                        try {
                            withTimeoutOrNull(10_000) {
                                val params = WbiUtils.sign(mapOf(
                                    "mid" to creator.mid.toString(), "pn" to "1", "ps" to "20", "order" to "pubdate"
                                ), keys.first, keys.second)
                                val response = NetworkModule.spaceApi.getSpaceVideos(params)
                                val data = response.data
                                if (response.code != 0 || data == null) return@withTimeoutOrNull false
                                val videos = data.list.vlist.map { item ->
                                    VideoItem(
                                        id = item.aid, aid = item.aid, bvid = item.bvid, title = item.title,
                                        pic = item.pic, pubdate = item.created, tid = item.typeid, tname = item.typename,
                                        owner = Owner(mid = creator.mid, name = creator.name, face = creator.face),
                                        stat = Stat(view = item.play, reply = item.comment),
                                        duration = item.length.split(':').fold(0) { seconds, part -> seconds * 60 + (part.toIntOrNull() ?: 0) },
                                        rights = VideoRights(
                                            pay = if (item.isChargingArc) 1 else 0,
                                            ugcPay = if (item.isUgcpay) 1 else item.ugcPay,
                                            ugcPayPreview = item.ugcPayPreview,
                                        ),
                                    )
                                }
                                PriorityCreatorStore.recordVideos(context, creator, videos)
                                true
                            } ?: false
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            false // Keep cached videos on network/server errors.
                        }
                    }
                } }.awaitAll().all { it }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return false
        } finally {
            refreshMutex.unlock()
        }
    }
}
