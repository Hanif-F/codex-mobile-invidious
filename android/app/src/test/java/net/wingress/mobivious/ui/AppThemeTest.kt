package net.wingress.mobivious.ui

import androidx.compose.ui.graphics.Color
import net.wingress.mobivious.data.AccentPreset
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.pow

class AppThemeTest {
    private fun luminance(c: Color): Double {
        fun channel(n: Float) = if (n <= .04045) n / 12.92 else ((n + .055) / 1.055).pow(2.4)
        return .2126 * channel(c.red) + .7152 * channel(c.green) + .0722 * channel(c.blue)
    }
    private fun contrast(a: Color, b: Color): Double {
        val x=luminance(a); val y=luminance(b)
        return (maxOf(x,y)+.05)/(minOf(x,y)+.05)
    }
    @Test fun everyEnabledTextAndBoundaryRolePassesInAllTwelvePalettes() {
        for (dark in listOf(false,true)) for (accent in AccentPreset.entries) {
            val s=appColorScheme(dark,accent)
            val surfaces=listOf(s.surface,s.surfaceContainerLowest,s.surfaceContainerLow,s.surfaceContainer,
                s.surfaceContainerHigh,s.surfaceContainerHighest)
            val text=surfaces.flatMap { listOf(s.onSurface to it,s.onSurfaceVariant to it,s.primary to it) } + listOf(
                s.onPrimary to s.primary,s.onPrimaryContainer to s.primaryContainer,
                s.onSecondary to s.secondary,s.onSecondaryContainer to s.secondaryContainer,
                s.onError to s.error,s.onErrorContainer to s.errorContainer,
                s.inverseOnSurface to s.inverseSurface,s.inversePrimary to s.inverseSurface,
                s.onPrimaryFixed to s.primaryFixed,s.onPrimaryFixedVariant to s.primaryFixed,
                s.onPrimaryFixed to s.primaryFixedDim,s.onPrimaryFixedVariant to s.primaryFixedDim,
                s.onSecondaryFixed to s.secondaryFixed,s.onSecondaryFixedVariant to s.secondaryFixed,
                s.onSecondaryFixed to s.secondaryFixedDim,s.onSecondaryFixedVariant to s.secondaryFixedDim)
            text.forEach { (a,b) -> assertTrue("$accent dark=$dark text contrast ${contrast(a,b)}",contrast(a,b)>=4.5) }
            val boundaries=surfaces.map {s.outline to it} + listOf(s.onPrimaryContainer to s.primaryContainer)
            boundaries.forEach { (a,b) -> assertTrue("$accent dark=$dark boundary",contrast(a,b)>=3.0) }
            surfaces.forEach { assertEquals(1f,it.alpha) }
            assertEquals(0f,s.surfaceTint.alpha)
        }
    }
    @Test fun accentsNeverChangeNeutralSurfacesAndUnknownIdsResolveBlue() {
        assertEquals(AccentPreset.BLUE,AccentPreset.saved(null))
        assertEquals(AccentPreset.BLUE,AccentPreset.saved("unknown"))
        for (a in AccentPreset.entries) {
            assertEquals(a,AccentPreset.saved(a.id))
            for (dark in listOf(false,true)) assertEquals(appColorScheme(dark).surface,appColorScheme(dark,a).surface)
        }
    }
}
