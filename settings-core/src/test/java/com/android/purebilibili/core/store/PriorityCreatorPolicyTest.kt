package com.android.purebilibili.core.store

import org.junit.Assert.*
import org.junit.Test

class PriorityCreatorPolicyTest {
    private data class Video(val id: String, val mid: Long, val marked: Boolean = false)
    private val normal = (1..100).map { Video("normal$it", 99) }
    private val preferred = (1..20).map { Video("preferred$it", 1) }
    private fun mix(
        recommended: List<Video> = normal,
        candidates: List<Video> = preferred,
        selected: Set<Long> = setOf(1),
        limit: Int = 20,
        start: Int = 0,
    ) = mixPriorityCreatorVideos(recommended, candidates, selected, limit, { it.id }, { it.mid }, start) { it.copy(marked = true) }

    @Test fun `only fourth of each twenty is injected`() {
        val result = mix(limit = 60)
        assertEquals(60, result.size)
        assertEquals(listOf(3, 23, 43), result.indices.filter { result[it].marked })
    }
    @Test fun `ordinary recommendation order is preserved`() {
        assertEquals(normal.take(57), mix(limit = 60).filterNot { it.marked })
    }
    @Test fun `pagination continues global positions instead of restarting`() {
        assertEquals(listOf(13, 33), mix(limit = 40, start = 10).indices.filter { mix(limit = 40, start = 10)[it].marked })
        assertTrue(mix(limit = 1, start = 23).single().marked)
        assertFalse(mix(limit = 1, start = 24).single().marked)
    }
    @Test fun `all page boundaries use the fourth global slot`() {
        for (start in 0..80) {
            val result = mix(limit = 20, start = start)
            assertEquals((0..19).filter { (start + it) % 20 == 3 }, result.indices.filter { result[it].marked })
        }
    }
    @Test fun `no selected creators preserves original feed order`() {
        assertEquals(normal.take(20), mix(selected = emptySet()))
    }
    @Test fun `duplicates across both pools consume one slot only`() {
        val result = mix(recommended = preferred.take(4) + normal + normal, candidates = preferred + preferred)
        assertEquals(result.size, result.map { it.id }.distinct().size)
        assertEquals(listOf(3), result.indices.filter { result[it].marked })
    }
    @Test fun `unavailable creator slot falls back without a highlight`() {
        assertEquals(normal.take(20), mix(candidates = emptyList()))
    }
    @Test fun `creators never fill ordinary slots when ordinary pool is empty`() {
        assertTrue(mix(recommended = emptyList()).isEmpty())
        assertEquals(1, mix(recommended = emptyList(), start = 3).size)
    }
    @Test fun `multiple creators rotate across fixed slots`() {
        val other = preferred.map { it.copy(id = "other" + it.id, mid = 2) }
        assertEquals(listOf(1L, 2L, 1L), mix(candidates = preferred + other, selected = setOf(1, 2), limit = 60).filter { it.marked }.map { it.mid })
        assertEquals(2L, mix(candidates = preferred + other, selected = setOf(1, 2), start = 20).first { it.marked }.mid)
    }
    @Test fun `unselected cached creators cannot be injected`() {
        assertEquals(normal.take(20), mix(candidates = preferred.map { it.copy(mid = 2) }))
    }
    @Test fun `naturally recommended favorite creators remain in normal slots`() {
        val input = normal.map { it.copy(mid = 1) }
        assertEquals(input.take(19), mix(recommended = input).filterNot { it.marked })
    }
    @Test fun `zero and negative limits return empty`() {
        assertTrue(mix(limit = 0).isEmpty())
        assertTrue(mix(limit = -1).isEmpty())
        assertFalse(isPriorityCreatorSlot(-17))
    }
    @Test fun `ninety percent is the exact finished threshold`() {
        assertFalse(isNearlyFinishedVideo(89999, 100000))
        assertTrue(isNearlyFinishedVideo(90000, 100000))
        assertTrue(isNearlyFinishedVideo(100000, 100000))
    }
    @Test fun `unknown duration and invalid position are not finished`() {
        assertFalse(isNearlyFinishedVideo(100, 0))
        assertFalse(isNearlyFinishedVideo(100, -1))
        assertFalse(isNearlyFinishedVideo(-1, 100))
    }
    @Test fun `large durations do not overflow`() {
        assertTrue(isNearlyFinishedVideo(Long.MAX_VALUE, Long.MAX_VALUE))
        assertFalse(isNearlyFinishedVideo(Long.MAX_VALUE / 2, Long.MAX_VALUE))
    }
    @Test fun `old uploads and known IDs do not cause alerts`() {
        assertFalse(isNewPriorityCreatorVideo("BVold", 99, 100, emptySet()))
        assertFalse(isNewPriorityCreatorVideo("BVsame", 100, 100, emptySet()))
        assertFalse(isNewPriorityCreatorVideo("BVseen", 101, 100, setOf("BVseen")))
        assertFalse(isNewPriorityCreatorVideo("", 101, 100, emptySet()))
        assertTrue(isNewPriorityCreatorVideo("BVnew", 101, 100, emptySet()))
    }
}
