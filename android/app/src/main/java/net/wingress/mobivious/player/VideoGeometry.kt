package net.wingress.mobivious.player

/** Geometry is bound to one media item; decoder dimensions already include rotation. */
data class VideoGeometry(val mediaId: String = "", val width: Int = 0, val height: Int = 0, val pixelRatio: Float = 1f) {
    val ratio: Float? get() = (width.toDouble() * pixelRatio / height).toFloat()
        .takeIf { width > 0 && height > 0 && pixelRatio.isFinite() && pixelRatio > 0 && it.isFinite() && it > 0 }
    val layoutRatio: Float get() = ratio ?: 16f / 9f
    val pipRatio: Float get() = layoutRatio.coerceIn(1f / 2.39f, 2.39f)
    val pipFraction: Pair<Int, Int> get() = when {
        pipRatio <= 1f / 2.39f -> 100 to 239
        pipRatio >= 2.39f -> 239 to 100
        else -> kotlin.math.round(pipRatio * 100000).toInt().coerceIn(41842, 239000) to 100000
    }
    fun embeddedHeight(width: Float, availableHeight: Float, comments: Boolean): Float =
        minOf(width.coerceAtLeast(0f) / layoutRatio, availableHeight.coerceAtLeast(0f) * if (comments) .4f else .7f)
    val orientation: Int get() = ratio?.let {
        when { it < .99f -> 1; it > 1.01f -> -1; else -> 0 }
    } ?: 0
}
