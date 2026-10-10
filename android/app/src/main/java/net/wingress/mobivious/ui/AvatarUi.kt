package net.wingress.mobivious.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.disk.DiskCache
import net.wingress.mobivious.data.Avatars
import net.wingress.mobivious.data.Channel
import net.wingress.mobivious.data.ChannelImages
import net.wingress.mobivious.data.ContentVisibility
import net.wingress.mobivious.data.Video
import net.wingress.mobivious.data.DisplayFormats
import java.io.File

/** Shared image cache, keyed by the complete instance URL. */
internal object AvatarImageLoader {
    private var loader: ImageLoader? = null
    @Synchronized fun get(context: Context): ImageLoader = loader ?: ImageLoader.Builder(context.applicationContext)
        .okHttpClient(Avatars.client())
        .diskCache { DiskCache.Builder().directory(File(context.applicationContext.cacheDir, "avatars"))
            .maxSizeBytes(16L * 1024 * 1024).build() }
        .build().also { loader = it }
}

@Composable
internal fun ChannelAvatar(server: String, image: String, name: String, size: Dp, tag: String) {
    val url = remember(server, image) { Avatars.url(server, image) }
    val initial = remember(name) { Avatars.initial(name) }
    val colors = MaterialTheme.colorScheme
    val palette = listOf(colors.primaryContainer to colors.onPrimaryContainer,
        colors.secondaryContainer to colors.onSecondaryContainer, colors.tertiaryContainer to colors.onTertiaryContainer)
    val (background, foreground) = palette[(initial?.codePointAt(0) ?: 0) % palette.size]
    Box(Modifier.size(size).clip(CircleShape).background(background).testTag(tag), contentAlignment = Alignment.Center) {
        if (initial != null) Text(initial, Modifier.clearAndSetSemantics {}, color = foreground,
            fontSize = (size.value * .43f / LocalDensity.current.fontScale).sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        else Icon(Icons.Default.Person, null, Modifier.size(size * .6f), tint = foreground)
        if (url != null) AsyncImage(url, null, modifier = Modifier.fillMaxSize(), imageLoader = AvatarImageLoader.get(LocalContext.current),
            contentScale = ContentScale.Crop)
    }
}

/** Avatar and name share one navigation action and accessibility node. */
@Composable
internal fun ChannelAuthor(server: String, image: String, name: String,
    showAvatar: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp, tag: String,
    onClick: (() -> Unit)? = null, maxLines: Int = Int.MAX_VALUE) {
    val action = if (onClick != null) Modifier.clickable(onClickLabel = "Open $name's channel", onClick = onClick) else Modifier
    Row(modifier.semantics(mergeDescendants = true) {}.then(action),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showAvatar) ChannelAvatar(server, image, name, size, tag)
        Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false), maxLines = maxLines, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun WatchChannelIdentity(video: Video, server: String, thinMode: Boolean, subscribed: Boolean,
    subscribe: () -> Unit, channel: (String) -> Unit, verified: Boolean = false, subscribers: String = "",
    subscriptionBusy: Boolean = false, subscriptionKnown: Boolean = true, subscriptionChecking: Boolean = false, subscriptionError: String? = null) {
    val fontScale = LocalDensity.current.fontScale
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val author: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier.heightIn(min = 48.dp).semantics(mergeDescendants = true) {}
                .clickable(enabled = ContentVisibility.validChannel(video.channelId), role = Role.Button,
                    onClickLabel = "Open ${video.author}'s channel") { channel(video.channelId) },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Avatars.show(thinMode, video.channelId)) ChannelAvatar(server, video.authorAvatar,
                    video.author, 40.dp, "watch-channel-avatar")
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(video.author.ifBlank { "Unknown channel" }, Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleSmall)
                        if (verified) Icon(Icons.Default.Verified, "Verified channel", Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    DisplayFormats.subscribers(subscribers).takeIf(String::isNotBlank)?.let { Text(it,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
        val button: @Composable () -> Unit = {
            SubscriptionControl(subscribed, subscribe, busy = subscriptionBusy, known = subscriptionKnown,
                checking = subscriptionChecking, watch = true, resetKey = listOf(server, video.channelId))
        }
        if (maxWidth < 360.dp || fontScale > 1.4f) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            author(Modifier.fillMaxWidth()); button()
        } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            author(Modifier.weight(1f)); button()
        }
    }
    subscriptionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) }
    }
}

@Composable
internal fun ChannelHeader(channel: Channel, server: String, thinMode: Boolean, subscribed: Boolean,
    actions: @Composable () -> Unit = {}, readDescription: () -> Unit = {},
    subscriptionBusy: Boolean = false, subscriptionKnown: Boolean = true, subscriptionChecking: Boolean = false,
    subscriptionError: String? = null, subscribe: () -> Unit) {
    var bannerFailed by remember(server, channel.banner) { mutableStateOf(false) }
    BrowseArtworkSurface(if (thinMode || bannerFailed) null else ChannelImages.url(server, channel.banner)) {
        Column(Modifier.fillMaxWidth().padding(Liquid.inset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!thinMode && !bannerFailed && channel.banner.isNotBlank()) ChannelImages.url(server, channel.banner)?.let { url ->
                AsyncImage(url, "${channel.name} channel banner", modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).aspectRatio(3.8f).clip(Liquid.media).testTag("channel-banner"), imageLoader = AvatarImageLoader.get(LocalContext.current), contentScale = ContentScale.Crop, onError = { bannerFailed = true })
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val identity: @Composable () -> Unit = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(channel.name, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            if (channel.verified) Icon(Icons.Default.Verified, "Verified channel", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        if (channel.handle.isNotBlank()) Text(channel.handle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (channel.pronouns.isNotBlank()) Text(channel.pronouns, style = MaterialTheme.typography.bodyMedium)
                        DisplayFormats.subscribers(channel.subscribers).takeIf(String::isNotBlank)?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                if (maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        if (!thinMode) ChannelAvatar(server, channel.image, channel.name, 72.dp, "channel-header-avatar")
                        Spacer(Modifier.weight(1f)); actions()
                    }
                    identity()
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (!thinMode) ChannelAvatar(server, channel.image, channel.name, 72.dp, "channel-header-avatar")
                    Box(Modifier.weight(1f)) { identity() }
                    actions()
                }
            }
            if (channel.description.isNotBlank()) {
                Text(channel.description, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                TextButton(onClick = readDescription, modifier = Modifier.testTag("channel-description-open")) { Text("Read full description") }
            }
            SubscriptionControl(subscribed, subscribe, busy = subscriptionBusy, known = subscriptionKnown,
                checking = subscriptionChecking, error = subscriptionError, resetKey = listOf(server, channel.id))
        }
    }
}

@Composable
internal fun SubscriptionChannelChip(channel: Channel, server: String, thinMode: Boolean, open: () -> Unit) {
    val width = (if (thinMode) 112.dp else 88.dp) * LocalDensity.current.fontScale.coerceIn(1f, 2f)
    Column(Modifier.width(width).clip(Liquid.media)
        .clickable(role = Role.Button, onClickLabel = "Open ${channel.name}'s channel", onClick = open)
        .heightIn(min = 48.dp).padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!thinMode) ChannelAvatar(server, channel.image, channel.name, 48.dp, "subscription-avatar-${channel.id}")
        Text(channel.name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
