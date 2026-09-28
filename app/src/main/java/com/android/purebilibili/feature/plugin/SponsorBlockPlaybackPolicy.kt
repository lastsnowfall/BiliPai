package com.android.purebilibili.feature.plugin

import com.android.purebilibili.data.model.response.SponsorActionType
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorSegment

/** A community label is evidence of a promotion, never proof that an unlabelled video is ad-free. */
enum class SponsorVideoNotice(val title: String) {
    FULL_SPONSOR("商单提示 · 社区标记为整条赞助视频"),
    BRAND_COOPERATION("商单提示 · 社区标记为品牌合作 / 抢先体验"),
    SPONSOR_SEGMENTS("广告提示 · 社区标记了广告片段")
}

internal fun resolveSponsorVideoNotice(segments: List<SponsorSegment>): SponsorVideoNotice? = when {
    segments.any { it.category == SponsorCategory.SPONSOR && it.actionType == SponsorActionType.FULL } ->
        SponsorVideoNotice.FULL_SPONSOR
    segments.any { it.category == SponsorCategory.EXCLUSIVE_ACCESS && it.actionType == SponsorActionType.FULL } ->
        SponsorVideoNotice.BRAND_COOPERATION
    segments.any { it.category == SponsorCategory.SPONSOR && it.hasValidPlaybackRange() &&
        (it.isSkipType || it.isMuteType) } -> SponsorVideoNotice.SPONSOR_SEGMENTS
    else -> null
}

internal fun SponsorSegment.hasValidPlaybackRange(): Boolean =
    segment.size == 2 && startTime.isFinite() && endTime.isFinite() &&
        startTime >= 0f && endTime > startTime && endTimeMs > startTimeMs

/** Preserve every distinct ad break, including overlapping submissions; only deduplicate exact ranges. */
internal fun normalizeSponsorSegments(segments: List<SponsorSegment>): List<SponsorSegment> =
    segments.asSequence()
        .filter { it.UUID.isNotBlank() && it.hasValidPlaybackRange() && (it.isSkipType || it.isMuteType) }
        .groupBy { listOf(it.category, it.actionType, it.startTimeMs, it.endTimeMs) }
        .values
        .mapNotNull { candidates ->
            candidates.maxWithOrNull(compareBy<SponsorSegment> { it.locked }.thenBy { it.votes })
        }
        .sortedWith(compareBy<SponsorSegment> { it.startTimeMs }.thenBy { it.endTimeMs })

/** Use half-open intervals: an ad ending at the current position must not seek backwards. */
internal fun findSponsorSegmentAtPosition(
    segments: List<SponsorSegment>,
    skippedIds: Set<String>,
    positionMs: Long
): SponsorSegment? = segments.firstOrNull {
    it.UUID !in skippedIds && positionMs >= it.startTimeMs && positionMs < it.endTimeMs
}

/** Media3 may briefly report the old position after seeking; do not apply a shorter overlap. */
internal fun shouldWaitForSponsorSeek(positionMs: Long, targetMs: Long?, elapsedMs: Long): Boolean =
    targetMs != null && positionMs < targetMs && elapsedMs in 0L until 3_000L

/** Accessed on the player/main dispatcher. A slow response cannot overwrite a newer video. */
internal class SponsorBlockLoadSession {
    private var generation = 0L
    private var loadedVideo: Pair<String, Long>? = null

    fun begin(): Long {
        loadedVideo = null
        return ++generation
    }

    fun isCurrent(token: Long): Boolean = token == generation

    fun complete(token: Long, bvid: String, cid: Long): Boolean {
        if (!isCurrent(token)) return false
        loadedVideo = bvid to cid
        return true
    }

    fun isLoadedFor(bvid: String, cid: Long): Boolean = loadedVideo == (bvid to cid)
}
