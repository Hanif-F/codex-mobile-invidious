package net.wingress.mobivious.ui

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.*
import android.opengl.GLES11Ext
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.view.Surface
import android.widget.FrameLayout
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.session.MediaController
import androidx.media3.ui.SubtitleView
import net.wingress.mobivious.data.CaptionAppearance
import net.wingress.mobivious.player.Projection
import net.wingress.mobivious.player.ProjectionMath
import net.wingress.mobivious.player.ViewingState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/** A normal decoder surface, reprojected on the GPU; the playback service stays the sole player. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ProjectionVideoView(context: Context) : FrameLayout(context), SensorEventListener {
    private val sensors = context.getSystemService(SensorManager::class.java)
    private val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private var lastAngles: FloatArray? = null
    private var motion = false
    private var active = false
    private var resumed = false
    private var motionRequested = false
    private var awakeRequested = false
    private var released = false
    private var controller: MediaController? = null
    private var surface: Surface? = null
    var onFailure: () -> Unit = {}
    var onMotion: (Float, Float) -> Unit = { _, _ -> }
    private val subtitles = SubtitleView(context)
    private val listener = object : Player.Listener {
        override fun onCues(cueGroup: CueGroup) { subtitles.setCues(cueGroup.cues) }
    }
    private val gl = GLSurfaceView(context)
    private val renderer = SphereRenderer()
    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        gl.setEGLContextClientVersion(2)
        gl.preserveEGLContextOnPause = true
        gl.setRenderer(renderer)
        gl.renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        addView(gl, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(subtitles, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }
    fun configure(player: MediaController?, projection: Projection, view: ViewingState, appearance: CaptionAppearance,
        useMotion: Boolean, awake: Boolean) {
        if (controller !== player) {
            controller?.removeListener(listener)
            surface?.let { controller?.clearVideoSurface(it) }
            controller = player
            player?.addListener(listener)
            subtitles.setCues(player?.currentCues?.cues)
            if (active) surface?.let { player?.setVideoSurface(it) }
        }
        renderer.projection = projection
        renderer.yaw = view.yaw; renderer.pitch = view.pitch
        renderer.videoWidth = player?.videoSize?.width ?: 0
        renderer.videoHeight = player?.videoSize?.height ?: 0
        subtitles.applyCaptionAppearance(appearance)
        awakeRequested = awake
        motionRequested = useMotion
        keepScreenOn = active && awakeRequested
        updateMotion()
        gl.requestRender()
    }
    fun lifecycleChanged(state: Lifecycle.State) {
        val started = state.isAtLeast(Lifecycle.State.STARTED) && !released
        resumed = state.isAtLeast(Lifecycle.State.RESUMED) && !released
        if (started != active) {
            active = started
            if (started) { gl.onResume(); surface?.let { controller?.setVideoSurface(it) } }
            else { surface?.let { controller?.clearVideoSurface(it) }; gl.onPause() }
        }
        keepScreenOn = active && awakeRequested
        updateMotion()
    }
    private fun updateMotion() {
        val wantedMotion = active && resumed && motionRequested && sensor != null
        if (motion != wantedMotion) {
            motion = wantedMotion; lastAngles = null
            sensors?.unregisterListener(this)
            if (motion) sensors?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }
    fun release() {
        released = true; keepScreenOn = false
        sensors?.unregisterListener(this)
        controller?.removeListener(listener)
        surface?.let { controller?.clearVideoSurface(it); it.release() }; surface = null
        controller = null
        gl.queueEvent { renderer.release() }
        gl.onPause()
    }
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        if (!motion || !active || released) return
        val matrix = FloatArray(9)
        SensorManager.getRotationMatrixFromVector(matrix, event.values)
        val remapped = FloatArray(9)
        val rotation = display?.rotation ?: Surface.ROTATION_0
        val x = when (rotation) { Surface.ROTATION_90 -> SensorManager.AXIS_Y; Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X; Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y; else -> SensorManager.AXIS_X }
        val y = when (rotation) { Surface.ROTATION_90 -> SensorManager.AXIS_MINUS_X; Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_Y; Surface.ROTATION_270 -> SensorManager.AXIS_X; else -> SensorManager.AXIS_Y }
        SensorManager.remapCoordinateSystem(matrix, x, y, remapped)
        val orientation = ProjectionMath.motionAngles(remapped)
        val angles = floatArrayOf(orientation.first, orientation.second)
        lastAngles?.let { last ->
            val yaw = (angles[0] - last[0]).let { ((it + 540) % 360) - 180 }
            val pitch = angles[1] - last[1]
            // A display-rotation or sensor reset must not jump the camera.
            if (kotlin.math.abs(yaw) < 45 && kotlin.math.abs(pitch) < 45) onMotion(yaw, pitch)
        }
        lastAngles = angles
    }
    private inner class SphereRenderer : GLSurfaceView.Renderer {
        @Volatile var projection = Projection.EQUIRECTANGULAR
        @Volatile var yaw = 0f; @Volatile var pitch = 0f
        @Volatile var videoWidth = 0; @Volatile var videoHeight = 0
        private var program = 0; private var texture = 0
        private var producer: SurfaceTexture? = null
        @Volatile private var framePending = false
        private val transform = FloatArray(16).apply { android.opengl.Matrix.setIdentityM(this, 0) }
        private var aspect = 1f
        private val vertices = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)).apply { position(0) }
        private fun failed() { post { if (!released) onFailure() } }
        override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
            try {
                fun shader(type: Int, source: String): Int {
                    val shader = glCreateShader(type)
                    glShaderSource(shader, source); glCompileShader(shader)
                    val result = IntArray(1); glGetShaderiv(shader, GL_COMPILE_STATUS, result, 0)
                    check(result[0] != 0) { glGetShaderInfoLog(shader) }
                    return shader
                }
                program = glCreateProgram()
                val vertex = shader(GL_VERTEX_SHADER, VERTEX)
                val fragment = shader(GL_FRAGMENT_SHADER, FRAGMENT)
                glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program)
                glDeleteShader(vertex); glDeleteShader(fragment)
                val linked = IntArray(1); glGetProgramiv(program, GL_LINK_STATUS, linked, 0)
                check(linked[0] != 0) { glGetProgramInfoLog(program) }
                val textures = IntArray(1); glGenTextures(1, textures, 0); texture = textures[0]
                glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
                glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
                glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
                glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
                producer?.release()
                val next = SurfaceTexture(texture).also { producer = it }
                next.setOnFrameAvailableListener { framePending = true; gl.requestRender() }
                val decoded = Surface(next)
                post {
                    if (released) decoded.release() else {
                        surface?.let { controller?.clearVideoSurface(it); it.release() }
                        surface = decoded
                        if (active) controller?.setVideoSurface(decoded)
                    }
                }
            } catch (_: Exception) { program = 0; failed() }
        }
        override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
            glViewport(0, 0, width, height); aspect = width.toFloat() / height.coerceAtLeast(1)
        }
        override fun onDrawFrame(unused: GL10?) {
            glClearColor(0f, 0f, 0f, 1f); glClear(GL_COLOR_BUFFER_BIT)
            if (program == 0) return
            try {
                if (framePending) { framePending = false; producer?.updateTexImage(); producer?.getTransformMatrix(transform) }
                glUseProgram(program)
                val pos = glGetAttribLocation(program, "aPosition")
                glEnableVertexAttribArray(pos); glVertexAttribPointer(pos, 2, GL_FLOAT, false, 0, vertices)
                glUniform1f(glGetUniformLocation(program, "aspect"), aspect)
                glUniform2f(glGetUniformLocation(program, "angle"), Math.toRadians(yaw.toDouble()).toFloat(), Math.toRadians(pitch.toDouble()).toFloat())
                glUniform1i(glGetUniformLocation(program, "eac"), if (projection == Projection.EAC) 1 else 0)
                glUniform2f(glGetUniformLocation(program, "videoSize"), videoWidth.coerceAtLeast(1).toFloat(), videoHeight.coerceAtLeast(1).toFloat())
                glUniformMatrix4fv(glGetUniformLocation(program, "texMatrix"), 1, false, transform, 0)
                glActiveTexture(GL_TEXTURE0); glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture)
                glUniform1i(glGetUniformLocation(program, "video"), 0)
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4); glDisableVertexAttribArray(pos)
                check(glGetError() == GL_NO_ERROR)
            } catch (_: Exception) { program = 0; failed() }
        }
        fun release() { producer?.release(); producer = null; if (program != 0) glDeleteProgram(program); if (texture != 0) glDeleteTextures(1, intArrayOf(texture), 0) }
    }
    companion object {
        fun motionAvailable(context: Context): Boolean = context.getSystemService(SensorManager::class.java)?.let {
            it.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR) != null || it.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
        } == true
        private const val VERTEX = "attribute vec2 aPosition; varying vec2 position; void main(){position=aPosition;gl_Position=vec4(aPosition,0.,1.);}"
        private const val FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;
            uniform samplerExternalOES video;
            uniform mat4 texMatrix;
            uniform float aspect;
            uniform vec2 angle;
            uniform bool eac;
            uniform vec2 videoSize;
            varying vec2 position;
            const float PI=3.141592653589793;
            void main(){
                vec3 d=normalize(vec3(position.x*aspect,position.y,-1.));
                float cp=cos(angle.y),sp=sin(angle.y),cy=cos(angle.x),sy=sin(angle.x);
                d=vec3(d.x,cp*d.y-sp*d.z,sp*d.y+cp*d.z);
                d=vec3(cy*d.x-sy*d.z,d.y,sy*d.x+cy*d.z);
                vec2 uv;
                if(!eac) uv=vec2(atan(d.x,-d.z)/(2.*PI)+.5,asin(clamp(d.y,-1.,1.))/PI+.5);
                else {
                    vec3 a=abs(d); float face; vec2 p;
                    if(a.z>=a.x && a.z>=a.y){
                        if(d.z<0.){face=1.;p=vec2(d.x,d.y)/a.z;}
                        else {face=4.;p=vec2(d.y,d.x)/a.z;}
                    } else if(a.x>=a.y){
                        if(d.x>0.){face=2.;p=vec2(d.z,d.y)/a.x;}
                        else {face=0.;p=vec2(-d.z,d.y)/a.x;}
                    } else {
                        if(d.y<0.){face=3.;p=vec2(d.z,d.x)/a.y;}
                        else {face=5.;p=vec2(-d.z,d.x)/a.y;}
                    }
                    vec2 q=atan(p)*2./PI+.5;
                    vec2 inset=min(vec2(.05),2./videoSize*vec2(3.,2.));
                    q=mix(inset,1.-inset,q);
                    uv=(vec2(mod(face,3.),face<3.?1.:0.)+q)/vec2(3.,2.);
                }
                gl_FragColor=texture2D(video,(texMatrix*vec4(uv,0.,1.)).xy);
            }
        """
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
internal fun ProjectedVideoSurface(playback: PlaybackState, controller: MediaController?, modifier: Modifier,
    appearance: CaptionAppearance, view: ViewingState, motionAllowed: Boolean, failure: () -> Unit, pan: (Float, Float) -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val native = remember(context) { ProjectionVideoView(context) }
    DisposableEffect(owner, native) {
        val observer = LifecycleEventObserver { _, _ -> native.lifecycleChanged(owner.lifecycle.currentState) }
        owner.lifecycle.addObserver(observer)
        native.lifecycleChanged(owner.lifecycle.currentState)
        onDispose { owner.lifecycle.removeObserver(observer); native.lifecycleChanged(Lifecycle.State.DESTROYED) }
    }
    AndroidView(factory = { native }, update = {
        it.onFailure = failure; it.onMotion = pan
        it.configure(controller, playback.projection, view, appearance,
            motionAllowed && view.motion, playback.keepVideoScreenOn(true))
    }, onRelease = { it.release() }, modifier = modifier)
}
