package net.wingress.mobivious.ui

import kotlin.math.roundToInt

/** Row-major, spatial colors. No images or decoder objects escape the sampler. */
internal data class AmbientPalette(val colors: List<Int>) {
    init { require(colors.size == 9) }

    companion object {
        fun fromPixels(pixels: IntArray, width: Int, height: Int): AmbientPalette {
            require(width >= 3 && height >= 3 && pixels.size == width * height)
            return AmbientPalette(List(9) { zone ->
                val column = zone % 3; val row = zone / 3
                var red = 0L; var green = 0L; var blue = 0L; var count = 0
                for (y in row * height / 3 until (row + 1) * height / 3) {
                    for (x in column * width / 3 until (column + 1) * width / 3) {
                        val pixel = pixels[y * width + x]
                        red += (pixel ushr 16) and 255; green += (pixel ushr 8) and 255; blue += pixel and 255
                        count++
                    }
                }
                val r = red.toFloat() / count; val g = green.toFloat() / count; val b = blue.toFloat() / count
                val luma = .2126f * r + .7152f * g + .0722f * b
                fun vivid(channel: Float) = (luma + (channel - luma) * 1.35f).roundToInt().coerceIn(0, 255)
                (255 shl 24) or (vivid(r) shl 16) or (vivid(g) shl 8) or vivid(b)
            })
        }
    }
}

/** A canceled copy still owns its bitmap until its callback arrives. */
internal class AmbientCaptureGate {
    private var generation = 0L
    private var lastRequest: Long? = null
    private var retryAt = 0L
    var inFlight = false; private set
    var closed = false; private set

    fun begin(now: Long): Long? {
        if (closed || inFlight || now < retryAt || lastRequest?.let { now - it < 250 } == true) return null
        inFlight = true; lastRequest = now
        return generation
    }

    fun finish(token: Long, now: Long, success: Boolean): Boolean {
        inFlight = false
        val current = !closed && token == generation
        if (current) retryAt = if (success) 0 else now + 1_000
        return current && success
    }

    fun invalidate() { generation++; retryAt = 0 }
    fun close() { closed = true; invalidate() }
}
