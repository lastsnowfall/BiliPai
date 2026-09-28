package com.android.purebilibili.feature.video.viewmodel

import com.android.purebilibili.data.model.response.SponsorCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SponsorBlockSeekPolicyTest {
    @Test fun automaticSkipPreservesPauseAndPlayIntent() {
        assertFalse(shouldResumePlaybackAfterSponsorBlockSkip(false))
        assertTrue(shouldResumePlaybackAfterSponsorBlockSkip(true))
    }

    @Test fun outOfBoundsAdCannotEndPlayback() {
        assertEquals(119_000, resolveSponsorBlockSkipTargetPositionMs(125_000, 120_000, SponsorCategory.SPONSOR))
    }

    @Test fun validAdUsesItsActualEnd() {
        assertEquals(25_000, resolveSponsorBlockSkipTargetPositionMs(25_000, 120_000, SponsorCategory.SPONSOR))
    }

    @Test fun negativeSeekAndVeryShortVideosStayNonNegative() {
        assertEquals(0, resolveSponsorBlockSkipTargetPositionMs(-100, 120_000, SponsorCategory.SPONSOR))
        assertEquals(0, resolveSponsorBlockSkipTargetPositionMs(600, 500, SponsorCategory.SPONSOR))
    }

    @Test fun unknownDurationDoesNotDiscardValidAdEnd() {
        assertEquals(25_000, resolveSponsorBlockSkipTargetPositionMs(25_000, 0, SponsorCategory.SPONSOR))
    }
}
