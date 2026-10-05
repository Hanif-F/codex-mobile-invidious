package net.wingress.mobivious.player

import androidx.media3.common.*
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.wingress.mobivious.data.*

/** A logical timeline with one lazily resolved ExoPlayer item. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class QueueSessionPlayer(player: Player, private val queue: QueueCoordinator,
    private val snapshot: () -> PlaybackQueueSnapshot) : ForwardingSimpleBasePlayer(player) {
    fun refresh() = invalidateState()
    override fun getState(): State {
        val base = super.getState()
        val state = snapshot()
        if (state.items.isEmpty() || state.currentIndex < 0) return base
        val entries = state.items.map { entry ->
            val active = entry.key == state.currentKey && player.currentMediaItem?.mediaMetadata?.extras?.getString("occurrence") == entry.key
            val item = if (active) player.currentMediaItem!! else MediaItem.Builder().setMediaId(entry.video.id)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.video.title).setArtist(entry.video.author).build()).build()
            // Keep the real active window, including live-edge timing and manifest state.
            val real = base.playlist.getOrNull(base.currentMediaItemIndex).takeIf { active }
            (real?.buildUpon()?.setUid(entry.key) ?: MediaItemData.Builder(entry.key)
                .setIsSeekable(true).setDurationUs(entry.video.duration * 1_000_000))
                .setMediaItem(item).setMediaMetadata(item.mediaMetadata).build()
        }.toMutableList()
        val prefix = if (state.source?.mix == false && (state.items.mapNotNull { it.sourceIndex }.minOrNull() ?: 0) > 0) 1 else 0
        if (prefix > 0) entries.add(0, MediaItemData.Builder("${state.token}:previous").setMediaItem(MediaItem.Builder().setMediaId("queue-previous").build()).setIsPlaceholder(true).build())
        if (state.source != null && !state.sourceComplete) entries.add(MediaItemData.Builder("${state.token}:more")
            .setMediaItem(MediaItem.Builder().setMediaId("queue-more").build()).setIsPlaceholder(true).build())
        val commands = base.availableCommands.buildUpon().remove(Player.COMMAND_SET_MEDIA_ITEM).remove(Player.COMMAND_CHANGE_MEDIA_ITEMS)
            .remove(Player.COMMAND_SET_SHUFFLE_MODE)
            .remove(Player.COMMAND_SEEK_TO_NEXT).remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).remove(Player.COMMAND_SEEK_TO_PREVIOUS).remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_MEDIA_ITEM).add(Player.COMMAND_SET_REPEAT_MODE)
        if (state.currentIndex + prefix > 0 || state.repeat == QueueRepeat.ALL) commands.add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_PREVIOUS)
        if (state.currentIndex < state.items.lastIndex || !state.sourceComplete || state.repeat == QueueRepeat.ALL) commands.add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM).add(Player.COMMAND_SEEK_TO_NEXT)
        return base.buildUpon().setPlaylist(entries).setCurrentMediaItemIndex(state.currentIndex + prefix).setAvailableCommands(commands.build())
            .setRepeatMode(when(state.repeat) { QueueRepeat.ONE -> Player.REPEAT_MODE_ONE; QueueRepeat.ALL -> Player.REPEAT_MODE_ALL; else -> Player.REPEAT_MODE_OFF })
            .build()
    }
    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> queue.advance()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> queue.advance(-1)
            Player.COMMAND_SEEK_TO_MEDIA_ITEM -> {
                val state = snapshot()
                val prefix = if (state.source?.mix == false && (state.items.mapNotNull { it.sourceIndex }.minOrNull() ?: 0) > 0) 1 else 0
                val entry = state.items.getOrNull(mediaItemIndex - prefix)
                if (entry != null) queue.select(entry.key, positionMs.takeIf { it != C.TIME_UNSET }) else queue.advance(if (mediaItemIndex < prefix) -1 else 1)
            }
            else -> return super.handleSeek(0, if (positionMs == C.TIME_UNSET) positionMs else queue.clampSeek(positionMs), seekCommand)
        }
        return Futures.immediateVoidFuture()
    }
    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        val bound = snapshot().current?.linkPlayback
        if (playWhenReady && bound?.endMs != null && player.currentPosition >= bound.endMs) player.seekTo(bound.startMs ?: 0)
        return super.handleSetPlayWhenReady(playWhenReady)
    }
    override fun handleSetRepeatMode(repeatMode: Int): ListenableFuture<*> {
        queue.repeat(when(repeatMode) { Player.REPEAT_MODE_ONE -> QueueRepeat.ONE; Player.REPEAT_MODE_ALL -> QueueRepeat.ALL; else -> QueueRepeat.OFF })
        return Futures.immediateVoidFuture()
    }
}
