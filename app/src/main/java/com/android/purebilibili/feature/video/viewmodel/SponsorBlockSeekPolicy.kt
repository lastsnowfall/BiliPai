package com.android.purebilibili.feature.video.viewmodel

internal fun shouldResumePlaybackAfterSponsorBlockSkip(
    playWhenReadyBeforeSkip: Boolean
): Boolean {
    // A background poll or seek into an ad must preserve the user's pause state.
    return playWhenReadyBeforeSkip
}

private const val SPONSOR_SKIP_END_GUARD_MS = 1_000L

internal fun resolveSponsorBlockSkipTargetPositionMs(
    requestedPositionMs: Long,
    durationMs: Long,
    category: String?,
): Long {
    val safeRequestedPositionMs = requestedPositionMs.coerceAtLeast(0L)
    if (durationMs <= 0L) return safeRequestedPositionMs

    // 片尾跳过允许自然进入播放完成；其他社区片段不能因时间轴误差 seek 到
    // duration 或其后，否则部分 Media3/解码器组合会直接进入 STATE_ENDED。
    if (category == com.android.purebilibili.data.model.response.SponsorCategory.OUTRO) {
        return safeRequestedPositionMs.coerceAtMost(durationMs)
    }
    val latestContinuousPlaybackPositionMs =
        (durationMs - SPONSOR_SKIP_END_GUARD_MS).coerceAtLeast(0L)
    return safeRequestedPositionMs.coerceAtMost(latestContinuousPlaybackPositionMs)
}
