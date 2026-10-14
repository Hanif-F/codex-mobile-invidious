package net.wingress.mobivious.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import net.wingress.mobivious.data.AccentPreset

internal object AppTokens {
    val light = appColorScheme(false)
    val dark = appColorScheme(true)
    val inset: androidx.compose.ui.unit.Dp
        @Composable get() = LocalPageInset.current
    val card = RoundedCornerShape(16.dp)
    val media = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(50)
    val typography = Typography()
    val shapes = Shapes(RoundedCornerShape(4.dp), RoundedCornerShape(8.dp), RoundedCornerShape(12.dp),
        RoundedCornerShape(16.dp), RoundedCornerShape(28.dp))
}

internal fun appColorScheme(dark: Boolean, accent: AccentPreset = AccentPreset.BLUE): ColorScheme {
    fun c(value: Long) = Color(value)
    val roles = (if (dark) accent.dark else accent.light).map(::c)
    val base = if (dark) darkColorScheme(
        background=c(0xFF131416), surface=c(0xFF131416), onBackground=c(0xFFE4E4E9), onSurface=c(0xFFE4E4E9),
        onSurfaceVariant=c(0xFFC6C6D0), surfaceDim=c(0xFF131416), surfaceBright=c(0xFF393A3E),
        surfaceContainerLowest=c(0xFF0E0F11), surfaceContainerLow=c(0xFF1B1C1F), surfaceContainer=c(0xFF202124),
        surfaceContainerHigh=c(0xFF2B2C2F), surfaceContainerHighest=c(0xFF343539), surfaceVariant=c(0xFF343539),
        secondary=c(0xFFC6C6D0), onSecondary=c(0xFF2F3038), secondaryContainer=c(0xFF45464F), onSecondaryContainer=c(0xFFE2E2EB),
        outline=c(0xFF90909B), outlineVariant=c(0xFF45464F), error=c(0xFFFFB4AB), onError=c(0xFF690005),
        errorContainer=c(0xFF93000A), onErrorContainer=c(0xFFFFDAD6), inverseSurface=c(0xFFE4E4E9), inverseOnSurface=c(0xFF303034)
    ) else lightColorScheme(
        background=c(0xFFF8F9FA), surface=c(0xFFF8F9FA), onBackground=c(0xFF1B1B1F), onSurface=c(0xFF1B1B1F),
        onSurfaceVariant=c(0xFF45464F), surfaceDim=c(0xFFD9DADE), surfaceBright=c(0xFFF8F9FA),
        surfaceContainerLowest=c(0xFFFFFFFF), surfaceContainerLow=c(0xFFF2F3F5), surfaceContainer=c(0xFFEEEFF1),
        surfaceContainerHigh=c(0xFFE9EAEC), surfaceContainerHighest=c(0xFFE3E4E8), surfaceVariant=c(0xFFE3E4E8),
        secondary=c(0xFF595A63), onSecondary=c(0xFFFFFFFF), secondaryContainer=c(0xFFE2E2EB), onSecondaryContainer=c(0xFF1A1B22),
        outline=c(0xFF74747E), outlineVariant=c(0xFFC5C6D0), error=c(0xFFBA1A1A), onError=c(0xFFFFFFFF),
        errorContainer=c(0xFFFFDAD6), onErrorContainer=c(0xFF410002), inverseSurface=c(0xFF303034), inverseOnSurface=c(0xFFF2F0F4)
    )
    return base.copy(
        primary=roles[0], onPrimary=roles[1], primaryContainer=roles[2], onPrimaryContainer=roles[3],
        tertiary=roles[0], onTertiary=roles[1], tertiaryContainer=roles[2], onTertiaryContainer=roles[3],
        inversePrimary=c((if (dark) accent.light else accent.dark)[0]),
        primaryFixed=c(accent.light[2]), primaryFixedDim=c(accent.dark[0]),
        onPrimaryFixed=c(accent.light[3]), onPrimaryFixedVariant=c(accent.dark[2]),
        tertiaryFixed=c(accent.light[2]), tertiaryFixedDim=c(accent.dark[0]),
        onTertiaryFixed=c(accent.light[3]), onTertiaryFixedVariant=c(accent.dark[2]),
        secondaryFixed=c(0xFFE2E2EB), secondaryFixedDim=c(0xFFC6C6D0),
        onSecondaryFixed=c(0xFF1A1B22), onSecondaryFixedVariant=c(0xFF45464F),
        surfaceTint=Color.Transparent, scrim=Color.Black
    )
}

@Composable
internal fun AppTheme(dark: Boolean, accent: AccentPreset, content: @Composable () -> Unit) {
    val colors=remember(dark,accent) {appColorScheme(dark,accent)}
    MaterialTheme(colorScheme=colors, typography=AppTokens.typography,
        shapes=AppTokens.shapes) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val width = with(density) { androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp() }
        androidx.compose.runtime.CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
            LocalPageInset provides if (width < 600.dp) 16.dp else 24.dp, content=content)
    }
}
