package com.android.purebilibili.core.store

const val PRIORITY_CREATOR_PERIOD = 20
const val PRIORITY_CREATOR_SLOT = 3

fun isPriorityCreatorSlot(index: Int): Boolean = index >= 0 && index % PRIORITY_CREATOR_PERIOD == PRIORITY_CREATOR_SLOT

/** Only the fourth slot of each twenty may be injected; ordinary recommendations retain their order. */
fun <T> mixPriorityCreatorVideos(
    recommended: List<T>,
    creatorVideos: List<T>,
    selectedMids: Set<Long>,
    limit: Int,
    key: (T) -> String,
    owner: (T) -> Long,
    startIndex: Int = 0,
    markPriority: (T) -> T = { it },
): List<T> {
    if (limit <= 0) return emptyList()
    val ordinary = ArrayDeque(recommended.distinctBy(key))
    if (selectedMids.isEmpty()) return ordinary.take(limit)
    val groups = creatorVideos.distinctBy(key).filter { owner(it) in selectedMids }
        .groupBy(owner).values.map { ArrayDeque(it) }
    val preferred = ArrayDeque<T>()
    val rotation = if (groups.isEmpty()) 0 else ((startIndex + 16) / PRIORITY_CREATOR_PERIOD) % groups.size
    while (groups.any { it.isNotEmpty() }) {
        groups.indices.forEach { index ->
            val group = groups[(index + rotation) % groups.size]
            if (group.isNotEmpty()) preferred.addLast(group.removeFirst())
        }
    }
    val result = mutableListOf<T>()
    val used = mutableSetOf<String>()
    while (result.size < limit) {
        while (ordinary.isNotEmpty() && key(ordinary.first()) in used) ordinary.removeFirst()
        while (preferred.isNotEmpty() && key(preferred.first()) in used) preferred.removeFirst()
        val next = when {
            isPriorityCreatorSlot(startIndex + result.size) && preferred.isNotEmpty() -> markPriority(preferred.removeFirst())
            ordinary.isNotEmpty() -> ordinary.removeFirst()
            else -> break
        }
        used += key(next)
        result += next
    }
    return result
}

fun isNearlyFinishedVideo(positionMs: Long, durationMs: Long): Boolean =
    durationMs > 0 && positionMs >= 0 && positionMs.toDouble() / durationMs >= 0.9

/** Do not announce an UP's back catalogue when they are first selected. */
fun isNewPriorityCreatorVideo(
    bvid: String,
    publishedAtSeconds: Long,
    selectedAtSeconds: Long,
    knownIds: Set<String>,
): Boolean = bvid.isNotBlank() && publishedAtSeconds > selectedAtSeconds && bvid !in knownIds
