package net.wingress.mobivious.player

/** Distances are in dp; positive scroll moves toward later watch details. */
internal object WatchPlayerScroll {
    const val TOP_REGION = 24f
    const val RESIZE_DISTANCE = 96f

    /** null topDistance means the first details item has already scrolled out of view. */
    fun consume(delta: Float, resized: Float, topDistance: Float?): Float = when {
        delta > 0f -> {
            // Once resizing begins, viewport remeasurement must not reintroduce the top region.
            val beforeResize = if (resized > 0f || topDistance == null) 0f
                else (TOP_REGION - topDistance).coerceAtLeast(0f)
            (delta - beforeResize).coerceAtLeast(0f).coerceAtMost(RESIZE_DISTANCE - resized)
        }
        delta < 0f && topDistance != null -> {
            val beforeExpand = (topDistance - TOP_REGION).coerceAtLeast(0f)
            -(-delta - beforeExpand).coerceAtLeast(0f).coerceAtMost(resized)
        }
        else -> 0f
    }
}
