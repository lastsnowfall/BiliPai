package com.android.purebilibili.data.repository

import com.android.purebilibili.core.network.NetworkModule
import com.android.purebilibili.feature.plugin.sponsorThumbnailHashPrefix
import com.android.purebilibili.feature.plugin.sponsorThumbnailLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** This endpoint contains full-video labels, not timed advertising segments. */
object SponsorThumbnailRepository {
    private data class Entry(val labels: Map<String, String>, val expiresAt: Long)
    private val cache = object : LinkedHashMap<String, Entry>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean = size > 128
    }
    private val locks = Array(32) { Mutex() }
    private val requests = Semaphore(3)
    private val client = buildSponsorBlockHttpClient(NetworkModule.okHttpClient).newBuilder()
        .callTimeout(8, TimeUnit.SECONDS).build()

    suspend fun label(bvid: String, baseUrl: String): String? = withContext(Dispatchers.IO) {
        if (!bvid.matches(Regex("BV[0-9A-Za-z]{10}"))) return@withContext null
        val prefix = sponsorThumbnailHashPrefix(bvid)
        val url = "${baseUrl.trimEnd('/')}/videoLabels/$prefix"
        locks[(url.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            val now = android.os.SystemClock.elapsedRealtime()
            val cached = synchronized(cache) { cache[url] }
            if (cached != null && cached.expiresAt > now) return@withLock cached.labels[bvid]
            val entry = requests.withPermit {
                try {
                    client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                        if (!response.isSuccessful && response.code != 404) error("HTTP ${response.code}")
                        val labels = if (response.code == 404) emptyMap() else {
                            Json.parseToJsonElement(response.body.string()).jsonArray.mapNotNull { item ->
                                val obj = item.jsonObject
                                val id = obj["videoID"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                                val categories = obj["segments"]?.jsonArray.orEmpty().mapNotNull {
                                    it.jsonObject["category"]?.jsonPrimitive?.contentOrNull
                                }
                                sponsorThumbnailLabel(categories)?.let { id to it }
                            }.toMap()
                        }
                        Entry(labels, now + TimeUnit.MINUTES.toMillis(15))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // A network failure is unknown, never a claim that the video has no advertising.
                    Entry(emptyMap(), now + TimeUnit.SECONDS.toMillis(30))
                }
            }
            synchronized(cache) { cache[url] = entry }
            entry.labels[bvid]
        }
    }
}
