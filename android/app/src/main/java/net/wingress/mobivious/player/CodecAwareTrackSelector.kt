package net.wingress.mobivious.player

import android.content.Context
import android.util.Pair
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.RendererCapabilities
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import net.wingress.mobivious.data.StreamFormat

/** Selects from manifest capabilities before any video segment is requested. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class CodecAwareTrackSelector(context: Context) : DefaultTrackSelector(context) {
    @Volatile private var policy = VideoSelection()
    fun configure(value: VideoSelection) { policy = value; invalidate() }
    private data class AudioPolicy(val selection: AudioSelection = AudioSelection(), val formats: List<StreamFormat> = emptyList())
    @Volatile private var audioPolicy = AudioPolicy()
    fun configureAudio(value: AudioSelection, formats: List<StreamFormat> = emptyList()) {
        audioPolicy = AudioPolicy(value, formats.toList()); invalidate()
    }

    override fun selectAudioTrack(mappedTrackInfo: MappedTrackInfo, rendererFormatSupports: Array<Array<IntArray>>,
        mixedMimeTypeSupports: IntArray, params: Parameters): Pair<ExoTrackSelection.Definition, Int>? {
        val current = audioPolicy
        if (!current.selection.enabled) return super.selectAudioTrack(mappedTrackInfo, rendererFormatSupports, mixedMimeTypeSupports, params)
        val groups = (0 until mappedTrackInfo.rendererCount).filter { mappedTrackInfo.getRendererType(it) == C.TRACK_TYPE_AUDIO }.flatMap { renderer ->
            val mapped = mappedTrackInfo.getTrackGroups(renderer)
            (0 until mapped.length).map { index ->
                val group = mapped[index]
                Tracks.Group(group, false, IntArray(group.length) { track ->
                    RendererCapabilities.getFormatSupport(rendererFormatSupports[renderer][index][track])
                }, BooleanArray(group.length))
            }
        }
        val choice = current.selection.fixed(StreamCatalog.choices(Tracks(groups), C.TRACK_TYPE_AUDIO, current.formats))
            ?: return super.selectAudioTrack(mappedTrackInfo, rendererFormatSupports, mixedMimeTypeSupports, params)
        val renderer = (0 until mappedTrackInfo.rendererCount).first { mappedTrackInfo.getTrackGroups(it).indexOf(choice.group) >= 0 }
        return Pair(ExoTrackSelection.Definition(choice.group, choice.index), renderer)
    }

    override fun selectVideoTrack(mappedTrackInfo: MappedTrackInfo, rendererFormatSupports: Array<Array<IntArray>>,
        mixedMimeTypeSupports: IntArray, params: Parameters, selectedAudioLanguage: String?): Pair<ExoTrackSelection.Definition, Int>? {
        val current = policy
        if (!current.dash && current.mode != VideoSelectionMode.MANUAL)
            return super.selectVideoTrack(mappedTrackInfo, rendererFormatSupports, mixedMimeTypeSupports, params, selectedAudioLanguage)
        val groups = (0 until mappedTrackInfo.rendererCount).filter { mappedTrackInfo.getRendererType(it) == C.TRACK_TYPE_VIDEO }.flatMap { renderer ->
            val mapped = mappedTrackInfo.getTrackGroups(renderer)
            (0 until mapped.length).map { index ->
                val group = mapped[index]
                Tracks.Group(group, false, IntArray(group.length) { track ->
                    val format = group.getFormat(track)
                    if (format.roleFlags and C.ROLE_FLAG_TRICK_PLAY != 0) C.FORMAT_UNSUPPORTED_TYPE
                    else RendererCapabilities.getFormatSupport(rendererFormatSupports[renderer][index][track])
                }, BooleanArray(group.length))
            }
        }
        val choices = StreamCatalog.choices(Tracks(groups), C.TRACK_TYPE_VIDEO)
        val resolved = current.resolve(choices)
        val fixed = resolved.fixed(choices)
        if (fixed != null) {
            val renderer = (0 until mappedTrackInfo.rendererCount).first { mappedTrackInfo.getTrackGroups(it).indexOf(fixed.group) >= 0 }
            return Pair(ExoTrackSelection.Definition(fixed.group, fixed.index), renderer)
        }
        // Preserve Media3's bandwidth adaptation and decoder/adaptation checks, while
        // excluding other codecs only when a supported preferred codec exists.
        val allowed = resolved.adaptive(choices)
        val filtered = Array(rendererFormatSupports.size) { renderer ->
            // Media3 includes an extra bucket for unmapped groups, which has no renderer.
            if (renderer >= mappedTrackInfo.rendererCount || mappedTrackInfo.getRendererType(renderer) != C.TRACK_TYPE_VIDEO)
                rendererFormatSupports[renderer]
            else Array(rendererFormatSupports[renderer].size) { groupIndex ->
                val group = mappedTrackInfo.getTrackGroups(renderer)[groupIndex]
                IntArray(group.length) { track ->
                    if (allowed.any { it.group == group && it.index == track })
                        rendererFormatSupports[renderer][groupIndex][track]
                    else RendererCapabilities.create(C.FORMAT_UNSUPPORTED_SUBTYPE)
                }
            }
        }
        return super.selectVideoTrack(mappedTrackInfo, filtered, mixedMimeTypeSupports, params, selectedAudioLanguage)
    }
}
