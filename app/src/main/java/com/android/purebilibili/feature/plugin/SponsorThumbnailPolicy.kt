package com.android.purebilibili.feature.plugin

import java.security.MessageDigest

/** The extension hashes the BV ID once, using the first four lowercase SHA-256 hex digits. */
internal fun sponsorThumbnailHashPrefix(bvid: String): String =
    MessageDigest.getInstance("SHA-256").digest(bvid.toByteArray(Charsets.UTF_8))
        .take(2).joinToString("") { "%02x".format(it.toInt() and 0xff) }

internal fun sponsorThumbnailLabel(categories: List<String>): String? = when {
    "sponsor" in categories -> "整条商单"
    "exclusive_access" in categories -> "品牌合作"
    else -> null
}
