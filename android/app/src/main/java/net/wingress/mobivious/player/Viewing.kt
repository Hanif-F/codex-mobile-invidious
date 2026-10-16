package net.wingress.mobivious.player

import net.wingress.mobivious.data.ApiContext
import kotlin.math.*

enum class Projection { FLAT, EQUIRECTANGULAR, EAC }
enum class ViewingMode(val label: String) {
    AUTO("Automatic"), FLAT("Flat"), EQUIRECTANGULAR("360° equirectangular"), EAC("360° EAC")
}
data class ViewingState(val context: ApiContext? = null, val occurrence: String? = null,
    val mode: ViewingMode = ViewingMode.AUTO, val yaw: Float = 0f, val pitch: Float = 0f,
    val motion: Boolean = false, val annotations: Boolean? = null, val error: String? = null) {
    fun projection(metadata: String?, automatic: Boolean): Projection = if (error != null) Projection.FLAT else when (mode) {
        ViewingMode.FLAT -> Projection.FLAT
        ViewingMode.EQUIRECTANGULAR -> Projection.EQUIRECTANGULAR
        ViewingMode.EAC -> Projection.EAC
        ViewingMode.AUTO -> if (!automatic) Projection.FLAT else when (metadata) {
            "EQUIRECTANGULAR" -> Projection.EQUIRECTANGULAR; "MESH" -> Projection.EAC; else -> Projection.FLAT
        }
    }
    fun pan(yawDelta: Float, pitchDelta: Float): ViewingState =
        if (!yawDelta.isFinite() || !pitchDelta.isFinite()) this else copy(
            yaw = ((yaw + yawDelta + 180) % 360 + 360) % 360 - 180,
            pitch = (pitch + pitchDelta).coerceIn(-85f, 85f))
}

/** CPU counterpart of the shader mapping, used to verify archived cubemap layouts with fixtures. */
object ProjectionMath {
    /** SensorManager's row-major device-to-world matrix; the camera looks through the back of the phone. */
    fun motionAngles(matrix: FloatArray): Pair<Float, Float> {
        require(matrix.size == 9 && matrix.all { it.isFinite() })
        val horizontal = hypot(matrix[2], matrix[5])
        val yaw = if (horizontal < .001f) 0f else Math.toDegrees(atan2(-matrix[2], -matrix[5]).toDouble()).toFloat()
        val pitch = Math.toDegrees(asin((-matrix[8]).coerceIn(-1f, 1f)).toDouble()).toFloat()
        return (if (abs(yaw) < .0001f) 0f else yaw) to (if (abs(pitch) < .0001f) 0f else pitch)
    }
    fun uv(projection: Projection, x: Double, y: Double, z: Double): Pair<Double, Double> {
        val length = sqrt(x*x + y*y + z*z)
        require(length.isFinite() && length > 0)
        if (projection == Projection.EQUIRECTANGULAR)
            return (atan2(x, -z) / (2 * PI) + .5) to (asin((y / length).coerceIn(-1.0, 1.0)) / PI + .5)
        // YouTube's 3 × 2 EAC atlas: left/front/right above bottom/back/top (the lower faces are rotated).
        val ax = abs(x); val ay = abs(y); val az = abs(z)
        val face: Int; val a: Double; val b: Double
        if (az >= ax && az >= ay) {
            if (z < 0) { face = 1; a = x / az; b = y / az }
            else { face = 4; a = y / az; b = x / az }
        } else if (ax >= ay) {
            if (x > 0) { face = 2; a = z / ax; b = y / ax }
            else { face = 0; a = -z / ax; b = y / ax }
        } else {
            if (y < 0) { face = 3; a = z / ay; b = x / ay }
            else { face = 5; a = -z / ay; b = x / ay }
        }
        return ((face % 3 + atan(a) * 2 / PI + .5) / 3) to
            ((if (face < 3) 1 else 0) + atan(b) * 2 / PI + .5) / 2
    }
}
