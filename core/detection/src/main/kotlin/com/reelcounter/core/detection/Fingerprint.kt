package com.reelcounter.core.detection

/**
 * Builds a short hash of the reel on screen (author, caption) so two reels can be told apart.
 * The text is read, hashed and dropped in one pass; only the 64-bit hash lives in memory.
 */
internal object Fingerprint {
    private const val FNV_OFFSET = -0x340d631b7bdddcdbL
    private const val FNV_PRIME = 0x100000001b3L

    fun of(root: UiNode, viewIds: List<String>, screen: ScreenSize): Long? {
        if (viewIds.isEmpty()) return null
        var hash = FNV_OFFSET
        var found = false
        for (id in viewIds) {
            // Pagers keep neighbouring pages attached, so pick the copy that is actually on screen.
            val node = root.findByViewId(id)
                .filter { it.isVisibleToUser }
                .maxByOrNull { screen.visibleArea(it.bounds) }
                ?: continue
            val value = node.text?.takeIf { it.isNotBlank() }
                ?: node.contentDescription?.takeIf { it.isNotBlank() }
                ?: continue
            found = true
            for (ch in value) {
                hash = (hash xor ch.code.toLong()) * FNV_PRIME
            }
            hash = (hash xor SEPARATOR) * FNV_PRIME
        }
        return if (found) hash else null
    }

    private const val SEPARATOR = 0x1FL
}
