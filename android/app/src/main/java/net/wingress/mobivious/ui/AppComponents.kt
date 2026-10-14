package net.wingress.mobivious.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val LocalInsideAppSection = staticCompositionLocalOf { false }
internal val LocalReduceTransparency = staticCompositionLocalOf { false }
internal val LocalContentBottomInset = staticCompositionLocalOf { 16.dp }
internal enum class AppSurfaceRole { CONTROL, SECTION, MINI_PLAYER, MEDIA_OVERLAY }
internal data class AppContentColors(val primary: Color,val secondary: Color,val plate: Color) {
    companion object {
        val light=AppContentColors(Color.White,Color.White,Color.Black.copy(alpha=.72f))
        val dark=AppContentColors(Color(0xFF1B1B1F),Color(0xFF45464F),Color.White)
    }
}
internal val LocalAppContentColors = staticCompositionLocalOf { AppContentColors.dark }

@Composable internal fun appContentColors(role: AppSurfaceRole=AppSurfaceRole.CONTROL,prominent: Boolean=false): AppContentColors {
    val c=MaterialTheme.colorScheme
    return when {
        role==AppSurfaceRole.MINI_PLAYER -> AppContentColors(c.onSurface,c.onSurfaceVariant,c.surfaceContainer)
        role==AppSurfaceRole.MEDIA_OVERLAY -> AppContentColors.light
        prominent -> AppContentColors(c.onPrimaryContainer,c.onPrimaryContainer,c.primaryContainer)
        role==AppSurfaceRole.SECTION -> AppContentColors(c.onSurface,c.onSurfaceVariant,c.surfaceContainerLow)
        else -> AppContentColors(c.onSurface,c.onSurfaceVariant,c.surfaceContainerHigh)
    }
}
@Composable internal fun AppForeground(colors: AppContentColors=appContentColors(),content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides colors.primary,LocalAppContentColors provides colors,content=content)
}
@Composable internal fun AppSurface(modifier: Modifier=Modifier,shape: Shape=MaterialTheme.shapes.medium,
    prominent: Boolean=false,role: AppSurfaceRole=AppSurfaceRole.CONTROL,content: @Composable BoxScope.() -> Unit) {
    val colors=appContentColors(role=role,prominent=prominent)
    Surface(modifier,shape=shape,color=colors.plate,contentColor=colors.primary,tonalElevation=0.dp,
        shadowElevation=if(role==AppSurfaceRole.MINI_PLAYER) 1.dp else 0.dp) {
        AppForeground(colors) {Box(content=content)}
    }
}
@Composable internal fun AppBackground(modifier: Modifier=Modifier,content: @Composable BoxScope.() -> Unit) {
    Box(modifier.background(MaterialTheme.colorScheme.surface),content=content)
}
@Composable internal fun Modifier.appControl(shape: Shape=MaterialTheme.shapes.medium,prominent: Boolean=false,
    role: AppSurfaceRole=AppSurfaceRole.CONTROL): Modifier = clip(shape).background(appContentColors(role=role,prominent=prominent).plate)
@Composable internal fun Modifier.playerScrim(shape: Shape=MaterialTheme.shapes.medium): Modifier = clip(shape).background(Color.Black.copy(alpha=.72f))
@Composable internal fun Modifier.appItemFeedback(source: MutableInteractionSource): Modifier = indication(source,ripple())
@Composable internal fun AppInsetIconButton(onClick: () -> Unit,modifier: Modifier=Modifier,enabled: Boolean=true,
    interactionSource: MutableInteractionSource?=null,content: @Composable () -> Unit) {
    IconButton(onClick,modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),enabled=enabled,interactionSource=interactionSource,content=content)
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun TooltipScope.AppTooltip(label: String) {
    PlainTooltip(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh,contentColor=MaterialTheme.colorScheme.onSurface) {Text(label)}
}
@Composable internal fun AppSnackbarHost(state: SnackbarHostState) {
    SnackbarHost(state) {data -> Snackbar(data,containerColor=MaterialTheme.colorScheme.inverseSurface,
        contentColor=MaterialTheme.colorScheme.inverseOnSurface,actionColor=MaterialTheme.colorScheme.inversePrimary)}
}
internal fun colorContrast(a: Color,b: Color) = (maxOf(a.luminance(),b.luminance())+.05f)/(minOf(a.luminance(),b.luminance())+.05f)

@Composable
internal fun AppSection(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape=MaterialTheme.shapes.large,
        color=MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation=0.dp) {
        CompositionLocalProvider(LocalInsideAppSection provides true) {
            Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp), content=content)
        }
    }
}

@Composable
internal fun AppIconButton(icon: ImageVector, description: String, modifier: Modifier = Modifier,
    enabled: Boolean = true, prominent: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    IconButton(onClick, modifier.sizeIn(minWidth=48.dp,minHeight=48.dp), enabled=enabled,
        colors=IconButtonDefaults.iconButtonColors(contentColor=if(prominent) colors.primary else colors.onSurface)) {
        Icon(icon,description)
    }
}

@Composable
internal fun PreferenceSaveFeedback(status: PreferenceSaveStatus?, error: String?, retry: () -> Unit, revert: () -> Unit, modifier: Modifier = Modifier) {
    if (status == null) return
    Column(modifier.fillMaxWidth().semantics { liveRegion=LiveRegionMode.Polite }) {
        when(status) {
            PreferenceSaveStatus.PENDING, PreferenceSaveStatus.DEBOUNCING -> Text("Saving…",style=MaterialTheme.typography.bodySmall)
            PreferenceSaveStatus.FAILED, PreferenceSaveStatus.INVALID -> {
                Text(error ?: "This change has not been saved.",color=MaterialTheme.colorScheme.error)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    if(status==PreferenceSaveStatus.FAILED) TextButton(retry) { Text("Retry") }
                    TextButton(revert) { Text("Revert to saved") }
                }
            }
        }
    }
}

/** Enabled control boundaries use outline; outlineVariant is for quiet dividers. */
@Composable
internal fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val colors = MaterialTheme.colorScheme
    FilterChip(selected,onClick,label,modifier.heightIn(min=48.dp),enabled=enabled,
        border=androidx.compose.foundation.BorderStroke(1.dp,if(!enabled) colors.onSurface.copy(alpha=.12f)
            else if(selected) colors.onPrimaryContainer else colors.outline),
        colors=FilterChipDefaults.filterChipColors(selectedContainerColor=colors.primaryContainer,
            selectedLabelColor=colors.onPrimaryContainer,selectedLeadingIconColor=colors.onPrimaryContainer))
}
