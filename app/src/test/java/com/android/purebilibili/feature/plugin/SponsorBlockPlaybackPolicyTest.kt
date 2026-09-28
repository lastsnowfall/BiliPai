package com.android.purebilibili.feature.plugin

import com.android.purebilibili.data.model.response.SponsorActionType
import com.android.purebilibili.data.model.response.SponsorCategory
import com.android.purebilibili.data.model.response.SponsorSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SponsorBlockPlaybackPolicyTest {
    private fun segment(
        id: String = "ad",
        start: Float = 10f,
        end: Float = 20f,
        category: String = SponsorCategory.SPONSOR,
        action: String = SponsorActionType.SKIP,
        votes: Int = 0,
        locked: Int = 0,
    ) = SponsorSegment(listOf(start, end), id, category, action, locked, votes)

    @Test fun fullVideoLabelWithZeroRangeIsShownButNeverSkipped() {
        val data = listOf(segment(start = 0f, end = 0f, action = SponsorActionType.FULL))
        assertEquals(SponsorVideoNotice.FULL_SPONSOR, resolveSponsorVideoNotice(data))
        assertTrue(normalizeSponsorSegments(data).isEmpty())
    }

    @Test fun fullVideoLabelWithDurationNeverSkipsTheEntireVideo() {
        val data = listOf(segment(start = 0f, end = 600f, action = SponsorActionType.FULL))
        assertEquals(SponsorVideoNotice.FULL_SPONSOR, resolveSponsorVideoNotice(data))
        assertTrue(normalizeSponsorSegments(data).isEmpty())
    }

    @Test fun brandCooperationIsASeparateNotice() {
        val data = listOf(segment(category = SponsorCategory.EXCLUSIVE_ACCESS, action = SponsorActionType.FULL))
        assertEquals(SponsorVideoNotice.BRAND_COOPERATION, resolveSponsorVideoNotice(data))
        assertTrue(normalizeSponsorSegments(data).isEmpty())
    }

    @Test fun absentDataDoesNotClaimTheVideoIsAdFree() {
        assertNull(resolveSponsorVideoNotice(emptyList()))
    }

    @Test fun introAndSelfPromotionDoNotLabelTheVideoAsSponsored() {
        assertNull(resolveSponsorVideoNotice(listOf(
            segment(category = SponsorCategory.INTRO),
            segment(category = SponsorCategory.SELFPROMO, action = SponsorActionType.FULL),
        )))
    }

    @Test fun regularSponsorBreakAndMuteHaveAnAdNotice() {
        assertEquals(SponsorVideoNotice.SPONSOR_SEGMENTS, resolveSponsorVideoNotice(listOf(segment())))
        assertEquals(SponsorVideoNotice.SPONSOR_SEGMENTS,
            resolveSponsorVideoNotice(listOf(segment(action = SponsorActionType.MUTE))))
    }

    @Test fun fullSponsorNoticeTakesPriorityOverBrandCooperationAndBreaks() {
        assertEquals(SponsorVideoNotice.FULL_SPONSOR, resolveSponsorVideoNotice(listOf(
            segment(),
            segment(category = SponsorCategory.EXCLUSIVE_ACCESS, action = SponsorActionType.FULL),
            segment(action = SponsorActionType.FULL),
        )))
    }

    @Test fun allDistinctAdBreaksAreKeptInTimeOrder() {
        val normalized = normalizeSponsorSegments(listOf(
            segment("last", 80f, 90f), segment("first", 0f, 10f), segment("middle", 40f, 50f)
        ))
        assertEquals(listOf("first", "middle", "last"), normalized.map { it.UUID })
    }

    @Test fun overlappingAdBreaksDoNotLoseTheirTails() {
        val normalized = normalizeSponsorSegments(listOf(segment("a", 10f, 20f), segment("b", 15f, 30f)))
        assertEquals(2, normalized.size)
        assertEquals("a", findSponsorSegmentAtPosition(normalized, emptySet(), 10_000)?.UUID)
        assertEquals("b", findSponsorSegmentAtPosition(normalized, setOf("a"), 20_000)?.UUID)
    }

    @Test fun futureSegmentCannotHideAnActiveOverlappingSegment() {
        val normalized = normalizeSponsorSegments(listOf(segment("a", 10f, 30f), segment("b", 15f, 20f)))
        assertEquals("b", findSponsorSegmentAtPosition(normalized, setOf("a"), 16_000)?.UUID)
    }

    @Test fun duplicateRangesPreferLockedThenVotes() {
        val normalized = normalizeSponsorSegments(listOf(
            segment("popular", votes = 100), segment("locked", votes = 1, locked = 1),
            segment("winner", votes = 5, locked = 1),
        ))
        assertEquals(listOf("winner"), normalized.map { it.UUID })
    }

    @Test fun invalidAndUnsupportedSegmentsCannotSeek() {
        val invalid = listOf(
            segment(start = -1f), segment(start = 30f), segment(end = 10f),
            segment(start = Float.NaN), segment(end = Float.POSITIVE_INFINITY),
            segment(action = "unknown"), segment(id = ""),
            segment(action = SponsorActionType.POI),
            segment().copy(segment = listOf(10f)),
            segment().copy(segment = listOf(10f, 20f, 30f)),
        )
        assertTrue(normalizeSponsorSegments(invalid).isEmpty())
    }

    @Test fun boundariesAreStartInclusiveAndEndExclusive() {
        val data = listOf(segment())
        assertNull(findSponsorSegmentAtPosition(data, emptySet(), 9_999))
        assertEquals("ad", findSponsorSegmentAtPosition(data, emptySet(), 10_000)?.UUID)
        assertEquals("ad", findSponsorSegmentAtPosition(data, emptySet(), 19_999)?.UUID)
        assertNull(findSponsorSegmentAtPosition(data, emptySet(), 20_000))
        assertNull(findSponsorSegmentAtPosition(data, emptySet(), 20_001))
    }

    @Test fun skippedSegmentsAreNotRepeatedWithoutExplicitRearming() {
        assertNull(findSponsorSegmentAtPosition(listOf(segment()), setOf("ad"), 15_000))
    }

    @Test fun stalePositionAfterSeekCannotTriggerABackwardsSkip() {
        assertTrue(shouldWaitForSponsorSeek(15_000, 30_000, 500))
        assertFalse(shouldWaitForSponsorSeek(30_000, 30_000, 500))
        assertFalse(shouldWaitForSponsorSeek(15_000, 30_000, 3_000))
        assertFalse(shouldWaitForSponsorSeek(15_000, null, 500))
    }

    @Test fun delayedResponseCannotOverwriteANewerVideo() {
        val session = SponsorBlockLoadSession()
        val old = session.begin()
        val current = session.begin()
        assertTrue(session.complete(current, "BVnew", 2))
        assertFalse(session.complete(old, "BVold", 1))
        assertTrue(session.isLoadedFor("BVnew", 2))
        assertFalse(session.isLoadedFor("BVold", 1))
    }

    @Test fun switchingPartsClearsThePreviousPartImmediately() {
        val session = SponsorBlockLoadSession()
        session.complete(session.begin(), "BVsame", 1)
        val next = session.begin()
        assertFalse(session.isLoadedFor("BVsame", 1))
        assertFalse(session.isLoadedFor("BVsame", 2))
        session.complete(next, "BVsame", 2)
        assertTrue(session.isLoadedFor("BVsame", 2))
    }

    @Test fun disablingOrEndingInvalidatesAnInFlightRequest() {
        val session = SponsorBlockLoadSession()
        val pending = session.begin()
        session.begin()
        assertFalse(session.isCurrent(pending))
        assertFalse(session.complete(pending, "BVold", 1))
    }
}
