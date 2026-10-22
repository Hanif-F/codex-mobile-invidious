package net.wingress.mobivious.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import coil.transform.Transformation
import java.io.File
import kotlin.math.roundToInt
import net.wingress.mobivious.data.StoryboardFrame
import net.wingress.mobivious.data.StoryboardRules
import net.wingress.mobivious.player.SeekPreviewState

/** Original sprite sheets share URL-based caches across crops and interactions. Requests use the shared instance-access boundary. */
internal class StoryboardImages(context: Context) {
    private val context = context.applicationContext
    val loader = (this.context as net.wingress.mobivious.MobiviousApplication).imageLoaderBuilder(
            okhttp3.OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).build())
        .memoryCache { MemoryCache.Builder(this.context).maxSizeBytes(32 * 1024 * 1024).build() }
        .diskCache { DiskCache.Builder().directory(File(this.context.cacheDir, "storyboards")).maxSizeBytes(64L * 1024 * 1024).build() }
        .build()

    suspend fun sheet(url: String, cachedOnly: Boolean): Bitmap? {
        val request = ImageRequest.Builder(context).data(url).size(Size.ORIGINAL).allowHardware(false)
            // Coil uses only-if-cached when network reads are disabled; disk and memory remain enabled.
            .networkCachePolicy(if (cachedOnly) CachePolicy.DISABLED else CachePolicy.ENABLED).build()
        return (loader.execute(request) as? SuccessResult)?.drawable?.toBitmap()
    }
}

internal class StoryboardCrop(private val frame: StoryboardFrame) : Transformation {
    override val cacheKey = "storyboard-crop:${frame.x}:${frame.y}:${frame.width}:${frame.height}"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        require(StoryboardRules.fits(frame, input.width, input.height)) { "Invalid storyboard crop" }
        return Bitmap.createBitmap(input, frame.x, frame.y, frame.width, frame.height)
    }
}

/** An opaque fit layer also covers native subtitles and video letterboxing, without owning input. */
@Composable
internal fun PlayerSeekPreview(preview: SeekPreviewState<Bitmap>, modifier: Modifier = Modifier) {
    val frame = preview.frame ?: return
    val sheet = preview.sheet ?: return
    if (preview.kind == null || !StoryboardRules.fits(frame, sheet.width, sheet.height)) return
    val bitmap = sheet.asImageBitmap()
    Canvas(modifier.fillMaxSize().background(Color.Black).testTag("player-seek-preview")) {
        val scale = minOf(size.width / frame.width, size.height / frame.height)
        val width = (frame.width * scale).roundToInt(); val height = (frame.height * scale).roundToInt()
        drawImage(bitmap, srcOffset = IntOffset(frame.x, frame.y), srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset(((size.width - width) / 2).roundToInt(), ((size.height - height) / 2).roundToInt()),
            dstSize = IntSize(width, height), filterQuality = FilterQuality.High)
    }
}
