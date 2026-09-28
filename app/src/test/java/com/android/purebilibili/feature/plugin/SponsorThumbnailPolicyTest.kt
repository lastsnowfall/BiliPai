package com.android.purebilibili.feature.plugin

import org.junit.Assert.*
import org.junit.Test

class SponsorThumbnailPolicyTest {
    @Test fun `hash matches the extension single SHA256 prefix`() {
        assertEquals("7520", sponsorThumbnailHashPrefix("BV1bY4y1v7Mb"))
        assertEquals("e3b0", sponsorThumbnailHashPrefix(""))
    }
    @Test fun `full sponsor labels have precedence`() {
        assertEquals("整条商单", sponsorThumbnailLabel(listOf("exclusive_access", "sponsor")))
    }
    @Test fun `brand cooperation has a separate label`() {
        assertEquals("品牌合作", sponsorThumbnailLabel(listOf("exclusive_access")))
    }
    @Test fun `unrelated or missing categories do not claim sponsorship`() {
        assertNull(sponsorThumbnailLabel(emptyList()))
        assertNull(sponsorThumbnailLabel(listOf("intro", "outro", "unknown")))
    }
}
